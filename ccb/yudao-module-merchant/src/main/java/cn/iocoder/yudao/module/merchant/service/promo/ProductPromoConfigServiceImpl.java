package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.ProductPromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ProductPromoConfigMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
public class ProductPromoConfigServiceImpl implements ProductPromoConfigService {

    @Resource
    private ProductPromoConfigMapper mapper;

    @Override
    public ProductPromoConfigDO getBySpuId(Long spuId) {
        ProductPromoConfigDO existing = mapper.selectBySpuId(spuId);
        if (existing != null) {
            return existing;
        }
        // 默认值（全关；不入库）
        return ProductPromoConfigDO.builder()
                .spuId(spuId)
                .consumePointRatio(BigDecimal.ZERO)
                .tuijianEnabled(false)
                .tuijianN(0)
                .tuijianRatios("[]")
                .poolEnabled(false)
                .build();
    }

    @Override
    public void save(ProductPromoConfigSaveReqVO reqVO) {
        ProductPromoConfigDO existing = mapper.selectBySpuId(reqVO.getSpuId());
        if (existing == null) {
            ProductPromoConfigDO insert = new ProductPromoConfigDO();
            BeanUtils.copyProperties(reqVO, insert);
            mapper.insert(insert);
            return;
        }
        BeanUtils.copyProperties(reqVO, existing);
        mapper.updateById(existing);
    }

    @Override
    public Map<Long, ProductPromoConfigDO> mapBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ProductPromoConfigDO> list = mapper.selectListBySpuIds(spuIds);
        return list.stream().collect(Collectors.toMap(ProductPromoConfigDO::getSpuId, Function.identity()));
    }

}
