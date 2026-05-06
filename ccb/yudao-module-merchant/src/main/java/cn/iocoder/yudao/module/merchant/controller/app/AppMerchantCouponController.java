package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.controller.app.vo.coupon.AppShopCouponSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.coupon.ShopCouponDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.coupon.ShopCouponUserDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.coupon.ShopCouponMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.coupon.ShopCouponUserMapper;
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

    // ==================== 商户端：自建模板（token 租户隔离） ====================

    @GetMapping("/app-api/merchant/mini/coupon/list")
    @Operation(summary = "商户：列出本店所有券模板（含已下架）")
    public CommonResult<List<ShopCouponDO>> merchantList() {
        return success(couponMapper.selectAllByMerchant());
    }

    @PostMapping("/app-api/merchant/mini/coupon/save")
    @Operation(summary = "商户：新建/编辑券模板（带 id=编辑）")
    public CommonResult<Long> merchantSave(@Valid @RequestBody AppShopCouponSaveReqVO reqVO) {
        ShopCouponDO row;
        if (reqVO.getId() != null) {
            row = couponMapper.selectById(reqVO.getId());
            if (row == null) {
                throw new ServiceException(404, "券模板不存在");
            }
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
            couponMapper.insert(row);
        }
        return success(row.getId());
    }

    @PutMapping("/app-api/merchant/mini/coupon/{id}/status")
    @Operation(summary = "商户：上/下架券模板")
    public CommonResult<Boolean> merchantUpdateStatus(@PathVariable("id") Long id,
                                                      @RequestParam("status") Integer status) {
        ShopCouponDO row = couponMapper.selectById(id);
        if (row == null) {
            throw new ServiceException(404, "券模板不存在");
        }
        row.setStatus(status);
        couponMapper.updateById(row);
        return success(true);
    }

    @DeleteMapping("/app-api/merchant/mini/coupon/{id}")
    @Operation(summary = "商户：删除券模板（软删）")
    public CommonResult<Boolean> merchantDelete(@PathVariable("id") Long id) {
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
            // 库存检查
            if (coupon.getTotalCount() != null && coupon.getTotalCount() > 0
                    && (coupon.getTakenCount() != null && coupon.getTakenCount() >= coupon.getTotalCount())) {
                throw new ServiceException(400, "已领完");
            }
            // 幂等：同一用户同一券限领一张（DB UNIQUE 兜底）
            ShopCouponUserDO existed = couponUserMapper.selectByUserIdAndCouponId(userId, couponId);
            if (existed != null) {
                return success(existed.getId());
            }
            LocalDateTime now = LocalDateTime.now();
            ShopCouponUserDO row = ShopCouponUserDO.builder()
                    .couponId(couponId)
                    .userId(userId)
                    .discountAmount(coupon.getDiscountAmount())
                    .minAmount(coupon.getMinAmount())
                    .effectiveTime(now)
                    .expireTime(now.plusDays(coupon.getValidDays() == null ? 30 : coupon.getValidDays()))
                    .status(0)
                    .build();
            couponUserMapper.insert(row);
            // 已领数 +1
            coupon.setTakenCount((coupon.getTakenCount() == null ? 0 : coupon.getTakenCount()) + 1);
            couponMapper.updateById(coupon);
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
