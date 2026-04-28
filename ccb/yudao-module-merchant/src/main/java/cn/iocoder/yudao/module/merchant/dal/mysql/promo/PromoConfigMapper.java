package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户营销配置 Mapper
 *
 * 因 TenantBaseDO 自动按 tenant_id 过滤，selectOne 即返当前租户的配置。
 */
@Mapper
public interface PromoConfigMapper extends BaseMapperX<PromoConfigDO> {

    /** 取当前租户的配置（无则返 null，由 Service 层兜底默认） */
    default PromoConfigDO selectCurrent() {
        return selectOne(Wrappers.<PromoConfigDO>lambdaQuery());
    }

}
