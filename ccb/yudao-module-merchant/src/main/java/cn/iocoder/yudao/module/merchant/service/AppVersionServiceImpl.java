package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion.AppVersionPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion.AppVersionSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AppVersionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.AppVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;

/**
 * App 版本 Service 实现
 */
@Service
@Validated
public class AppVersionServiceImpl implements AppVersionService {

    @Resource
    private AppVersionMapper appVersionMapper;

    @Override
    public Long createAppVersion(AppVersionSaveReqVO reqVO) {
        AppVersionDO version = BeanUtils.toBean(reqVO, AppVersionDO.class);
        normalize(version);
        appVersionMapper.insert(version);
        return version.getId();
    }

    @Override
    public void updateAppVersion(AppVersionSaveReqVO reqVO) {
        if (appVersionMapper.selectById(reqVO.getId()) == null) {
            throw exception(NOT_FOUND, "App 版本不存在");
        }
        AppVersionDO version = BeanUtils.toBean(reqVO, AppVersionDO.class);
        normalize(version);
        appVersionMapper.updateById(version);
    }

    private void normalize(AppVersionDO version) {
        if (StringUtils.hasText(version.getPlatform())) {
            version.setPlatform(version.getPlatform().trim().toLowerCase());
        }
        if (version.getStatus() == null) {
            version.setStatus(CommonStatusEnum.ENABLE.getStatus());
        }
        if (version.getForceUpdate() == null) {
            version.setForceUpdate(false);
        }
    }

    @Override
    public void deleteAppVersion(Long id) {
        appVersionMapper.deleteById(id);
    }

    @Override
    public AppVersionDO getAppVersion(Long id) {
        return appVersionMapper.selectById(id);
    }

    @Override
    public PageResult<AppVersionDO> getAppVersionPage(AppVersionPageReqVO reqVO) {
        return appVersionMapper.selectPage(reqVO);
    }

    @Override
    public AppVersionDO getLatestPublished(String platform) {
        String p = StringUtils.hasText(platform) ? platform.trim().toLowerCase() : "android";
        return appVersionMapper.selectLatestPublished(p);
    }

}
