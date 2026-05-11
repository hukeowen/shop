package cn.iocoder.yudao.module.merchant.service.saas;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 平台商户首次启动初始化 —— 把 docs/测试参数.txt 的通联凭据 + 密码 BCrypt 落库。
 *
 * <p>幂等：每次启动检测，已配置则跳过。</p>
 *
 * <p>背景：V035 SQL 建了平台商户的 system_tenant / member_user / merchant_info / shop_info
 * 骨架，但密码哈希 + 通联凭据（AES 加密落库）需要 Java 才能正确生成，故由本初始化器完成。</p>
 *
 * <p>关键凭据写入 shop_info(id=999, tenant=999)：</p>
 * <ul>
 *   <li>tl_mch_id = 56165105331VE5Z</li>
 *   <li>tl_app_id = 00240592</li>
 *   <li>tl_sign_type = SM2</li>
 *   <li>tl_sm2_private_key / tl_sm2_public_key（@EncryptTypeHandler 自动 AES 加密）</li>
 *   <li>tl_rsa_private_key（备用）</li>
 * </ul>
 */
@Component
@Order(100)  // 在 schema 迁移之后
@Slf4j
public class PlatformMerchantInitializer implements ApplicationRunner {

    private static final long PLATFORM_TENANT_ID = 999L;
    private static final long PLATFORM_USER_ID = 999L;
    private static final long PLATFORM_SHOP_ID = 999L;
    private static final String PLATFORM_PASSWORD_PLAIN = "yhzc123456";

    @Resource
    private DataSource dataSource;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource(name = "passwordEncoder")
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            initPasswordIfBlank();
            initTlpayCredentialsIfBlank();
        } catch (Throwable t) {
            log.error("[PlatformMerchantInitializer] 初始化失败（不阻塞启动）", t);
        }
    }

    private void initPasswordIfBlank() throws Exception {
        try (Connection con = dataSource.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT password FROM member_user WHERE id = ?")) {
                ps.setLong(1, PLATFORM_USER_ID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        log.warn("[PlatformMerchantInitializer] member_user id=999 不存在，跳过密码初始化（V035 SQL 未执行）");
                        return;
                    }
                    String existing = rs.getString(1);
                    if (existing != null && existing.length() >= 30) {
                        log.info("[PlatformMerchantInitializer] member_user.password 已为 BCrypt 形态，跳过");
                        return;
                    }
                }
            }
            String hash = passwordEncoder.encode(PLATFORM_PASSWORD_PLAIN);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE member_user SET password = ?, update_time = NOW() WHERE id = ?")) {
                ps.setString(1, hash);
                ps.setLong(2, PLATFORM_USER_ID);
                int rows = ps.executeUpdate();
                log.info("[PlatformMerchantInitializer] ✅ 平台商户密码已写入（BCrypt）rows={}", rows);
            }
        }
    }

    private void initTlpayCredentialsIfBlank() {
        ShopInfoDO shop = TenantUtils.executeIgnore(() -> shopInfoMapper.selectById(PLATFORM_SHOP_ID));
        if (shop == null) {
            log.warn("[PlatformMerchantInitializer] shop_info id=999 不存在，跳过通联凭据初始化");
            return;
        }
        if (shop.getTlMchId() != null && !shop.getTlMchId().isEmpty()
                && shop.getTlSm2PrivateKey() != null && !shop.getTlSm2PrivateKey().isEmpty()) {
            log.info("[PlatformMerchantInitializer] 通联凭据已就位，跳过");
            return;
        }
        // 从 docs/测试参数.txt 读
        String[] lines = readTestParams();
        if (lines == null) return;

        String rsaPriv = "-----BEGIN PRIVATE KEY-----\n" + lines[5] + "\n-----END PRIVATE KEY-----";
        String sm2Priv = "-----BEGIN PRIVATE KEY-----\n" + lines[8] + "\n-----END PRIVATE KEY-----";
        String line9 = lines[9];
        int colon = line9.indexOf(':');
        if (colon < 0) colon = line9.indexOf('：');
        String sm2PubB64 = colon >= 0 ? line9.substring(colon + 1).trim() : line9.trim();
        String sm2Pub = "-----BEGIN PUBLIC KEY-----\n" + sm2PubB64 + "\n-----END PUBLIC KEY-----";

        ShopInfoDO patch = new ShopInfoDO();
        patch.setId(PLATFORM_SHOP_ID);
        patch.setTlEnabled(true);
        patch.setTlMchId("56165105331VE5Z");
        patch.setTlAppId("00240592");
        patch.setTlSignType("SM2");
        patch.setTlRsaPrivateKey(rsaPriv);
        patch.setTlSm2PrivateKey(sm2Priv);
        patch.setTlSm2PublicKey(sm2Pub);
        TenantUtils.executeIgnore(() -> {
            shopInfoMapper.updateById(patch);
            return null;
        });
        log.info("[PlatformMerchantInitializer] ✅ 平台商户通联凭据已加密写入（cusId=56165105331VE5Z signType=SM2）");
    }

    private String[] readTestParams() {
        java.io.File f = new java.io.File("docs/测试参数.txt");
        if (!f.exists()) {
            // 部署环境可能不带 docs/，跳过（用户手工写入 admin 配置）
            log.warn("[PlatformMerchantInitializer] docs/测试参数.txt 不存在，跳过自动写入（请手工配置平台商户通联凭据）");
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
            java.util.List<String> all = new java.util.ArrayList<>();
            String l;
            while ((l = reader.readLine()) != null) all.add(l);
            if (all.size() < 10) {
                log.warn("[PlatformMerchantInitializer] 测试参数.txt 行数不足 10");
                return null;
            }
            return all.toArray(new String[0]);
        } catch (Exception e) {
            log.warn("[PlatformMerchantInitializer] 读 测试参数.txt 失败", e);
            return null;
        }
    }
}
