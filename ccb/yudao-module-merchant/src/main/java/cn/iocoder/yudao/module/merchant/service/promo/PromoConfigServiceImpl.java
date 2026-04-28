package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.PromoConfigMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 商户营销配置 Service 实现
 */
@Service
@Validated
public class PromoConfigServiceImpl implements PromoConfigService {

    /** 默认极差比例：1星=1%, 2星=2%, ..., 5星=5% */
    private static final String DEFAULT_COMMISSION_RATES = "[1,2,3,4,5]";

    /** 默认升星门槛：[{2,3},{3,9},{5,27},{8,81},{12,243}] */
    private static final String DEFAULT_STAR_UPGRADE_RULES =
            "[{\"directCount\":2,\"teamSales\":3},"
                    + "{\"directCount\":3,\"teamSales\":9},"
                    + "{\"directCount\":5,\"teamSales\":27},"
                    + "{\"directCount\":8,\"teamSales\":81},"
                    + "{\"directCount\":12,\"teamSales\":243}]";

    @Resource
    private PromoConfigMapper promoConfigMapper;

    @Override
    public PromoConfigDO getConfig() {
        PromoConfigDO existing = promoConfigMapper.selectCurrent();
        if (existing != null) {
            return existing;
        }
        // 内置默认值（不入库；商户首次保存时再持久化）
        return PromoConfigDO.builder()
                .starLevelCount(5)
                .commissionRates(DEFAULT_COMMISSION_RATES)
                .starUpgradeRules(DEFAULT_STAR_UPGRADE_RULES)
                .pointConversionRatio(BigDecimal.ONE)
                .withdrawThreshold(10000)
                .poolEnabled(false)
                .poolRatio(BigDecimal.ZERO)
                .poolEligibleStars("[]")
                .poolDistributeMode("ALL")
                .poolSettleCron("0 0 0 1 * ?")
                .poolLotteryRatio(new BigDecimal("5.00"))
                .poolSettleMode("FULL")
                .build();
    }

    @Override
    public void saveConfig(PromoConfigSaveReqVO reqVO) {
        PromoConfigDO existing = promoConfigMapper.selectCurrent();
        if (existing == null) {
            PromoConfigDO insert = new PromoConfigDO();
            BeanUtils.copyProperties(reqVO, insert);
            promoConfigMapper.insert(insert);
            return;
        }
        BeanUtils.copyProperties(reqVO, existing);
        promoConfigMapper.updateById(existing);
    }

}
