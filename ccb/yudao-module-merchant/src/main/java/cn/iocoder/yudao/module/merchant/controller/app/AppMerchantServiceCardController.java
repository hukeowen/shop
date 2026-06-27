package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppServiceCardDefSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.card.ServiceCardDefDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.service.card.ServiceCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 服务卡包 / 核销。
 *
 * <ul>
 *   <li>商家：defs/save、defs（建商品时配卡 / 编辑回显）；verify-info、redeem、verify-records（核销）</li>
 *   <li>用户：my（我的卡包）、get（单卡详情/出示码）</li>
 * </ul>
 *
 * <p>商户操作需 user 在 merchant_info 登记（requireMerchantTenantId）；用户操作仅需 member 登录。
 * 统一 @TenantIgnore：member token tenant=0 ≠ 卡所属 tenant，靠显式 tenantId 过滤。</p>
 */
@Tag(name = "商户小程序 - 服务卡包/核销")
@RestController
@RequestMapping("/merchant/mini/card")
@Validated
public class AppMerchantServiceCardController {

    @Resource
    private ServiceCardService serviceCardService;
    @Resource
    private MerchantMapper merchantInfoMapper;

    // ==================== 商家：商品卡定义 ====================

    @PostMapping("/defs/save")
    @Operation(summary = "保存商品的服务卡定义（全量覆盖）")
    @Parameter(name = "spuId", description = "商品 SPU ID", required = true)
    @TenantIgnore
    public CommonResult<Boolean> saveDefs(@RequestParam("spuId") @NotNull Long spuId,
                                          @RequestBody(required = false) List<AppServiceCardDefSaveReqVO> defs) {
        Long tenantId = requireMerchantTenantId();
        List<ServiceCardDefDO> rows = new ArrayList<>();
        if (defs != null) {
            for (AppServiceCardDefSaveReqVO d : defs) {
                rows.add(ServiceCardDefDO.builder()
                        .itemSpuId(d.getItemSpuId())
                        .name(d.getName())
                        .validityDays(d.getValidityDays())
                        .maxCount(d.getMaxCount())
                        .description(d.getDescription())
                        .build());
            }
        }
        serviceCardService.saveDefs(tenantId, spuId, rows);
        return success(true);
    }

    @GetMapping("/defs")
    @Operation(summary = "查商品的服务卡定义（编辑回显）")
    @Parameter(name = "spuId", description = "商品 SPU ID", required = true)
    @TenantIgnore
    public CommonResult<List<ServiceCardDefDO>> listDefs(@RequestParam("spuId") @NotNull Long spuId) {
        Long tenantId = requireMerchantTenantId();
        return success(serviceCardService.listDefs(tenantId, spuId));
    }

    @GetMapping("/defs-public")
    @Operation(summary = "查商品的服务卡定义（C 端商品详情展示用，公开）")
    @Parameter(name = "spuId", description = "商品 SPU ID", required = true)
    @Parameter(name = "tenantId", description = "店铺租户 ID", required = true)
    @TenantIgnore
    public CommonResult<List<ServiceCardDefDO>> listDefsPublic(@RequestParam("spuId") @NotNull Long spuId,
                                                               @RequestParam("tenantId") @NotNull Long tenantId) {
        return success(serviceCardService.listDefs(tenantId, spuId));
    }

    // ==================== 用户：我的卡包 ====================

    @GetMapping("/my")
    @Operation(summary = "我的卡包（跨店）")
    @TenantIgnore
    public CommonResult<List<Map<String, Object>>> myCards() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(serviceCardService.listMyCards(userId));
    }

    @GetMapping("/get")
    @Operation(summary = "我的单张卡详情（出示码页）")
    @Parameter(name = "id", description = "卡实例 ID", required = true)
    @TenantIgnore
    public CommonResult<Map<String, Object>> getCard(@RequestParam("id") @NotNull Long id) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(serviceCardService.getMyCard(userId, id));
    }

    // ==================== 商家：核销 ====================

    @GetMapping("/verify-info")
    @Operation(summary = "扫码/输码后查卡信息（不改数据）")
    @Parameter(name = "cardNo", description = "核销码", required = true)
    @TenantIgnore
    public CommonResult<Map<String, Object>> verifyInfo(@RequestParam("cardNo") String cardNo) {
        Long tenantId = requireMerchantTenantId();
        return success(serviceCardService.verifyInfo(tenantId, cardNo));
    }

    @PostMapping("/redeem")
    @Operation(summary = "核销一次")
    @Parameter(name = "cardNo", description = "核销码", required = true)
    @TenantIgnore
    public CommonResult<Map<String, Object>> redeem(@RequestParam("cardNo") String cardNo,
                                                    @RequestParam(value = "remark", required = false) String remark) {
        Long tenantId = requireMerchantTenantId();
        Long verifierId = SecurityFrameworkUtils.getLoginUserId();
        return success(serviceCardService.redeem(tenantId, cardNo, verifierId, remark));
    }

    @GetMapping("/verify-records")
    @Operation(summary = "本店核销记录分页")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> verifyRecords(PageParam pageParam) {
        Long tenantId = requireMerchantTenantId();
        return success(serviceCardService.listVerifyRecords(tenantId, pageParam));
    }

    // ==================== 工具 ====================

    private Long requireMerchantTenantId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw ServiceExceptionUtil.exception0(401, "请先登录");
        }
        MerchantDO merchant = merchantInfoMapper.selectByUserId(userId);
        if (merchant == null || merchant.getTenantId() == null || merchant.getTenantId() <= 0) {
            throw ServiceExceptionUtil.exception0(403, "无商户核销权限");
        }
        return merchant.getTenantId();
    }

}
