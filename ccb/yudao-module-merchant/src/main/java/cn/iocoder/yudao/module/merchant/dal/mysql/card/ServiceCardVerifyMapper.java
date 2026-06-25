package cn.iocoder.yudao.module.merchant.dal.mysql.card;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.card.ServiceCardVerifyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ServiceCardVerifyMapper extends BaseMapperX<ServiceCardVerifyDO> {

    /** 某卡的核销流水（倒序） */
    default List<ServiceCardVerifyDO> selectListByCardId(Long cardId) {
        return selectList(new LambdaQueryWrapperX<ServiceCardVerifyDO>()
                .eq(ServiceCardVerifyDO::getCardId, cardId)
                .orderByDesc(ServiceCardVerifyDO::getId));
    }

    /** 商户核销记录分页（当前租户，倒序）；调用方 @TenantIgnore + 显式传 tenantId */
    default PageResult<ServiceCardVerifyDO> selectPageByTenant(Long tenantId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ServiceCardVerifyDO>()
                .eq(ServiceCardVerifyDO::getTenantId, tenantId)
                .orderByDesc(ServiceCardVerifyDO::getId));
    }

}
