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
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.PromoConfigMapper shopPromoConfigMapper;

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
            // V044 合规整改 P0-3：sum + 商户级 directCommissionRatio ≤ 100
            // 防止单笔订单返奖总额超过商品单价 → 资金跨订单庞氏
            try {
                cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO shopCfg =
                        cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(
                                () -> shopPromoConfigMapper.selectCurrent());
                if (shopCfg != null && shopCfg.getDirectCommissionRatio() != null) {
                    BigDecimal direct = shopCfg.getDirectCommissionRatio();
                    BigDecimal total = sum.add(direct);
                    if (total.compareTo(new BigDecimal("100")) > 0) {
                        throw ServiceExceptionUtil.exception0(1_031_002_011,
                                "推 N 反 1 累积返奖 (" + sum + "%) + 邀请奖比例 (" + direct + "%) "
                                        + "= " + total + "% 超过 100%，单笔订单返奖将超过商品单价，"
                                        + "违反合规硬约束 P0-3。请商户调整 邀请奖比例 或 推 N 反 1 配置。");
                    }
                }
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) {
                throw ex;
            } catch (Exception ignore) {
                // 跨租户拉商户配置失败不致命，仅放过本次校验
            }
        }
        // V044 合规：starRatios 重定义为"邀请奖按推荐人 VIP 等级差异化比例"
        // starUpgradeRules 改为纯个人 KPI（直推付费数 OR 自购金额任一即升）
        Integer sc = req.getStarCount();
        if (sc != null && sc > 0) {
            List<BigDecimal> srs = parseDecArray(req.getStarRatios(), "starRatios");
            if (srs.size() != sc) {
                throw ServiceExceptionUtil.exception0(1_031_002_004,
                        "starRatios 数组长度 (" + srs.size() + ") 必须等于 starCount (" + sc + ")");
            }
            // V044 合规 P0-4 硬约束：每个 VIP 等级邀请奖比例 ≤ 35%
            BigDecimal cap = new BigDecimal("35");
            for (int i = 0; i < srs.size(); i++) {
                if (srs.get(i).compareTo(cap) > 0) {
                    throw ServiceExceptionUtil.exception0(1_031_002_012,
                            "starRatios[" + i + "] = " + srs.get(i)
                                    + "% 超过 35% 合规上限。请调整后保存。");
                }
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

        // v8 奖池分配规则：可空（不配 = 没启用结算），非空时强校验
        String distJson = req.getPoolDistRules();
        if (distJson != null && !distJson.trim().isEmpty() && !"[]".equals(distJson.trim())) {
            List<Map<String, Object>> rules;
            try {
                rules = JsonUtils.parseArray(distJson, (Class) Map.class);
            } catch (Exception e) {
                throw ServiceExceptionUtil.exception0(1_031_002_009,
                        "poolDistRules 不是合法 JSON 数组：" + e.getMessage());
            }
            if (rules == null || rules.isEmpty()) {
                throw ServiceExceptionUtil.exception0(1_031_002_010, "poolDistRules 至少配 1 条");
            }
            int starCount = sc == null ? 0 : sc;
            BigDecimal ratioSum = BigDecimal.ZERO;
            java.util.Set<Integer> seenStars = new java.util.HashSet<>();
            for (int i = 0; i < rules.size(); i++) {
                Map<String, Object> r = rules.get(i);
                Object starObj = r.get("star");
                Object ratioObj = r.get("ratio");
                Object modeObj = r.get("mode");
                if (!(starObj instanceof Number)) {
                    throw ServiceExceptionUtil.exception0(1_031_002_011,
                            "poolDistRules[" + i + "].star 缺失或非数字");
                }
                int star = ((Number) starObj).intValue();
                if (star <= 0 || (starCount > 0 && star > starCount)) {
                    throw ServiceExceptionUtil.exception0(1_031_002_012,
                            "poolDistRules[" + i + "].star=" + star + " 必须 ∈ [1," + starCount + "]");
                }
                if (!seenStars.add(star)) {
                    throw ServiceExceptionUtil.exception0(1_031_002_013,
                            "poolDistRules 星级 " + star + " 重复");
                }
                if (!(ratioObj instanceof Number)) {
                    throw ServiceExceptionUtil.exception0(1_031_002_014,
                            "poolDistRules[" + i + "].ratio 缺失或非数字");
                }
                BigDecimal ratio = new BigDecimal(ratioObj.toString());
                if (ratio.compareTo(BigDecimal.ZERO) <= 0 || ratio.compareTo(new BigDecimal("100")) > 0) {
                    throw ServiceExceptionUtil.exception0(1_031_002_015,
                            "poolDistRules[" + i + "].ratio=" + ratio + " 必须 ∈ (0,100]");
                }
                ratioSum = ratioSum.add(ratio);
                String mode = modeObj == null ? "" : modeObj.toString();
                if (!"EQUAL".equals(mode) && !"LOTTERY".equals(mode)) {
                    throw ServiceExceptionUtil.exception0(1_031_002_016,
                            "poolDistRules[" + i + "].mode 必须是 EQUAL 或 LOTTERY，当前=" + mode);
                }
                if ("LOTTERY".equals(mode)) {
                    Object winnersObj = r.get("winners");
                    if (!(winnersObj instanceof Number) || ((Number) winnersObj).intValue() < 1) {
                        throw ServiceExceptionUtil.exception0(1_031_002_017,
                                "poolDistRules[" + i + "] LOTTERY 模式 winners 必须 ≥ 1");
                    }
                }
            }
            if (ratioSum.compareTo(new BigDecimal("100")) != 0) {
                throw ServiceExceptionUtil.exception0(1_031_002_018,
                        "poolDistRules ratio 加总 = " + ratioSum + " 必须严格等于 100");
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
