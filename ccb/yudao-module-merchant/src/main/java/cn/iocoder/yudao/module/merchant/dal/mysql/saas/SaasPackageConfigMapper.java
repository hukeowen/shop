package cn.iocoder.yudao.module.merchant.dal.mysql.saas;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.SaasPackageConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SaasPackageConfigMapper extends BaseMapperX<SaasPackageConfigDO> {

    /** 列上架套餐（C 端续费页用） */
    default List<SaasPackageConfigDO> selectListEnabledOrderBySort() {
        return selectList(new LambdaQueryWrapperX<SaasPackageConfigDO>()
                .eq(SaasPackageConfigDO::getStatus, 0)
                .orderByAsc(SaasPackageConfigDO::getSort));
    }

    default SaasPackageConfigDO selectByLevel(String level) {
        return selectOne(new LambdaQueryWrapperX<SaasPackageConfigDO>()
                .eq(SaasPackageConfigDO::getLevel, level));
    }

    /** V042：套餐作为商品 SPU 后，按 spu_id 反查套餐配置 */
    default SaasPackageConfigDO selectBySpuId(Long spuId) {
        return selectOne(new LambdaQueryWrapperX<SaasPackageConfigDO>()
                .eq(SaasPackageConfigDO::getSpuId, spuId));
    }
}
