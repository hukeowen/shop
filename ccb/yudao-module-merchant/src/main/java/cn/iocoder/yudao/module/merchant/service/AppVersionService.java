package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion.AppVersionPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion.AppVersionSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AppVersionDO;

/**
 * App 版本 Service（App 自动升级）
 */
public interface AppVersionService {

    Long createAppVersion(AppVersionSaveReqVO reqVO);

    void updateAppVersion(AppVersionSaveReqVO reqVO);

    void deleteAppVersion(Long id);

    AppVersionDO getAppVersion(Long id);

    PageResult<AppVersionDO> getAppVersionPage(AppVersionPageReqVO reqVO);

    /** 某平台当前已发布的最新版本（App 端启动时比对用），无则返回 null */
    AppVersionDO getLatestPublished(String platform);

}
