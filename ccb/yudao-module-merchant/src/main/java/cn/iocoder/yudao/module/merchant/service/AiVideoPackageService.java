package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoPackagePageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoPackageSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoPackageDO;

import javax.validation.Valid;
import java.util.List;

/**
 * AI 视频套餐 Service（Phase 0.3.1）。平台级资源，不做租户过滤。
 */
public interface AiVideoPackageService {

    /** 创建套餐。 */
    Long createPackage(@Valid AiVideoPackageSaveReqVO reqVO);

    /** 更新套餐。 */
    void updatePackage(@Valid AiVideoPackageSaveReqVO reqVO);

    /** 逻辑删除套餐。 */
    void deletePackage(Long id);

    /** 获取详情。 */
    AiVideoPackageDO getPackage(Long id);

    /** Admin 分页。 */
    PageResult<AiVideoPackageDO> getPackagePage(AiVideoPackagePageReqVO reqVO);

    /** App 在架列表（status=0，sort DESC）。 */
    List<AiVideoPackageDO> listEnabledPackages();

    /**
     * 校验套餐存在且可用（status=0）；不可用抛
     * {@link cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants#PACKAGE_NOT_FOUND}
     * 或 {@link cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants#PACKAGE_NOT_AVAILABLE}。
     *
     * <p>支付下单前必须先调用它，拿到权威 video_count / price 再落订单，防止前端篡改。</p>
     */
    AiVideoPackageDO validatePackageAvailable(Long id);

}
