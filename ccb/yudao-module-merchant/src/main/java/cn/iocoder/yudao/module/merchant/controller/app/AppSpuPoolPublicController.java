package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.promo.AppSpuPoolPayoutItemRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.promo.AppSpuPoolSettleRecordRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolPayoutItemDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolSettleRecordDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolPayoutItemMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolSettleRecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户端 - 奖池公示。
 *
 * <p>展示位置（详见 docs/design/marketing-system-v8.md）：</p>
 * <ul>
 *   <li>商品详情页「本期中奖名单」模块 — getLatestPayouts</li>
 *   <li>独立入口「奖池公示」页 — listSettleRecords + getPayoutsBySettle</li>
 * </ul>
 *
 * <p>权限：已登录用户即可查（JWT Bearer）。脱敏：昵称按 「张** + 末字」规则脱敏；
 * 头像不脱敏（用户头像本身就是公开的）；用户 id 不返给前端。</p>
 */
@Tag(name = "用户 App - 星级奖池公示（登录可见）")
@RestController
@RequestMapping("/merchant/promo/pool")   // 框架自动加 /app-api 前缀
@Validated
public class AppSpuPoolPublicController {

    @Resource
    private SpuStarPoolMapper spuStarPoolMapper;
    @Resource
    private SpuStarPoolSettleRecordMapper settleRecordMapper;
    @Resource
    private SpuStarPoolPayoutItemMapper payoutItemMapper;
    @Resource
    private MemberUserApi memberUserApi;

    @GetMapping("/balance")
    @Operation(summary = "查某 SPU 当前池余额 + 累计入/出（任何登录用户）")
    @Parameter(name = "spuId", required = true)
    public CommonResult<Map<String, Long>> getBalance(@RequestParam("spuId") @NotNull Long spuId) {
        SpuStarPoolDO pool = spuStarPoolMapper.selectBySpuId(spuId);
        Map<String, Long> resp = new java.util.HashMap<>();
        if (pool == null) {
            resp.put("poolBalance", 0L);
            resp.put("totalIn", 0L);
            resp.put("totalOut", 0L);
        } else {
            resp.put("poolBalance", pool.getPoolBalance() == null ? 0L : pool.getPoolBalance());
            resp.put("totalIn", pool.getTotalIn() == null ? 0L : pool.getTotalIn());
            resp.put("totalOut", pool.getTotalOut() == null ? 0L : pool.getTotalOut());
        }
        return success(resp);
    }

    @GetMapping("/latest-payouts")
    @Operation(summary = "商品详情页用：最近一次结算的中奖名单（≤ N 条）")
    public CommonResult<List<AppSpuPoolPayoutItemRespVO>> getLatestPayouts(
            @RequestParam("spuId") @NotNull Long spuId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        PageResult<SpuStarPoolSettleRecordDO> recordPage =
                settleRecordMapper.selectPageBySpuId(spuId, 1, 1);
        if (recordPage.getList().isEmpty()) {
            return success(java.util.Collections.emptyList());
        }
        SpuStarPoolSettleRecordDO latest = recordPage.getList().get(0);
        List<SpuStarPoolPayoutItemDO> items = payoutItemMapper.selectListBySettleId(latest.getId());
        if (limit > 0 && items.size() > limit) {
            items = items.subList(0, limit);
        }
        return success(toAppVOs(items));
    }

    @GetMapping("/settle-records")
    @Operation(summary = "独立公示页：分页查某 SPU 历次结算")
    public CommonResult<PageResult<AppSpuPoolSettleRecordRespVO>> listSettleRecords(
            @RequestParam("spuId") @NotNull Long spuId,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        PageResult<SpuStarPoolSettleRecordDO> page = settleRecordMapper.selectPageBySpuId(spuId, pageNo, pageSize);
        List<AppSpuPoolSettleRecordRespVO> list = new ArrayList<>(page.getList().size());
        for (SpuStarPoolSettleRecordDO r : page.getList()) {
            AppSpuPoolSettleRecordRespVO v = new AppSpuPoolSettleRecordRespVO();
            v.setId(r.getId());
            v.setSpuId(r.getSpuId());
            v.setPoolBalanceBefore(r.getPoolBalanceBefore());
            v.setTotalDistributed(r.getTotalDistributed());
            v.setCreateTime(r.getCreateTime());
            // winnerCount 单独查 payout_item 的 count 以避免大字段冗余
            v.setWinnerCount(payoutItemMapper.selectListBySettleId(r.getId()).size());
            list.add(v);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/settle-record/payouts")
    @Operation(summary = "独立公示页：查某次结算的中奖名单（脱敏）")
    public CommonResult<List<AppSpuPoolPayoutItemRespVO>> getPayoutsBySettle(
            @RequestParam("settleId") @NotNull Long settleId) {
        List<SpuStarPoolPayoutItemDO> items = payoutItemMapper.selectListBySettleId(settleId);
        return success(toAppVOs(items));
    }

    private List<AppSpuPoolPayoutItemRespVO> toAppVOs(List<SpuStarPoolPayoutItemDO> items) {
        if (items.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 批量查 member 信息一次性出
        Set<Long> userIds = items.stream().map(SpuStarPoolPayoutItemDO::getUserId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, MemberUserRespDTO> userMap;
        try {
            userMap = memberUserApi.getUserMap(userIds);
        } catch (Exception e) {
            userMap = java.util.Collections.emptyMap();
        }
        Long selfId = null;
        try {
            selfId = SecurityFrameworkUtils.getLoginUserId();
        } catch (Exception ignored) {
        }
        List<AppSpuPoolPayoutItemRespVO> out = new ArrayList<>(items.size());
        for (SpuStarPoolPayoutItemDO d : items) {
            AppSpuPoolPayoutItemRespVO v = new AppSpuPoolPayoutItemRespVO();
            v.setId(d.getId());
            v.setSettleId(d.getSettleId());
            v.setSpuId(d.getSpuId());
            v.setStar(d.getStar());
            v.setMode(d.getMode());
            v.setAmount(d.getAmount());
            v.setCreateTime(d.getCreateTime());
            v.setIsSelf(Objects.equals(selfId, d.getUserId()));
            MemberUserRespDTO mu = userMap.get(d.getUserId());
            v.setMaskedNickname(maskNickname(mu == null ? null : mu.getNickname(), d.getUserId()));
            v.setAvatar(mu == null ? null : mu.getAvatar());
            out.add(v);
        }
        return out;
    }

    /**
     * 昵称脱敏：
     * <ul>
     *   <li>null/空 → "用户" + (userId 末 4 位)</li>
     *   <li>1 字 → 该字 + "**"</li>
     *   <li>2 字 → 首字 + "*"</li>
     *   <li>≥3 字 → 首字 + "**" + 末字</li>
     * </ul>
     */
    private String maskNickname(String nickname, Long userId) {
        if (nickname == null || nickname.trim().isEmpty()) {
            String suffix = String.valueOf(userId == null ? 0 : userId % 10000);
            return "用户" + suffix;
        }
        String n = nickname.trim();
        if (n.length() == 1) return n + "**";
        if (n.length() == 2) return n.charAt(0) + "*";
        return n.charAt(0) + "**" + n.charAt(n.length() - 1);
    }

    private static <T> CommonResult<T> success(T data) {
        return CommonResult.success(data);
    }

}
