package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoPackagePageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoPackageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 视频套餐 Mapper。平台级表，不做租户过滤。
 */
@Mapper
public interface AiVideoPackageMapper extends BaseMapperX<AiVideoPackageDO> {

    /**
     * Admin 分页：支持名称模糊 + 状态筛选。
     */
    default PageResult<AiVideoPackageDO> selectPage(AiVideoPackagePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AiVideoPackageDO>()
                .likeIfPresent(AiVideoPackageDO::getName, reqVO.getName())
                .eqIfPresent(AiVideoPackageDO::getStatus, reqVO.getStatus())
                .orderByDesc(AiVideoPackageDO::getSort)
                .orderByDesc(AiVideoPackageDO::getId));
    }

    /**
     * App 用：返回所有在架套餐（status=0），按 sort DESC 排。
     */
    default List<AiVideoPackageDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<AiVideoPackageDO>()
                .eq(AiVideoPackageDO::getStatus, 0)
                .orderByDesc(AiVideoPackageDO::getSort)
                .orderByDesc(AiVideoPackageDO::getId));
    }

}
