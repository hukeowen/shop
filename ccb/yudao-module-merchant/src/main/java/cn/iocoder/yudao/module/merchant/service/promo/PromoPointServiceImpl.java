package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopConsumePointRecordDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoRecordDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopConsumePointRecordMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoRecordMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 双积分账本实现。
 *
 * 并发安全：
 *   - 余额变更走 Mapper 的 UPDATE col = col + ?（原子），不再读改写，杜绝丢更新
 *   - 扣减用 WHERE col + delta >= 0 兜底；UPDATE 影响 0 行 = 余额不足，抛业务异常
 *   - getOrCreateAccount 在并发首单下可能两个事务同插入 → 捕获 DuplicateKeyException 重读
 *
 * 幂等：
 *   - 调用前检查 record mapper 的 (userId, sourceType, sourceId) 三元组
 *   - 已存在 → 返 false，整个调用安全跳过（不动余额、不写流水）
 */
@Service
@Slf4j
public class PromoPointServiceImpl implements PromoPointService {

    @Resource
    private ShopUserStarMapper userStarMapper;
    @Resource
    private ShopPromoRecordMapper promoRecordMapper;
    @Resource
    private ShopConsumePointRecordMapper consumeRecordMapper;
    @Resource
    private PromoConfigService promoConfigService;

    @Override
    public ShopUserStarDO getOrCreateAccount(Long userId) {
        ShopUserStarDO account = userStarMapper.selectByUserId(userId);
        if (account != null) {
            return account;
        }
        account = ShopUserStarDO.builder()
                .userId(userId)
                .directCount(0)
                .teamSalesCount(0)
                .currentStar(0)
                .promoPointBalance(0L)
                .consumePointBalance(0L)
                .build();
        try {
            userStarMapper.insert(account);
            return account;
        } catch (DuplicateKeyException e) {
            // 并发首单：另一个事务先插入了 → 重读
            ShopUserStarDO concurrent = userStarMapper.selectByUserId(userId);
            if (concurrent != null) {
                return concurrent;
            }
            throw e; // 极端情况：重读还是 null（理论上不可能），抛回去让上层失败可见
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addPromoPoint(Long userId, long amount, String sourceType, Long sourceId, String remark) {
        if (amount <= 0) {
            return false;
        }
        if (promoRecordMapper.exists(userId, sourceType, sourceId)) {
            return false;
        }
        getOrCreateAccount(userId);  // 确保账户存在
        int rows = userStarMapper.addPromoPointBalance(userId, amount);
        if (rows != 1) {
            throw new IllegalStateException("addPromoPoint 原子写失败 userId=" + userId
                    + " amount=" + amount + " rows=" + rows);
        }
        long newBalance = userStarMapper.selectByUserId(userId).getPromoPointBalance();
        promoRecordMapper.insert(buildPromoRecord(userId, sourceType, sourceId, amount, newBalance, remark));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductPromoPoint(Long userId, long amount, String sourceType, Long sourceId, String remark) {
        if (amount <= 0) {
            return false;
        }
        if (promoRecordMapper.exists(userId, sourceType, sourceId)) {
            return false;
        }
        getOrCreateAccount(userId);
        int rows = userStarMapper.addPromoPointBalance(userId, -amount);
        if (rows == 0) {
            // 余额不足；此处不查余额防止额外 IO，错误信息保持简洁
            throw new IllegalStateException("推广积分余额不足 userId=" + userId + " need=" + amount);
        }
        long newBalance = userStarMapper.selectByUserId(userId).getPromoPointBalance();
        promoRecordMapper.insert(buildPromoRecord(userId, sourceType, sourceId, -amount, newBalance, remark));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addConsumePoint(Long userId, long amount, String sourceType, Long sourceId, String remark) {
        if (amount <= 0) {
            return false;
        }
        if (consumeRecordMapper.exists(userId, sourceType, sourceId)) {
            return false;
        }
        getOrCreateAccount(userId);
        int rows = userStarMapper.addConsumePointBalance(userId, amount);
        if (rows != 1) {
            throw new IllegalStateException("addConsumePoint 原子写失败 userId=" + userId
                    + " amount=" + amount + " rows=" + rows);
        }
        long newBalance = userStarMapper.selectByUserId(userId).getConsumePointBalance();
        consumeRecordMapper.insert(buildConsumeRecord(userId, sourceType, sourceId, amount, newBalance, remark));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductConsumePoint(Long userId, long amount, String sourceType, Long sourceId, String remark) {
        if (amount <= 0) {
            return false;
        }
        if (consumeRecordMapper.exists(userId, sourceType, sourceId)) {
            return false;
        }
        getOrCreateAccount(userId);
        int rows = userStarMapper.addConsumePointBalance(userId, -amount);
        if (rows == 0) {
            throw new IllegalStateException("消费积分余额不足 userId=" + userId + " need=" + amount);
        }
        long newBalance = userStarMapper.selectByUserId(userId).getConsumePointBalance();
        consumeRecordMapper.insert(buildConsumeRecord(userId, sourceType, sourceId, -amount, newBalance, remark));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void convertPromoToConsume(Long userId, long promoAmount, Long idempotencyKey) {
        if (promoAmount <= 0) {
            throw new IllegalArgumentException("转换金额必须 > 0");
        }
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("convertPromoToConsume 必须提供 idempotencyKey");
        }
        PromoConfigDO config = promoConfigService.getConfig();
        BigDecimal ratio = config.getPointConversionRatio() != null
                ? config.getPointConversionRatio()
                : BigDecimal.ONE;
        long consumeAmount = BigDecimal.valueOf(promoAmount)
                .multiply(ratio)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
        if (consumeAmount <= 0) {
            throw new IllegalArgumentException("按当前转换比例换算后消费积分为 0，无法转换");
        }
        // 推广侧 / 消费侧用同一 idempotencyKey；两表互不冲突
        deductPromoPoint(userId, promoAmount, "CONVERT", idempotencyKey, "转换为消费积分");
        addConsumePoint(userId, consumeAmount, "CONVERT", idempotencyKey, "由推广积分转换");
    }

    private ShopPromoRecordDO buildPromoRecord(Long userId, String sourceType, Long sourceId,
                                               long amount, long balanceAfter, String remark) {
        return ShopPromoRecordDO.builder()
                .userId(userId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .remark(remark)
                .build();
    }

    private ShopConsumePointRecordDO buildConsumeRecord(Long userId, String sourceType, Long sourceId,
                                                        long amount, long balanceAfter, String remark) {
        return ShopConsumePointRecordDO.builder()
                .userId(userId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .remark(remark)
                .build();
    }

}
