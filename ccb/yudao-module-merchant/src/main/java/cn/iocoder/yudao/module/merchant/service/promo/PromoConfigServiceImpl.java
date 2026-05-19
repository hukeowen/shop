package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.PromoConfigMapper;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
                .consumePointRedeemEnabled(false)
                .consumePointRedeemRatio(BigDecimal.ONE)
                .build();
    }

    @Override
    public void saveConfig(PromoConfigSaveReqVO reqVO) {
        log.info("[saveConfig] reqVO directCommissionRatio={} naturalPushEnabled={}",
                reqVO.getDirectCommissionRatio(), reqVO.getNaturalPushEnabled());
        PromoConfigDO existing = promoConfigMapper.selectCurrent();
        if (existing == null) {
            PromoConfigDO insert = new PromoConfigDO();
            BeanUtils.copyProperties(reqVO, insert);
            // 显式 set v7 字段（Spring BeanUtils 在某些版本对包装类有问题，主动兜底）
            insert.setDirectCommissionRatio(reqVO.getDirectCommissionRatio());
            insert.setNaturalPushEnabled(reqVO.getNaturalPushEnabled());
            insert.setConsumePointRedeemEnabled(reqVO.getConsumePointRedeemEnabled());
            insert.setConsumePointRedeemRatio(reqVO.getConsumePointRedeemRatio());
            promoConfigMapper.insert(insert);
            return;
        }
        cn.hutool.core.bean.BeanUtil.copyProperties(reqVO, existing,
                cn.hutool.core.bean.copier.CopyOptions.create().ignoreNullValue());
        // 显式 set v7 字段，绕过 hutool/Spring BeanUtils 对 Boolean / BigDecimal 包装类
        // 在某些 JDK / 反射场景下不写入的 corner case
        if (reqVO.getDirectCommissionRatio() != null) {
            existing.setDirectCommissionRatio(reqVO.getDirectCommissionRatio());
        }
        if (reqVO.getNaturalPushEnabled() != null) {
            existing.setNaturalPushEnabled(reqVO.getNaturalPushEnabled());
        }
        if (reqVO.getConsumePointRedeemEnabled() != null) {
            existing.setConsumePointRedeemEnabled(reqVO.getConsumePointRedeemEnabled());
        }
        if (reqVO.getConsumePointRedeemRatio() != null) {
            existing.setConsumePointRedeemRatio(reqVO.getConsumePointRedeemRatio());
        }
        log.info("[saveConfig] after copy: directCommissionRatio={} naturalPushEnabled={}",
                existing.getDirectCommissionRatio(), existing.getNaturalPushEnabled());
        promoConfigMapper.updateById(existing);
    }

}
