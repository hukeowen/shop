package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMyShopRelRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberWithdrawApplyDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberWithdrawApplyMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import cn.iocoder.yudao.module.merchant.service.MemberShopRelService;
import cn.iocoder.yudao.module.merchant.service.promo.ReferralService;
import cn.iocoder.yudao.module.merchant.service.promo.StarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 会员×商户关系（余额/积分/推荐人）
 */
@Tag(name = "商户小程序 - 会员店铺关系")
@RestController
@RequestMapping("/merchant/mini/member-rel")
@Validated
public class AppMemberShopRelController {

    @Resource
    private MemberShopRelService memberShopRelService;
    @Resource
    private MemberWithdrawApplyMapper memberWithdrawApplyMapper;
    @Resource
    private ReferralService referralService;
    @Resource
    private StarService starService;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private ShopUserStarMapper shopUserStarMapper;

    /**
     * 列出当前用户访问过的所有店铺（设计 9.2 节"店铺级收藏"语义，按 lastVisitAt 倒序）。
     */
    @GetMapping("/my-shops")
    @Operation(summary = "我访问过的所有店铺（即店铺级收藏夹）")
    @TenantIgnore
    public CommonResult<java.util.List<MemberShopRelDO>> listMyShops() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(memberShopRelService.listByUserId(userId));
    }

    /**
     * 增强版：返回带店铺名/封面/星级/余额/积分/最近访问的完整 VO，C 端 user-home /
     * user-me 一次拉全。每个 tenantId 用 TenantUtils.execute 切租户查 shop_info 和
     * shop_user_star，性能会随店铺数线性增长，正常用户加入店铺数 ≤ 50 不会成瓶颈。
     *
     * @param onlyFavorite 是否只返收藏店铺（true=仅 favorite=1，false=全部）
     */
    @GetMapping("/my-shops-enriched")
    @Operation(summary = "我加入的店铺（含店铺名/封面/星级/余额/积分）")
    @TenantIgnore
    public CommonResult<java.util.List<AppMyShopRelRespVO>> listMyShopsEnriched(
            @RequestParam(value = "onlyFavorite", required = false, defaultValue = "false") Boolean onlyFavorite) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        java.util.List<MemberShopRelDO> rels = memberShopRelService.listByUserId(userId);
        if (rels == null || rels.isEmpty()) {
            return success(java.util.Collections.emptyList());
        }
        java.util.List<AppMyShopRelRespVO> out = new java.util.ArrayList<>(rels.size());
        for (MemberShopRelDO rel : rels) {
            if (Boolean.TRUE.equals(onlyFavorite) && !Boolean.TRUE.equals(rel.getFavorite())) {
                continue;
            }
            AppMyShopRelRespVO vo = new AppMyShopRelRespVO();
            vo.setTenantId(rel.getTenantId());
            vo.setBalance(rel.getBalance() == null ? 0 : rel.getBalance());
            vo.setPoints(rel.getPoints() == null ? 0 : rel.getPoints());
            vo.setLastVisitAt(rel.getLastVisitAt());
            vo.setReferrerUserId(rel.getReferrerUserId());
            // shop_info 是 BaseDO（tenantId 普通字段），跨租户查直接 mapper 即可
            try {
                ShopInfoDO shop = shopInfoMapper.selectByTenantId(rel.getTenantId());
                if (shop != null) {
                    vo.setShopName(shop.getShopName());
                    vo.setCoverUrl(shop.getCoverUrl());
                    vo.setAddress(shop.getAddress());
                    vo.setBusinessHours(shop.getBusinessHours());
                }
            } catch (Exception ignore) {}
            // 店铺名兜底：shop_info 可能没建（merchant_info 是 TenantBaseDO 要切租户查）
            if (vo.getShopName() == null) {
                try {
                    Long uid = userId;
                    Long tid = rel.getTenantId();
                    TenantUtils.execute(tid, () -> {
                        java.util.List<MerchantDO> mlist = merchantMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantDO>()
                                        .eq(MerchantDO::getTenantId, tid)
                                        .last("LIMIT 1"));
                        if (!mlist.isEmpty() && vo.getShopName() == null) {
                            vo.setShopName(mlist.get(0).getName());
                        }
                    });
                } catch (Exception ignore) {}
            }
            // shop_user_star 是 TenantBaseDO，要切租户查
            try {
                Long tid = rel.getTenantId();
                TenantUtils.execute(tid, () -> {
                    ShopUserStarDO star = shopUserStarMapper.selectByUserId(userId);
                    if (star != null && star.getCurrentStar() != null) {
                        vo.setStar(star.getCurrentStar());
                    } else {
                        vo.setStar(0);
                    }
                });
            } catch (Exception e) {
                vo.setStar(0);
            }
            out.add(vo);
        }
        return success(out);
    }

    /**
     * 切换店铺收藏（C 端 shop-home 顶部 ♥ 按钮）。
     * favorite=true → 标记收藏；false → 取消。如果用户尚未访问过该店，会先建 rel
     * 记录再标 favorite。
     */
    @PostMapping("/favorite/toggle")
    @Operation(summary = "切换店铺收藏")
    @TenantIgnore
    public CommonResult<Boolean> toggleFavorite(
            @RequestParam("tenantId") @NotNull Long tenantId,
            @RequestParam("favorite") @NotNull Boolean favorite) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        // 不存在则先建空 rel（不绑推荐人）
        MemberShopRelDO rel = memberShopRelService.getByUserAndTenant(userId, tenantId);
        if (rel == null) {
            memberShopRelService.getOrCreateWithReferrer(userId, tenantId, null);
        }
        memberShopRelService.setFavorite(userId, tenantId, favorite);
        return success(true);
    }

    /**
     * 获取当前登录用户在当前 tenant 的关系记录。
     * 不存在时返回默认空对象（balance=0, points=0）。
     */
    @GetMapping("/my")
    @Operation(summary = "获取当前用户在当前店铺的余额/积分")
    @TenantIgnore
    public CommonResult<MemberShopRelDO> getMy() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        MemberShopRelDO rel = memberShopRelService.getByUserAndTenant(userId, tenantId);
        if (rel == null) {
            rel = MemberShopRelDO.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .balance(0)
                    .points(0)
                    .build();
        }
        return success(rel);
    }

    /**
     * 记录进店：v6 严格语义 — 仅首次进店时绑定推荐人，已访问过的店铺即使再带 inviter 也不补绑。
     *
     * <p>判定依据：member_shop_rel(userId, tenantId) 是否已有记录。已存在 → 仅 update lastVisitAt；
     * 不存在 → 同时建 rel(referrerUserId) + 同步写 promo 营销引擎用的 shop_user_referral 表。</p>
     */
    @PostMapping("/visit")
    @Operation(summary = "记录进店（v6 严格语义：仅首次访问带 inviter 才绑上下级）")
    @Parameter(name = "tenantId", description = "商户租户ID", required = true)
    @Parameter(name = "referrerUserId", description = "推荐人用户ID（可选；仅在用户首次进店时生效）")
    @TenantIgnore
    public CommonResult<Boolean> visit(@RequestParam("tenantId") @NotNull Long tenantId,
                                       @RequestParam(value = "referrerUserId", required = false) Long referrerUserId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MemberShopRelDO existing = memberShopRelService.getByUserAndTenant(userId, tenantId);
        if (existing == null) {
            // ===== 首次进店 =====
            memberShopRelService.getOrCreateWithReferrer(userId, tenantId, referrerUserId);
            // 同步写营销引擎用的 shop_user_referral，使 v6 推 N 反 1 / 团队极差能识别上下级
            if (referrerUserId != null && referrerUserId > 0 && !referrerUserId.equals(userId)) {
                Long previousTenant = TenantContextHolder.getTenantId();
                try {
                    TenantContextHolder.setTenantId(tenantId);
                    boolean newlyBound = referralService.bindParent(userId, referrerUserId, null);
                    if (newlyBound) {
                        starService.handleReferralBound(referrerUserId);
                    }
                } finally {
                    if (previousTenant != null) {
                        TenantContextHolder.setTenantId(previousTenant);
                    } else {
                        TenantContextHolder.clear();
                    }
                }
            }
        } else {
            // ===== 已访问过：v6 不再补绑，仅更新最近进店时间 =====
            memberShopRelService.updateLastVisitAt(userId, tenantId);
        }
        return success(true);
    }

    /**
     * 余额单向转积分（1分=1积分）。
     */
    @PostMapping("/balance-to-points")
    @Operation(summary = "余额转积分（1:1）")
    @Parameter(name = "amountFen", description = "转换金额（分）", required = true)
    @TenantIgnore
    public CommonResult<Boolean> balanceToPoints(@RequestParam("amountFen") @NotNull Integer amountFen) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        if (amountFen <= 0) {
            throw exception0(1_031_001_002, "转换金额必须大于 0");
        }
        memberShopRelService.balanceToPoints(userId, tenantId, amountFen);
        return success(true);
    }

    /**
     * 用户发起提现申请：原子扣减余额后插入申请记录。
     */
    @PostMapping("/withdraw")
    @Operation(summary = "用户发起提现申请")
    @TenantIgnore
    public CommonResult<Boolean> withdraw(
            @RequestParam("amount") @NotNull Integer amount,
            @RequestParam("withdrawType") @NotNull Integer withdrawType,
            @RequestParam(value = "accountName", required = false) String accountName,
            @RequestParam(value = "accountNo", required = false) String accountNo,
            @RequestParam(value = "bankName", required = false) String bankName) {
        if (amount <= 0) {
            throw exception0(1_031_001_003, "提现金额必须大于 0");
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        // 原子扣减余额，返回 0 说明余额不足
        int affected = memberShopRelService.deductBalance(userId, tenantId, amount);
        if (affected == 0) {
            throw exception0(1_031_001_004, "余额不足");
        }
        MemberWithdrawApplyDO apply = MemberWithdrawApplyDO.builder()
                .userId(userId)
                .tenantId(tenantId)
                .amount(amount)
                .withdrawType(withdrawType)
                .accountName(accountName)
                .accountNo(accountNo)
                .bankName(bankName)
                .status(0)
                .build();
        memberWithdrawApplyMapper.insert(apply);
        return success(true);
    }

    /**
     * 查询当前用户在当前店铺的提现记录列表。
     */
    @GetMapping("/withdraw/list")
    @Operation(summary = "查询当前用户提现记录")
    @TenantIgnore
    public CommonResult<List<MemberWithdrawApplyDO>> withdrawList() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        return success(memberWithdrawApplyMapper.selectByUserIdAndTenantId(userId, tenantId));
    }

}
