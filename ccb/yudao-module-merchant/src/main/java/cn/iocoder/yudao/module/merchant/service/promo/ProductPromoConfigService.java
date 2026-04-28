package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.ProductPromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;

import java.util.List;
import java.util.Map;

/**
 * 商品营销配置 Service
 */
public interface ProductPromoConfigService {

    /**
     * 取某商品的配置；如不存在，返回内置默认值（全关；不入库）。
     */
    ProductPromoConfigDO getBySpuId(Long spuId);

    /**
     * upsert：(tenant_id, spu_id) 唯一键定位，已存在则更新，否则插入。
     */
    void save(ProductPromoConfigSaveReqVO reqVO);

    /**
     * 批量取（商品列表页使用），返回 Map&lt;spuId, DO&gt;，未配置的不在 map 里。
     */
    Map<Long, ProductPromoConfigDO> mapBySpuIds(List<Long> spuIds);

}
