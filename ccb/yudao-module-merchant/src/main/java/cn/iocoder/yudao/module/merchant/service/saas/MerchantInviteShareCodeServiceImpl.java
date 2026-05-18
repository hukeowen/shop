package cn.iocoder.yudao.module.merchant.service.saas;

import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantInviteShareCodeDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.MerchantInviteShareCodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.SecureRandom;

@Service
@Slf4j
public class MerchantInviteShareCodeServiceImpl implements MerchantInviteShareCodeService {

    /** 排除易混字符 0/O/1/I/L 的 6 位字母数字（约 1B 个组合，万级商户冲突概率 ~ 万分之一）。 */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private MerchantInviteShareCodeMapper mapper;

    @Override
    public MerchantInviteShareCodeDO getOrCreate(Long referrerUserId, Long referrerTenantId) {
        if (referrerUserId == null || referrerUserId <= 0) {
            throw new IllegalArgumentException("referrerUserId 必填");
        }
        MerchantInviteShareCodeDO existing = mapper.selectByReferrerUserId(referrerUserId);
        if (existing != null) {
            return existing;
        }
        // 首次生成：随机 6 位 code + 处理唯一冲突重试
        for (int attempt = 0; attempt < 8; attempt++) {
            String code = randomCode(6);
            MerchantInviteShareCodeDO doRow = MerchantInviteShareCodeDO.builder()
                    .referrerUserId(referrerUserId)
                    .referrerTenantId(referrerTenantId)
                    .code(code)
                    .usedCount(0)
                    .enabled(true)
                    .remark("")
                    .build();
            try {
                mapper.insert(doRow);
                return doRow;
            } catch (DuplicateKeyException e) {
                // code 撞了，重试。也可能是并发同一 user 两次插入；重读一次
                MerchantInviteShareCodeDO concurrent = mapper.selectByReferrerUserId(referrerUserId);
                if (concurrent != null) {
                    return concurrent;
                }
                log.warn("[InviteShareCode] code 冲突重试 attempt={} user={}", attempt + 1, referrerUserId);
            }
        }
        throw new IllegalStateException("生成分享码连续 8 次冲突，请联系运维");
    }

    @Override
    public MerchantInviteShareCodeDO findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        MerchantInviteShareCodeDO row = mapper.selectByCode(code.trim().toUpperCase());
        if (row == null || Boolean.FALSE.equals(row.getEnabled())) {
            return null;
        }
        return row;
    }

    @Override
    public void incrementUsedCount(String code) {
        if (code == null || code.trim().isEmpty()) return;
        mapper.incrementUsedCount(code.trim().toUpperCase());
    }

    private static String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

}
