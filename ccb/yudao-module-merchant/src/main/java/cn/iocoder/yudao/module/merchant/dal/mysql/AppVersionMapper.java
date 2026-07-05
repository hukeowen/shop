package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion.AppVersionPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AppVersionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppVersionMapper extends BaseMapperX<AppVersionDO> {

    /** 某平台当前「已发布」的最新版本（versionCode 最大的启用记录） */
    default AppVersionDO selectLatestPublished(String platform) {
        return selectOne(new LambdaQueryWrapperX<AppVersionDO>()
                .eq(AppVersionDO::getPlatform, platform)
                .eq(AppVersionDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByDesc(AppVersionDO::getVersionCode)
                .last("LIMIT 1"));
    }

    default PageResult<AppVersionDO> selectPage(AppVersionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AppVersionDO>()
                .eqIfPresent(AppVersionDO::getPlatform, reqVO.getPlatform())
                .eqIfPresent(AppVersionDO::getStatus, reqVO.getStatus())
                .likeIfPresent(AppVersionDO::getVersionName, reqVO.getVersionName())
                .orderByDesc(AppVersionDO::getVersionCode));
    }

}
