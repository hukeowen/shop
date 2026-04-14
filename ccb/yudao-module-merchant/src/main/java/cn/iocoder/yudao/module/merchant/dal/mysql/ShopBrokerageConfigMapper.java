package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopBrokerageConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopBrokerageConfigMapper extends BaseMapperX<ShopBrokerageConfigDO> {

    /** 查询当前租户的配置（TenantBaseDO 自动过滤 tenant_id） */
    default ShopBrokerageConfigDO selectCurrent() {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopBrokerageConfigDO>()
                .last("LIMIT 1"));
    }

}
