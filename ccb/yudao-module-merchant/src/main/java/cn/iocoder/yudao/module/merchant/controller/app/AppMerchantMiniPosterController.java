package cn.iocoder.yudao.module.merchant.controller.app;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.config.WeChatMiniAppProperties;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.service.wechat.WeChatMiniAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户小程序 - 店铺推广小程序码海报。
 *
 * <p>用户在店铺主页生成一张带【小程序码】的海报，别人扫码 → 直接打开本小程序、
 * 落到该商家主页并绑定推广关系（scene = tenantId-inviterUserId，前端 shop/home 解析）。</p>
 *
 * <p>中文字体策略：Linux 服务器通常缺 CJK 字体，中文会渲染成方块。启动时探测系统是否有
 * 能显示中文的字体：有则合成【文字版】海报（店铺名 + 引导语 + 品牌）；没有则退化为
 * 【纯小程序码版】（仅品牌底 + 居中小程序码 + 英文/数字），保证任何环境都不出方块。</p>
 */
@Tag(name = "用户端 - 店铺推广小程序码海报")
@RestController
@RequestMapping("/merchant/mini")
@Validated
@Slf4j
public class AppMerchantMiniPosterController {

    private static final int POSTER_W = 600;
    private static final int POSTER_H = 900;
    private static final int QR_SIZE = 360;
    private static final String SHOP_HOME_PAGE = "pages/shop/home";

    @Resource
    private WeChatMiniAppService weChatMiniAppService;
    @Resource
    private WeChatMiniAppProperties miniAppProperties;
    @Resource
    private ShopInfoMapper shopInfoMapper;

    /** 探测一次能显示中文的字体名（惰性初始化，null 表示没有 → 退化英文版）。 */
    private volatile String cjkFontName;
    private volatile boolean cjkFontResolved;

    @GetMapping("/shop-poster")
    @Operation(summary = "生成本店推广小程序码海报（扫码进店 + 绑定推广关系）")
    @Parameter(name = "tenantId", description = "店铺所属租户 ID", required = true)
    @PermitAll
    @TenantIgnore
    @RateLimiter(time = 60, count = 20, keyResolver = ClientIpRateLimiterKeyResolver.class,
            message = "海报生成过于频繁，请稍后再试")
    public CommonResult<String> shopPoster(@RequestParam("tenantId") Long tenantId) {
        if (tenantId == null) {
            return CommonResult.error(400, "tenantId 不能为空");
        }
        // 登录用户即推广人；未登录则 inviter 为空，扫码仍能进店只是不绑推广
        Long inviterUserId = SecurityFrameworkUtils.getLoginUserId();
        // scene 仅数字 + '-'，天然满足 getwxacodeunlimit 的 32 字符 + 受限字符集要求
        String scene = tenantId + "-" + (inviterUserId != null ? inviterUserId : "");

        // 店铺名（跨租户查，仅取展示用 shopName）
        String shopName = null;
        try {
            ShopInfoDO shop = shopInfoMapper.selectByTenantId(tenantId);
            if (shop != null) {
                shopName = shop.getShopName();
            }
        } catch (Exception e) {
            log.warn("[shopPoster] 查询店铺名失败 tenantId={}: {}", tenantId, e.getMessage());
        }

        // 1) 拿小程序码 PNG
        byte[] qrBytes;
        try {
            qrBytes = weChatMiniAppService.getUnlimitedQRCode(scene, SHOP_HOME_PAGE, miniAppProperties.getEnvVersion());
        } catch (Exception e) {
            log.error("[shopPoster] 生成小程序码失败 tenantId={} scene={}: {}", tenantId, scene, e.getMessage());
            return CommonResult.error(500, "小程序码生成失败，请稍后重试");
        }

        // 2) 合成海报
        try {
            byte[] posterBytes = composePoster(qrBytes, shopName);
            String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(posterBytes);
            return success(dataUrl);
        } catch (Exception e) {
            log.error("[shopPoster] 合成海报失败 tenantId={}: {}", tenantId, e.getMessage(), e);
            // 合成失败兜底：直接返回小程序码本身，至少可扫
            String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes);
            return success(dataUrl);
        }
    }

    /**
     * 竖版海报合成：浅橙背景 + 顶部店铺名 + 居中小程序码 + 底部引导语/品牌。
     * 中文按字体可用性择优；无中文字体时退化为英文/数字，绝不出方块。
     */
    private byte[] composePoster(byte[] qrBytes, String shopName) throws Exception {
        BufferedImage poster = new BufferedImage(POSTER_W, POSTER_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = poster.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 背景：浅橙渐变感（纯色分层，避免依赖 Paint 复杂度）
            g.setColor(new Color(0xFF, 0xF3, 0xE8)); // 浅橙
            g.fillRect(0, 0, POSTER_W, POSTER_H);
            g.setColor(new Color(0xFF, 0x6B, 0x35)); // 品牌橙顶栏
            g.fillRect(0, 0, POSTER_W, 120);

            String cjk = resolveCjkFont();
            boolean hasCjk = cjk != null;

            // 顶部标题
            if (hasCjk) {
                drawCenteredString(g, safeShopName(shopName, true),
                        new Font(cjk, Font.BOLD, 40), Color.WHITE, POSTER_W / 2, 78);
            } else {
                drawCenteredString(g, "SCAN TO ENTER SHOP",
                        new Font(Font.SANS_SERIF, Font.BOLD, 34), Color.WHITE, POSTER_W / 2, 74);
            }

            // 小程序码白卡 + 居中贴码
            int cardPad = 30;
            int cardSize = QR_SIZE + cardPad * 2;
            int cardX = (POSTER_W - cardSize) / 2;
            int cardY = 190;
            g.setColor(Color.WHITE);
            g.fillRoundRect(cardX, cardY, cardSize, cardSize, 28, 28);
            g.setColor(new Color(0xFF, 0xD9, 0xC2));
            g.drawRoundRect(cardX, cardY, cardSize, cardSize, 28, 28);

            BufferedImage qr = ImageIO.read(new ByteArrayInputStream(qrBytes));
            if (qr != null) {
                int qrX = (POSTER_W - QR_SIZE) / 2;
                int qrY = cardY + cardPad;
                g.drawImage(qr, qrX, qrY, QR_SIZE, QR_SIZE, null);
            }

            int belowCardY = cardY + cardSize + 70;
            if (hasCjk) {
                drawCenteredString(g, "长按识别小程序码 · 进店下单赚推广积分",
                        new Font(cjk, Font.BOLD, 26), new Color(0x33, 0x33, 0x33), POSTER_W / 2, belowCardY);
                drawCenteredString(g, "邀三惠",
                        new Font(cjk, Font.BOLD, 44), new Color(0xFF, 0x6B, 0x35), POSTER_W / 2, belowCardY + 90);
            } else {
                drawCenteredString(g, "Long-press to open Mini Program",
                        new Font(Font.SANS_SERIF, Font.PLAIN, 22), new Color(0x33, 0x33, 0x33), POSTER_W / 2, belowCardY);
                drawCenteredString(g, "YAOSANHUI",
                        new Font(Font.SANS_SERIF, Font.BOLD, 40), new Color(0xFF, 0x6B, 0x35), POSTER_W / 2, belowCardY + 90);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(poster, "png", out);
        return out.toByteArray();
    }

    /** 店铺名兜底 + 过长截断，避免撑出画布。 */
    private static String safeShopName(String shopName, boolean cjk) {
        String name = StrUtil.isNotBlank(shopName) ? shopName.trim() : "扫码进店";
        if (name.length() > 12) {
            name = name.substring(0, 12);
        }
        return name;
    }

    private static void drawCenteredString(Graphics2D g, String text, Font font, Color color, int centerX, int baselineY) {
        g.setFont(font);
        g.setColor(color);
        int textW = g.getFontMetrics().stringWidth(text);
        g.drawString(text, centerX - textW / 2, baselineY);
    }

    /**
     * 惰性探测系统里能显示中文的字体：优先常见 CJK 字体名，再兜底遍历所有字体用
     * {@code canDisplay('店')} 判定。找不到返回 null → 走英文版海报。
     */
    private String resolveCjkFont() {
        if (cjkFontResolved) {
            return cjkFontName;
        }
        synchronized (this) {
            if (cjkFontResolved) {
                return cjkFontName;
            }
            char probe = '店'; // '店'
            String[] preferred = {
                    "Microsoft YaHei", "微软雅黑", "SimHei", "SimSun", "PingFang SC",
                    "Noto Sans CJK SC", "Noto Sans CJK", "Source Han Sans SC",
                    "WenQuanYi Zen Hei", "WenQuanYi Micro Hei", "Droid Sans Fallback",
                    "Heiti SC", "STHeiti"
            };
            String found = null;
            for (String name : preferred) {
                Font f = new Font(name, Font.PLAIN, 20);
                // 仅当该字体真实存在（字族名匹配）且能显示中文才采用
                if (name.equalsIgnoreCase(f.getFamily()) || name.equalsIgnoreCase(f.getName())) {
                    if (f.canDisplay(probe)) {
                        found = name;
                        break;
                    }
                }
            }
            if (found == null) {
                try {
                    for (Font f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
                        if (f.canDisplay(probe)) {
                            found = f.getName();
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[resolveCjkFont] 遍历系统字体异常: {}", e.getMessage());
                }
            }
            cjkFontName = found;
            cjkFontResolved = true;
            log.info("[resolveCjkFont] 中文字体探测结果: {}", found == null ? "无（海报走英文版）" : found);
            return cjkFontName;
        }
    }

}
