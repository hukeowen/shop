package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantVideoQuotaLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
@Validated
@Slf4j
public class MerchantVideoQuotaLogServiceImpl implements MerchantVideoQuotaLogService {

    @Resource
    private MerchantVideoQuotaLogMapper logMapper;

    /**
     * 平台级表：tenant_id 靠 DDL default 0，不写 DO 字段。
     * 依赖 {@code uk_biz(biz_type, biz_id)} 的 UNIQUE 约束兜底幂等——支付回调重试/BFF 重放时
     * 第二次 insert 会触发 {@link DuplicateKeyException}，被捕后返回 false，由调用方决定回滚上层事务。
     */
    @Override
    public boolean insertLog(MerchantVideoQuotaLogDO logDO) {
        try {
            logMapper.insert(logDO);
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("[quota-log] 重复流水忽略 bizType={} bizId={}", logDO.getBizType(), logDO.getBizId());
            return false;
        }
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

    @Override
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore  // @Scheduled 跑无 tenant 上下文
    public List<MerchantVideoQuotaLogDO> findOrphanDebits(Date start, Date end) {
        return logMapper.findOrphanDebits(start, end);
    }

    @Override
    public int appendTaskIdToRemark(Long merchantId, String bizId, String taskId) {
        return logMapper.appendTaskIdToRemark(merchantId, bizId, taskId);
    }

}
