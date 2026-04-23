package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantVideoQuotaLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

@Service
@Validated
@Slf4j
public class MerchantVideoQuotaLogServiceImpl implements MerchantVideoQuotaLogService {

    @Resource
    private MerchantVideoQuotaLogMapper logMapper;

    @Override
    public void insertLog(MerchantVideoQuotaLogDO log) {
        logMapper.insert(log);
    }

    @Override
    public PageResult<MerchantVideoQuotaLogDO> getLogPage(AiVideoQuotaLogPageReqVO reqVO) {
        return logMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<MerchantVideoQuotaLogDO> getLogPageByMerchant(Long merchantId, Integer bizType,
                                                                    AiVideoQuotaLogPageReqVO pageParam) {
        return logMapper.selectPageByMerchant(merchantId, bizType, pageParam);
    }

}
