package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;

/**
 * 商户营销配置 Service
 */
public interface PromoConfigService {

    /**
     * 取当前租户的营销配置；如不存在，返回内置默认值（不入库）。
     */
    PromoConfigDO getConfig();

    /**
     * upsert：当前租户已有配置则更新，否则插入。
     */
    void saveConfig(PromoConfigSaveReqVO reqVO);

}
