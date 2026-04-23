package cn.iocoder.yudao.module.merchant.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantInviteCodeDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantInviteCodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.INVITE_CODE_DISABLED;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.INVITE_CODE_EXHAUSTED;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.INVITE_CODE_NOT_FOUND;

/**
 * BD 邀请码 Service 实现
 */
@Service
@Slf4j
public class MerchantInviteCodeServiceImpl implements MerchantInviteCodeService {

    private static final String CODE_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"; // 去掉易混淆 0/O/1/I/l
    private static final int CODE_LEN = 8;
    private static final int MAX_CREATE_RETRY = 10;

    @Resource
    private MerchantInviteCodeMapper inviteCodeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantInviteCodeDO validateAndConsume(String code) {
        if (StrUtil.isBlank(code)) {
            throw exception(INVITE_CODE_NOT_FOUND);
        }
        MerchantInviteCodeDO inviteCode = inviteCodeMapper.selectByCode(code.trim());
        if (inviteCode == null) {
            throw exception(INVITE_CODE_NOT_FOUND);
        }
        if (Boolean.FALSE.equals(inviteCode.getEnabled())) {
            throw exception(INVITE_CODE_DISABLED);
        }
        // 原子自增；失败说明被其他并发事务用完/禁用
        int affected = inviteCodeMapper.incrementUsedCount(inviteCode.getId());
        if (affected == 0) {
            // 重新查询一次，区分 disabled 与 exhausted 的错误提示
            MerchantInviteCodeDO refreshed = inviteCodeMapper.selectById(inviteCode.getId());
            if (refreshed != null && Boolean.FALSE.equals(refreshed.getEnabled())) {
                throw exception(INVITE_CODE_DISABLED);
            }
            throw exception(INVITE_CODE_EXHAUSTED);
        }
        return inviteCode;
    }

    @Override
    public String createCode(Long operatorUserId, Integer usageLimit, String remark) {
        int limit = usageLimit == null ? -1 : usageLimit;
        // 重试生成唯一码
        for (int i = 0; i < MAX_CREATE_RETRY; i++) {
            String code = RandomUtil.randomString(CODE_CHARSET, CODE_LEN);
            if (inviteCodeMapper.selectByCode(code) != null) {
                continue;
            }
            MerchantInviteCodeDO entity = MerchantInviteCodeDO.builder()
                    .code(code)
                    .operatorUserId(operatorUserId)
                    .usageLimit(limit)
                    .usedCount(0)
                    .enabled(Boolean.TRUE)
                    .remark(remark)
                    .build();
            inviteCodeMapper.insert(entity);
            log.info("[createCode] 生成邀请码成功，operator={}, code={}, limit={}",
                    operatorUserId, code, limit);
            return code;
        }
        throw new IllegalStateException("生成邀请码失败，请重试");
    }
}
