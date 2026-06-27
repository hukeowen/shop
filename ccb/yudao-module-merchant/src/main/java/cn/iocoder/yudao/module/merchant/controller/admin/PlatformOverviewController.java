package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberShopRelMapper;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantSubscriptionOrderDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.SaasPackageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.MerchantSubscriptionOrderMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.SaasPackageConfigMapper;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.dal.dataobject.comment.ProductCommentDO;
import cn.iocoder.yudao.module.product.dal.mysql.comment.ProductCommentMapper;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 平台运营总览（独立模块，不改 yudao 自带商城页面）。
 *
 * <p>超管跨所有商户租户聚合查看/管理，关联 shop_info 显示店铺名，
 * 免去顶部一个个切换租户。所有接口 @TenantIgnore 跨租户。</p>
 */
@Tag(name = "管理后台 - 平台运营总览")
@RestController
@RequestMapping("/merchant/platform")
@Validated
public class PlatformOverviewController {

    @Resource
    private ProductSpuMapper productSpuMapper;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private SaasPackageConfigMapper saasPackageConfigMapper;
    @Resource
    private MerchantSubscriptionOrderMapper merchantSubscriptionOrderMapper;
    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private MemberShopRelMapper memberShopRelMapper;
    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private ProductCommentMapper productCommentMapper;

    @GetMapping("/shops")
    @Operation(summary = "所有店铺（租户ID+店铺名+状态）—— 总览筛选下拉用")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<List<Map<String, Object>>> shops() {
        List<Map<String, Object>> resp = new ArrayList<>();
        for (ShopInfoDO s : shopInfoMapper.selectList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenantId", s.getTenantId());
            m.put("shopName", s.getShopName());
            m.put("status", s.getStatus());
            resp.add(m);
        }
        return success(resp);
    }

    @GetMapping("/product/page")
    @Operation(summary = "平台跨租户商品总览（全部店铺，可按店铺/名称/状态筛选）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> productPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        LambdaQueryWrapperX<ProductSpuDO> q = new LambdaQueryWrapperX<ProductSpuDO>()
                .likeIfPresent(ProductSpuDO::getName, name)
                .eqIfPresent(ProductSpuDO::getStatus, status)
                .eqIfPresent(ProductSpuDO::getTenantId, tenantId)
                .orderByDesc(ProductSpuDO::getId);
        PageResult<ProductSpuDO> page = productSpuMapper.selectPage(pageParam, q);
        Map<Long, String> shopNames = loadShopNames();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProductSpuDO spu : page.getList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", spu.getId());
            m.put("tenantId", spu.getTenantId());
            m.put("shopName", shopNames.getOrDefault(spu.getTenantId(), "租户" + spu.getTenantId()));
            m.put("name", spu.getName());
            m.put("picUrl", spu.getPicUrl());
            m.put("price", spu.getPrice());
            m.put("status", spu.getStatus());
            m.put("salesCount", spu.getSalesCount());
            m.put("stock", spu.getStock());
            m.put("createTime", spu.getCreateTime());
            list.add(m);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @PutMapping("/product/update-status")
    @Operation(summary = "平台上/下架某店铺商品（0下架 1上架 4回收站）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<Boolean> updateProductStatus(@RequestParam("id") Long id,
                                                     @RequestParam("status") Integer status) {
        ProductSpuDO update = new ProductSpuDO();
        update.setId(id);
        update.setStatus(status);
        productSpuMapper.updateById(update);
        return success(true);
    }

    @GetMapping("/subscription/page")
    @Operation(summary = "店铺套餐总览：每店当前套餐/到期时间/累计付费金额")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> subscriptionPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "level", required = false) String level) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        LambdaQueryWrapperX<MerchantDO> q = new LambdaQueryWrapperX<MerchantDO>()
                .eqIfPresent(MerchantDO::getTenantId, tenantId)
                .eqIfPresent(MerchantDO::getServicePackageLevel, level)
                .orderByDesc(MerchantDO::getId);
        PageResult<MerchantDO> page = merchantMapper.selectPage(pageParam, q);
        Map<Long, String> shopNames = loadShopNames();
        // 套餐档位 → 套餐名（试用版兜底）
        Map<String, String> levelName = new HashMap<>();
        levelName.put("TRIAL", "试用版");
        levelName.put("PLATFORM", "平台");
        for (SaasPackageConfigDO c : saasPackageConfigMapper.selectList()) {
            levelName.put(c.getLevel(), c.getName());
        }
        // 商户 → 累计已付金额（PAID）
        Map<Long, Integer> paidMap = new HashMap<>();
        for (MerchantSubscriptionOrderDO o : merchantSubscriptionOrderMapper.selectList(
                MerchantSubscriptionOrderDO::getPayStatus, MerchantSubscriptionOrderDO.PAY_STATUS_PAID)) {
            paidMap.merge(o.getMerchantId(), o.getPayAmountFen() == null ? 0 : o.getPayAmountFen(), Integer::sum);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (MerchantDO m : page.getList()) {
            String lv = m.getServicePackageLevel();
            Map<String, Object> mp = new LinkedHashMap<>();
            mp.put("merchantId", m.getId());
            mp.put("tenantId", m.getTenantId());
            mp.put("shopName", shopNames.getOrDefault(m.getTenantId(), m.getName()));
            mp.put("level", lv);
            mp.put("levelName", lv == null ? "试用版" : levelName.getOrDefault(lv, lv));
            mp.put("serviceExpireAt", m.getServiceExpireAt());
            mp.put("paid", lv != null && !"TRIAL".equals(lv) && !"PLATFORM".equals(lv));
            mp.put("totalPaidFen", paidMap.getOrDefault(m.getId(), 0));
            list.add(mp);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/order/page")
    @Operation(summary = "平台跨租户订单总览（全部店铺，按店铺/订单号/状态筛选）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> orderPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "no", required = false) String no,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        LambdaQueryWrapperX<TradeOrderDO> q = new LambdaQueryWrapperX<TradeOrderDO>()
                .likeIfPresent(TradeOrderDO::getNo, no)
                .eqIfPresent(TradeOrderDO::getStatus, status)
                .eqIfPresent(TradeOrderDO::getTenantId, tenantId)
                .orderByDesc(TradeOrderDO::getId);
        PageResult<TradeOrderDO> page = tradeOrderMapper.selectPage(pageParam, q);
        Map<Long, String> shopNames = loadShopNames();
        List<Map<String, Object>> list = new ArrayList<>();
        for (TradeOrderDO o : page.getList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("no", o.getNo());
            m.put("tenantId", o.getTenantId());
            m.put("shopName", shopNames.getOrDefault(o.getTenantId(), "租户" + o.getTenantId()));
            m.put("userId", o.getUserId());
            m.put("totalPrice", o.getTotalPrice());
            m.put("payPrice", o.getPayPrice());
            m.put("payStatus", o.getPayStatus());
            m.put("status", o.getStatus());
            m.put("payTime", o.getPayTime());
            m.put("createTime", o.getCreateTime());
            list.add(m);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/stats")
    @Operation(summary = "平台数据概览：订单总额/净销售额/套餐收入/商户·会员·商品数 等")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<Map<String, Object>> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        // 订单：GMV(下单总额) + 净销售额(实付)
        long gmvFen = 0, netFen = 0, paidOrders = 0;
        List<TradeOrderDO> orders = tradeOrderMapper.selectList();
        for (TradeOrderDO o : orders) {
            gmvFen += o.getTotalPrice() == null ? 0 : o.getTotalPrice();
            if (Boolean.TRUE.equals(o.getPayStatus())) {
                netFen += o.getPayPrice() == null ? 0 : o.getPayPrice();
                paidOrders++;
            }
        }
        m.put("orderCount", orders.size());
        m.put("paidOrderCount", paidOrders);
        m.put("orderTotalFen", gmvFen);
        m.put("netSalesFen", netFen);
        // 套餐收入（SaaS 平台收入）
        long subRevenueFen = 0;
        for (MerchantSubscriptionOrderDO s : merchantSubscriptionOrderMapper.selectList(
                MerchantSubscriptionOrderDO::getPayStatus, MerchantSubscriptionOrderDO.PAY_STATUS_PAID)) {
            subRevenueFen += s.getPayAmountFen() == null ? 0 : s.getPayAmountFen();
        }
        m.put("subscriptionRevenueFen", subRevenueFen);
        // 商户 / 付费商户 / 即将到期(30天内)
        List<MerchantDO> merchants = merchantMapper.selectList();
        int paidMerchants = 0, expiringSoon = 0;
        java.time.LocalDateTime soon = java.time.LocalDateTime.now().plusDays(30);
        java.time.LocalDateTime nowTime = java.time.LocalDateTime.now();
        for (MerchantDO mc : merchants) {
            String lv = mc.getServicePackageLevel();
            if (lv != null && !"TRIAL".equals(lv) && !"PLATFORM".equals(lv)) {
                paidMerchants++;
            }
            if (mc.getServiceExpireAt() != null
                    && mc.getServiceExpireAt().isAfter(nowTime) && mc.getServiceExpireAt().isBefore(soon)) {
                expiringSoon++;
            }
        }
        m.put("merchantCount", merchants.size());
        m.put("paidMerchantCount", paidMerchants);
        m.put("expiringSoonCount", expiringSoon);
        // 店铺 / 商品数（注意：ProductSpuMapper 重写了无参 selectCount() 为「警戒库存数」，
        // 这里必须用带 Wrapper 的 selectCount 统计全部商品）
        m.put("shopCount", shopInfoMapper.selectCount());
        m.put("productCount", productSpuMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductSpuDO>()));
        return success(m);
    }

    @GetMapping("/member/page")
    @Operation(summary = "平台会员管理：按店铺查会员（手机/余额/积分/推荐人/进店时间）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> memberPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        PageResult<MemberShopRelDO> page = memberShopRelMapper.selectPage(pageParam,
                new LambdaQueryWrapperX<MemberShopRelDO>()
                        .eqIfPresent(MemberShopRelDO::getTenantId, tenantId)
                        .orderByDesc(MemberShopRelDO::getId));
        Map<Long, String> shopNames = loadShopNames();
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (MemberShopRelDO r : page.getList()) {
            if (r.getUserId() != null) {
                userIds.add(r.getUserId());
            }
        }
        Map<Long, MemberUserRespDTO> userMap = userIds.isEmpty()
                ? java.util.Collections.emptyMap() : memberUserApi.getUserMap(userIds);
        List<Map<String, Object>> list = new ArrayList<>();
        for (MemberShopRelDO r : page.getList()) {
            MemberUserRespDTO u = userMap.get(r.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", r.getUserId());
            m.put("mobile", u == null ? null : u.getMobile());
            m.put("nickname", u == null ? null : u.getNickname());
            m.put("tenantId", r.getTenantId());
            m.put("shopName", shopNames.getOrDefault(r.getTenantId(), "租户" + r.getTenantId()));
            m.put("balance", r.getBalance());
            m.put("points", r.getPoints());
            m.put("referrerUserId", r.getReferrerUserId());
            m.put("firstVisitAt", r.getFirstVisitAt());
            m.put("lastVisitAt", r.getLastVisitAt());
            list.add(m);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/shop/page")
    @Operation(summary = "店铺管理：所有店铺（名称/状态/联系/地址/通联费率/自动上架）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> shopPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "shopName", required = false) String shopName,
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        PageResult<ShopInfoDO> page = shopInfoMapper.selectPage(pageParam,
                new LambdaQueryWrapperX<ShopInfoDO>()
                        .likeIfPresent(ShopInfoDO::getShopName, shopName)
                        .eqIfPresent(ShopInfoDO::getTenantId, tenantId)
                        .orderByDesc(ShopInfoDO::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShopInfoDO s : page.getList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("tenantId", s.getTenantId());
            m.put("shopName", s.getShopName());
            m.put("status", s.getStatus());
            m.put("mobile", s.getMobile());
            m.put("address", s.getAddress());
            m.put("businessType", s.getBusinessType());
            m.put("tlMchId", s.getTlMchId());
            m.put("onlinePayEnabled", s.getOnlinePayEnabled());
            m.put("tlFeeRate", s.getTlFeeRate());
            m.put("autoApprove", s.getAutoApprove() == null ? 1 : s.getAutoApprove());
            m.put("createTime", s.getCreateTime());
            list.add(m);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @PutMapping("/shop/update-rate")
    @Operation(summary = "修改某店铺通联支付费率")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<Boolean> updateShopRate(@RequestParam("id") Long id,
                                                @RequestParam(value = "tlFeeRate", required = false) String tlFeeRate) {
        ShopInfoDO update = new ShopInfoDO();
        update.setId(id);
        update.setTlFeeRate(tlFeeRate);
        shopInfoMapper.updateById(update);
        return success(true);
    }

    @PutMapping("/shop/update-auto-approve")
    @Operation(summary = "设置某店铺商品是否免审核自动上架")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<Boolean> updateShopAutoApprove(@RequestParam("id") Long id,
                                                       @RequestParam("autoApprove") Integer autoApprove) {
        ShopInfoDO update = new ShopInfoDO();
        update.setId(id);
        update.setAutoApprove(autoApprove);
        shopInfoMapper.updateById(update);
        return success(true);
    }

    @GetMapping("/comment/page")
    @Operation(summary = "平台跨租户商品评价总览（全部店铺，可按店铺/评分筛选）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> commentPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "scores", required = false) Integer scores) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        LambdaQueryWrapperX<ProductCommentDO> q = new LambdaQueryWrapperX<ProductCommentDO>()
                .eqIfPresent(ProductCommentDO::getTenantId, tenantId)
                .eqIfPresent(ProductCommentDO::getScores, scores)
                .orderByDesc(ProductCommentDO::getId);
        PageResult<ProductCommentDO> page = productCommentMapper.selectPage(pageParam, q);
        Map<Long, String> shopNames = loadShopNames();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProductCommentDO c : page.getList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("tenantId", c.getTenantId());
            m.put("shopName", shopNames.getOrDefault(c.getTenantId(), "租户" + c.getTenantId()));
            m.put("spuName", c.getSpuName());
            m.put("skuPicUrl", c.getSkuPicUrl());
            m.put("userNickname", Boolean.TRUE.equals(c.getAnonymous()) ? ProductCommentDO.NICKNAME_ANONYMOUS : c.getUserNickname());
            m.put("userAvatar", c.getUserAvatar());
            m.put("scores", c.getScores());
            m.put("descriptionScores", c.getDescriptionScores());
            m.put("benefitScores", c.getBenefitScores());
            m.put("content", c.getContent());
            m.put("picUrls", c.getPicUrls());
            m.put("replyStatus", c.getReplyStatus());
            m.put("replyContent", c.getReplyContent());
            m.put("visible", c.getVisible());
            m.put("createTime", c.getCreateTime());
            list.add(m);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    private Map<Long, String> loadShopNames() {
        Map<Long, String> map = new HashMap<>();
        for (ShopInfoDO s : shopInfoMapper.selectList()) {
            map.put(s.getTenantId(), s.getShopName());
        }
        return map;
    }

}
