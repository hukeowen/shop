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
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.MerchantApplyMapper merchantApplyMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.service.KycSignService kycSignService;

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
        // 店铺管理员（商户登录账号）：shop.tenantId → merchant_info.userId → member_user(手机号即登录账号)。
        // 注意商户账号不在 system_users（那是后台管理员），而在 member_user，且 tenant_id=0，靠 @TenantIgnore 才查得到。
        java.util.Set<Long> tenantIds = new java.util.HashSet<>();
        for (ShopInfoDO s : page.getList()) {
            if (s.getTenantId() != null) {
                tenantIds.add(s.getTenantId());
            }
        }
        Map<Long, MerchantDO> merchantByTenant = new HashMap<>();
        if (!tenantIds.isEmpty()) {
            for (MerchantDO mc : merchantMapper.selectList(
                    new LambdaQueryWrapperX<MerchantDO>().in(MerchantDO::getTenantId, tenantIds))) {
                merchantByTenant.put(mc.getTenantId(), mc);
            }
        }
        java.util.Set<Long> adminUserIds = new java.util.HashSet<>();
        for (MerchantDO mc : merchantByTenant.values()) {
            if (mc.getUserId() != null) {
                adminUserIds.add(mc.getUserId());
            }
        }
        Map<Long, MemberUserRespDTO> adminUserMap = adminUserIds.isEmpty()
                ? java.util.Collections.emptyMap() : memberUserApi.getUserMap(adminUserIds);
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
            // 管理员账号（商户登录手机号）
            MerchantDO mc = merchantByTenant.get(s.getTenantId());
            MemberUserRespDTO au = mc != null && mc.getUserId() != null ? adminUserMap.get(mc.getUserId()) : null;
            m.put("adminUserId", mc == null ? null : mc.getUserId());
            m.put("adminMobile", au != null ? au.getMobile() : (mc == null ? null : mc.getContactPhone()));
            m.put("adminName", au != null && au.getNickname() != null && !au.getNickname().isEmpty()
                    ? au.getNickname() : (mc == null ? null : mc.getContactName()));
            // 是否有可查看的入驻/进件资料（前端据此决定「查看资料」按钮是否可点）
            m.put("hasKyc", hasKyc(s));
            list.add(m);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    /** 该店是否上传过任何进件/入驻资料图片 */
    private static boolean hasKyc(ShopInfoDO s) {
        return notBlank(s.getBusinessLicenseKey()) || notBlank(s.getIdCardFrontKey())
                || notBlank(s.getIdCardBackKey()) || notBlank(s.getStorePicKey())
                || notBlank(s.getIndoorPicKey()) || notBlank(s.getMerchantFullName())
                || notBlank(s.getLegalName()) || notBlank(s.getCreditCode());
    }

    private static boolean notBlank(String v) {
        return v != null && !v.trim().isEmpty();
    }

    @PutMapping("/shop/update-status")
    @Operation(summary = "店铺上架/下架（1=上架展示 0=下架隐藏，下架后用户端 ke 首页/搜索/分类均不展示）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<Boolean> updateShopStatus(@RequestParam("id") Long id,
                                                  @RequestParam("status") Integer status) {
        // ⚠ shop_info.status 语义与直觉相反：1=展示上架，0=隐藏下架。
        // 用户端 AppShopPublicController.listShops 硬过滤 .eq(status, 1)，故下架必须写 0。
        if (status == null || (status != 0 && status != 1)) {
            return CommonResult.error(400, "status 只能是 1(上架) 或 0(下架)");
        }
        ShopInfoDO update = new ShopInfoDO();
        update.setId(id);
        update.setStatus(status);
        shopInfoMapper.updateById(update);
        return success(true);
    }

    @GetMapping("/shop/kyc")
    @Operation(summary = "查看店铺入驻/进件资料（文字资料 + 证件图片临时 URL，1 小时过期）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<Map<String, Object>> shopKyc(@RequestParam("id") Long id) {
        ShopInfoDO s = shopInfoMapper.selectById(id);
        if (s == null) {
            return CommonResult.error(400, "店铺不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shopName", s.getShopName());
        m.put("tenantId", s.getTenantId());
        // ===== 主体 / 法人 =====
        m.put("merchantFullName", s.getMerchantFullName());
        m.put("legalName", s.getLegalName());
        m.put("legalIdNo", mask(s.getLegalIdNo()));
        m.put("creditCode", s.getCreditCode());
        m.put("creditCodeExpire", s.getCreditCodeExpire());
        m.put("busAddress", s.getBusAddress());
        // ===== 联系人 =====
        m.put("contactPerson", s.getContactPerson());
        m.put("contactPhone", s.getContactPhone());
        m.put("contactEmail", s.getContactEmail());
        m.put("servicePhone", s.getServicePhone());
        // ===== 结算 =====
        m.put("settleAcctName", s.getSettleAcctName());
        m.put("settleAcctNo", mask(s.getSettleAcctNo()));
        m.put("settleBankName", s.getSettleBankName());
        // ===== 进件状态 =====
        m.put("payApplyStatus", s.getPayApplyStatus());
        m.put("payApplyRejectReason", s.getPayApplyRejectReason());
        m.put("tlMchId", s.getTlMchId());
        // ===== 证件图片：现签 1h 临时 URL（私有 TOS，签发失败降级为 null，不影响其它字段）=====
        Map<String, String> pics = new LinkedHashMap<>();
        putSigned(pics, "businessLicense", s.getBusinessLicenseKey());
        putSigned(pics, "idCardFront", s.getIdCardFrontKey());
        putSigned(pics, "idCardBack", s.getIdCardBackKey());
        putSigned(pics, "storePic", s.getStorePicKey());
        putSigned(pics, "indoorPic", s.getIndoorPicKey());
        m.put("pics", pics);
        // ===== 兼容：若有入驻申请记录（merchant_apply，图片为公开 URL）一并返回 =====
        List<cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO> applies =
                s.getTenantId() == null ? java.util.Collections.emptyList() : merchantApplyMapper.selectList(
                        new LambdaQueryWrapperX<cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO>()
                                .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO::getTenantId,
                                        s.getTenantId())
                                .orderByDesc(cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO::getId)
                                .last("LIMIT 1"));
        if (applies != null && !applies.isEmpty()) {
            cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO a = applies.get(0);
            Map<String, Object> ap = new LinkedHashMap<>();
            ap.put("shopName", a.getShopName());
            ap.put("mobile", a.getMobile());
            ap.put("address", a.getAddress());
            ap.put("status", a.getStatus());
            ap.put("rejectReason", a.getRejectReason());
            ap.put("auditTime", a.getAuditTime());
            ap.put("createTime", a.getCreateTime());
            ap.put("licenseUrl", a.getLicenseUrl());
            ap.put("idCardFront", a.getIdCardFront());
            ap.put("idCardBack", a.getIdCardBack());
            m.put("apply", ap);
        }
        return success(m);
    }

    /** 私有证件 key → 1h 临时 URL；签发失败不抛错（sidecar 不可用时前端显示「暂不可预览」） */
    private void putSigned(Map<String, String> target, String name, String key) {
        if (!notBlank(key)) {
            return;
        }
        try {
            target.put(name, kycSignService.sign(key, 3600));
        } catch (Exception e) {
            // 不把异常抛给前端：一张图签不出来不该让整个资料弹窗打不开
            target.put(name, null);
        }
    }

    /** 身份证/银行卡等敏感号码脱敏：保留前 4 后 4 */
    private static String mask(String v) {
        if (!notBlank(v)) {
            return null;
        }
        String t = v.trim();
        if (t.length() <= 8) {
            return t.charAt(0) + "****";
        }
        return t.substring(0, 4) + "****" + t.substring(t.length() - 4);
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
