package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.controller.app.vo.coupon.AppShopCouponSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.coupon.ShopCouponDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.coupon.ShopCouponUserDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.coupon.ShopCouponMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.coupon.ShopCouponUserMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 优惠券：商户自建模板 + C 端领取。
 *
 * <p>商户端走 {@code /app-api/merchant/mini/coupon/*}（按 token 租户隔离），
 * C 端拉某店可领券走 {@code /app-api/merchant/shop/public/coupons?tenantId=X}（跨租户）。</p>
 */
@Tag(name = "用户/商户端 - 优惠券")
@RestController
@RequestMapping
@Validated
@Slf4j
public class AppMerchantCouponController {

    @Resource
    private ShopCouponMapper couponMapper;
    @Resource
    private ShopCouponUserMapper couponUserMapper;
    @Resource
    private MerchantService merchantService;

    /**
     * 商户身份门：未登录或非商户角色一律抛 401。
     * 同时返当前商户实体，供后续写入操作做 tenantId 比对（防越权改别人租户的券）。
     */
    private MerchantDO getMerchantOrThrow() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw new ServiceException(401, "请先登录");
        }
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw new ServiceException(403, "当前账号未开通商户");
        }
        return merchant;
    }

    /**
     * 商户写入操作前的「资源属主校验」：确认 coupon 属于当前商户租户。
     * 即使 token 切到别的租户，也挡得住越权改他人券。
     */
    private ShopCouponDO loadOwnCouponOrThrow(Long couponId, MerchantDO merchant) {
        if (couponId == null) {
            throw new ServiceException(404, "券模板不存在");
        }
        ShopCouponDO row = couponMapper.selectById(couponId);
        if (row == null) {
            throw new ServiceException(404, "券模板不存在");
        }
        if (!java.util.Objects.equals(row.getTenantId(), merchant.getTenantId())) {
            log.warn("[coupon] 越权操作 couponId={} 属租户={} 当前商户={} userId={}",
                    couponId, row.getTenantId(), merchant.getTenantId(), merchant.getUserId());
            throw new ServiceException(403, "无权操作该券");
        }
        return row;
    }

    // ==================== 商户端：自建模板（token 租户隔离） ====================

    @GetMapping("/app-api/merchant/mini/coupon/list")
    @Operation(summary = "商户：列出本店所有券模板（含已下架）")
    public CommonResult<List<ShopCouponDO>> merchantList() {
        getMerchantOrThrow();
        return success(couponMapper.selectAllByMerchant());
    }

    @PostMapping("/app-api/merchant/mini/coupon/save")
    @Operation(summary = "商户：新建/编辑券模板（带 id=编辑）")
    public CommonResult<Long> merchantSave(@Valid @RequestBody AppShopCouponSaveReqVO reqVO) {
        MerchantDO merchant = getMerchantOrThrow();
        ShopCouponDO row;
        if (reqVO.getId() != null) {
            // 编辑：必须属于当前商户租户
            row = loadOwnCouponOrThrow(reqVO.getId(), merchant);
        } else {
            row = ShopCouponDO.builder()
                    .takenCount(0)
                    .status(0) // 默认上架
                    .build();
        }
        row.setName(reqVO.getName());
        row.setDiscountAmount(reqVO.getDiscountAmount());
        row.setMinAmount(reqVO.getMinAmount());
        row.setTag(reqVO.getTag());
        row.setTotalCount(reqVO.getTotalCount());
        row.setValidDays(reqVO.getValidDays());
        if (reqVO.getId() != null) {
            couponMapper.updateById(row);
        } else {
            // 新建：tenantId 由 TenantBaseDO 自动注入（merchant 登录态下已是其租户）
            couponMapper.insert(row);
        }
        return success(row.getId());
    }

    @PutMapping("/app-api/merchant/mini/coupon/{id}/status")
    @Operation(summary = "商户：上/下架券模板")
    public CommonResult<Boolean> merchantUpdateStatus(@PathVariable("id") Long id,
                                                      @RequestParam("status") Integer status) {
        MerchantDO merchant = getMerchantOrThrow();
        ShopCouponDO row = loadOwnCouponOrThrow(id, merchant);
        row.setStatus(status);
        couponMapper.updateById(row);
        return success(true);
    }

    @DeleteMapping("/app-api/merchant/mini/coupon/{id}")
    @Operation(summary = "商户：删除券模板（软删）")
    public CommonResult<Boolean> merchantDelete(@PathVariable("id") Long id) {
        MerchantDO merchant = getMerchantOrThrow();
        loadOwnCouponOrThrow(id, merchant);  // 仅校验属主，让 deleteById 走标准软删
        couponMapper.deleteById(id);
        return success(true);
    }

    // ==================== C 端：拉某店可领券 + 领取 ====================

    @GetMapping("/app-api/merchant/shop/public/coupons")
    @Operation(summary = "C 端：拉某店可领券列表（按 tenantId）")
    @Parameter(name = "tenantId", description = "店铺所属租户 ID", required = true)
    @PermitAll
    @TenantIgnore
    public CommonResult<List<Map<String, Object>>> publicListByTenant(
            @RequestParam("tenantId") Long tenantId) {
        List<ShopCouponDO> list = TenantUtils.execute(tenantId,
                () -> couponMapper.selectEnabledByTenant());
        // 用户已领过的标 taken；未登录则全 false
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Set<Long> takenIds = userId == null
                ? Collections.emptySet()
                : TenantUtils.execute(tenantId, () -> couponUserMapper.selectTakenCouponIds(userId));
        List<Map<String, Object>> resp = new ArrayList<>(list.size());
        for (ShopCouponDO c : list) {
            Map<String, Object> m = cn.hutool.core.bean.BeanUtil.beanToMap(c, false, true);
            m.put("taken", takenIds.contains(c.getId()));
            // 库存剩余：0=不限
            int remain = c.getTotalCount() == null || c.getTotalCount() == 0
                    ? -1
                    : Math.max(0, c.getTotalCount() - (c.getTakenCount() == null ? 0 : c.getTakenCount()));
            m.put("remain", remain);
            resp.add(m);
        }
        return success(resp);
    }

    @PostMapping("/app-api/merchant/mini/coupon/grab")
    @Operation(summary = "C 端：领取某店的某张券")
    @TenantIgnore // 跨租户：用户 token tenant ≠ 商户 tenant，需手工切租户后写入
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Long> grab(@RequestParam("tenantId") Long tenantId,
                                    @RequestParam("couponId") Long couponId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw new ServiceException(401, "请先登录");
        }
        Long prevTenant = TenantContextHolder.getTenantId();
        Boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(false);
            TenantContextHolder.setTenantId(tenantId);

            ShopCouponDO coupon = couponMapper.selectById(couponId);
            if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 0) {
                throw new ServiceException(404, "券不存在或已下架");
            }
            // 幂等：同一用户同一券限领一张（DB UNIQUE 兜底）
            ShopCouponUserDO existed = couponUserMapper.selectByUserIdAndCouponId(userId, couponId);
            if (existed != null) {
                return success(existed.getId());
            }
            LocalDateTime now = LocalDateTime.now();
            // 关键：原子递增 taken_count（防 grab 并发超发）。
            //   atomicIncrTaken 内置 WHERE total_count=0 OR taken_count<total_count，
            //   返回 0 = 已领完 / 已下架；返回 1 = 占住一张库存。
            //   update_time 显式传 LocalDateTime.now() 以保证应用层时区一致（避免 NOW() 走 DB 时区）。
            int updated = couponMapper.atomicIncrTaken(couponId, now);
            if (updated == 0) {
                throw new ServiceException(400, "已领完");
            }
            ShopCouponUserDO row = ShopCouponUserDO.builder()
                    .couponId(couponId)
                    .userId(userId)
                    .discountAmount(coupon.getDiscountAmount())
                    .minAmount(coupon.getMinAmount())
                    .effectiveTime(now)
                    .expireTime(now.plusDays(coupon.getValidDays() == null ? 30 : coupon.getValidDays()))
                    .status(0)
                    .build();
            try {
                couponUserMapper.insert(row);
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                // UNIQUE 兜底场景：P1/P2 两个并发请求同时通过了「已领」幂等检查并都 atomicIncrTaken
                // 成功（taken_count 被两次 +1），但 INSERT 时 (user_id, coupon_id) UNIQUE 拦下一条。
                //
                // 友好且账本正确的处理：
                //   1. 显式补偿性 -1（atomicDecrTaken），抵消本线程多扣的库存名额。注意必须用
                //      atomicDecrTaken 而不是依赖事务回滚 —— 我们要保留下面 SELECT existed
                //      的成功路径，不能 throw 让事务回滚。
                //   2. 重查 existed 返同样的 id —— 用户网络重发或并发都拿一致结果（200 OK 含同 id）。
                //
                // 这样 P1/P2 都返同一张券记录的 id，taken_count = 实际发出张数（不超发）。
                log.warn("[grab] duplicate key user_id={} coupon_id={}, 重发幂等返已领 id", userId, couponId);
                couponMapper.atomicDecrTaken(couponId, now);
                ShopCouponUserDO again = couponUserMapper.selectByUserIdAndCouponId(userId, couponId);
                if (again != null) {
                    return success(again.getId());
                }
                // 极端情况：DupKey 但 select 又找不到（理论上几乎不可能，除非另一并发刚回滚）
                throw new ServiceException(500, "领券失败，请重试");
            }
            return success(row.getId());
        } finally {
            TenantContextHolder.setTenantId(prevTenant);
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @GetMapping("/app-api/merchant/mini/coupon/my-list")
    @Operation(summary = "C 端：我领过的券（按 tenantId 过滤）")
    @TenantIgnore
    public CommonResult<List<ShopCouponUserDO>> myList(
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) return success(Collections.emptyList());
        if (tenantId == null) {
            // 不指定租户 → 跨租户拉全部
            return success(couponUserMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopCouponUserDO>()
                            .eq(ShopCouponUserDO::getUserId, userId)));
        }
        return success(TenantUtils.execute(tenantId, () -> couponUserMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopCouponUserDO>()
                        .eq(ShopCouponUserDO::getUserId, userId))));
    }
}
