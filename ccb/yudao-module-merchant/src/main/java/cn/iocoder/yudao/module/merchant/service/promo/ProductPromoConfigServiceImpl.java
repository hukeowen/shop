package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
        validate(reqVO);
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

    /**
     * v8 校验：JSON 数组长度必须与 N / starCount 严格匹配，避免运行时 idx 越界靠 size-1 兜底导致语义错配。
     */
    private void validate(ProductPromoConfigSaveReqVO req) {
        // 推 N 反 1 启用时校验 ratios 长度 = N，且加总 ≤ 100
        if (Boolean.TRUE.equals(req.getTuijianEnabled())) {
            int n = req.getTuijianN() == null ? 0 : req.getTuijianN();
            if (n <= 0) {
                throw ServiceExceptionUtil.exception0(1_031_002_001, "启用推 N 反 1 时 N 必须 > 0");
            }
            List<BigDecimal> ratios = parseDecArray(req.getTuijianRatios(), "tuijianRatios");
            if (ratios.size() != n) {
                throw ServiceExceptionUtil.exception0(1_031_002_002,
                        "tuijianRatios 数组长度 (" + ratios.size() + ") 必须等于 N (" + n + ")");
            }
            BigDecimal sum = ratios.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(new BigDecimal("100")) > 0) {
                throw ServiceExceptionUtil.exception0(1_031_002_003,
                        "tuijianRatios 加总 = " + sum + " 不可 > 100");
            }
        }
        // 团队极差启用时校验 starRatios / starUpgradeRules 长度 = starCount
        Integer sc = req.getStarCount();
        if (sc != null && sc > 0) {
            List<BigDecimal> srs = parseDecArray(req.getStarRatios(), "starRatios");
            if (srs.size() != sc) {
                throw ServiceExceptionUtil.exception0(1_031_002_004,
                        "starRatios 数组长度 (" + srs.size() + ") 必须等于 starCount (" + sc + ")");
            }
            // starUpgradeRules：每条 {directCount, teamSales}；长度需 = starCount
            try {
                List<?> rules = JsonUtils.parseArray(req.getStarUpgradeRules(), Object.class);
                int len = rules == null ? 0 : rules.size();
                if (len != sc) {
                    throw ServiceExceptionUtil.exception0(1_031_002_005,
                            "starUpgradeRules 数组长度 (" + len + ") 必须等于 starCount (" + sc + ")");
                }
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) {
                throw ex;
            } catch (Exception e) {
                throw ServiceExceptionUtil.exception0(1_031_002_006,
                        "starUpgradeRules 不是合法 JSON 数组：" + e.getMessage());
            }
        }
    }

    private List<BigDecimal> parseDecArray(String json, String fieldName) {
        if (json == null || json.isEmpty()) {
            throw ServiceExceptionUtil.exception0(1_031_002_007, fieldName + " 不能为空");
        }
        try {
            List<Number> raw = JsonUtils.parseArray(json, Number.class);
            if (raw == null) {
                return java.util.Collections.emptyList();
            }
            return raw.stream()
                    .map(n -> n == null ? BigDecimal.ZERO : new BigDecimal(n.toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception0(1_031_002_008,
                    fieldName + " 不是合法 JSON 数字数组：" + e.getMessage());
        }
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
