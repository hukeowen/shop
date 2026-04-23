package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoPackagePageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoPackageSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoPackageDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.AiVideoPackageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PACKAGE_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PACKAGE_NOT_FOUND;

/**
 * AI 视频套餐 Service 实现类。
 */
@Service
@Validated
@Slf4j
public class AiVideoPackageServiceImpl implements AiVideoPackageService {

    @Resource
    private AiVideoPackageMapper packageMapper;

    @Override
    public Long createPackage(AiVideoPackageSaveReqVO reqVO) {
        AiVideoPackageDO pkg = BeanUtils.toBean(reqVO, AiVideoPackageDO.class);
        pkg.setId(null);
        if (pkg.getStatus() == null) {
            pkg.setStatus(0);
        }
        if (pkg.getSort() == null) {
            pkg.setSort(0);
        }
        packageMapper.insert(pkg);
        return pkg.getId();
    }

    @Override
    public void updatePackage(AiVideoPackageSaveReqVO reqVO) {
        if (packageMapper.selectById(reqVO.getId()) == null) {
            throw exception(PACKAGE_NOT_FOUND);
        }
        AiVideoPackageDO pkg = BeanUtils.toBean(reqVO, AiVideoPackageDO.class);
        packageMapper.updateById(pkg);
    }

    @Override
    public void deletePackage(Long id) {
        if (packageMapper.selectById(id) == null) {
            throw exception(PACKAGE_NOT_FOUND);
        }
        packageMapper.deleteById(id);
    }

    @Override
    public AiVideoPackageDO getPackage(Long id) {
        return packageMapper.selectById(id);
    }

    @Override
    public PageResult<AiVideoPackageDO> getPackagePage(AiVideoPackagePageReqVO reqVO) {
        return packageMapper.selectPage(reqVO);
    }

    @Override
    public List<AiVideoPackageDO> listEnabledPackages() {
        return packageMapper.selectEnabledList();
    }

    @Override
    public AiVideoPackageDO validatePackageAvailable(Long id) {
        AiVideoPackageDO pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw exception(PACKAGE_NOT_FOUND);
        }
        if (pkg.getStatus() == null || pkg.getStatus() != 0) {
            throw exception(PACKAGE_NOT_AVAILABLE);
        }
        return pkg;
    }

}
