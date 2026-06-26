package cn.iocoder.yudao.module.merchant.service.card;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.card.ServiceCardDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.card.ServiceCardDefDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.card.ServiceCardVerifyDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.card.ServiceCardDefMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.card.ServiceCardMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.card.ServiceCardVerifyMapper;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 服务卡包 / 核销 Service 实现。
 */
@Slf4j
@Service
public class ServiceCardServiceImpl implements ServiceCardService {

    @Resource
    private ServiceCardDefMapper defMapper;
    @Resource
    private ServiceCardMapper cardMapper;
    @Resource
    private ServiceCardVerifyMapper verifyMapper;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private MemberUserApi memberUserApi;

    // ==================== 商家：卡定义 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDefs(Long tenantId, Long spuId, List<ServiceCardDefDO> defs) {
        if (spuId == null) {
            throw ServiceExceptionUtil.exception0(1_031_005_001, "缺少商品 ID");
        }
        TenantUtils.execute(tenantId, () -> {
            defMapper.deleteBySpuId(spuId);
            if (defs == null || defs.isEmpty()) {
                return;
            }
            int sort = 0;
            for (ServiceCardDefDO d : defs) {
                if (d.getName() == null || d.getName().trim().isEmpty()) {
                    continue;
                }
                ServiceCardDefDO row = ServiceCardDefDO.builder()
                        .spuId(spuId)
                        .name(d.getName().trim())
                        .validityDays(d.getValidityDays() == null || d.getValidityDays() <= 0 ? 365 : d.getValidityDays())
                        // maxCount: null 或 <=0 视为不限次
                        .maxCount(d.getMaxCount() == null || d.getMaxCount() <= 0 ? null : d.getMaxCount())
                        .description(d.getDescription() == null ? "" : d.getDescription())
                        .sort(sort++)
                        .build();
                defMapper.insert(row);
            }
        });
    }

    @Override
    public List<ServiceCardDefDO> listDefs(Long tenantId, Long spuId) {
        if (spuId == null) {
            return Collections.emptyList();
        }
        return TenantUtils.execute(tenantId, () -> defMapper.selectListBySpuId(spuId));
    }

    // ==================== 发卡（付款后，幂等） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issueForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        // 幂等：已发过卡则跳过
        if (!cardMapper.selectListByOrderId(orderId).isEmpty()) {
            return;
        }
        TradeOrderDO order = tradeOrderQueryService.getOrder(orderId);
        if (order == null || order.getUserId() == null) {
            return;
        }
        List<TradeOrderItemDO> items = tradeOrderQueryService.getOrderItemListByOrderId(orderId);
        if (items == null || items.isEmpty()) {
            return;
        }
        LocalDateTime payTime = order.getPayTime() != null ? order.getPayTime() : LocalDateTime.now();
        int issued = 0;
        for (TradeOrderItemDO item : items) {
            Long spuId = item.getSpuId();
            if (spuId == null) {
                continue;
            }
            List<ServiceCardDefDO> defs = defMapper.selectListBySpuId(spuId);
            if (defs.isEmpty()) {
                continue;
            }
            int qty = item.getCount() == null || item.getCount() <= 0 ? 1 : item.getCount();
            for (ServiceCardDefDO def : defs) {
                LocalDateTime expire = payTime.plusDays(def.getValidityDays() == null ? 365 : def.getValidityDays());
                for (int q = 0; q < qty; q++) {
                    ServiceCardDO card = ServiceCardDO.builder()
                            .defId(def.getId())
                            .spuId(spuId)
                            .orderId(orderId)
                            .userId(order.getUserId())
                            .name(def.getName())
                            .cardNo(genCardNo())
                            .startTime(payTime)
                            .expireTime(expire)
                            .maxCount(def.getMaxCount())
                            .usedCount(0)
                            .status(ServiceCardDO.STATUS_ACTIVE)
                            .build();
                    cardMapper.insert(card);
                    issued++;
                }
            }
        }
        if (issued > 0) {
            log.info("[ServiceCard] 发卡完成 orderId={} userId={} 张数={}", orderId, order.getUserId(), issued);
        }
    }

    /** 14 位数字核销码（首位非 0；二维码与数字码同值） */
    private String genCardNo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        sb.append(1 + r.nextInt(9));
        for (int i = 0; i < 13; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.toString();
    }

    // ==================== 用户：我的卡包 ====================

    @Override
    public List<Map<String, Object>> listMyCards(Long userId) {
        List<ServiceCardDO> cards = TenantUtils.executeIgnore(() -> cardMapper.selectListByUserId(userId));
        if (cards.isEmpty()) {
            return Collections.emptyList();
        }
        // 店名缓存
        Map<Long, String> shopNameMap = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>(cards.size());
        for (ServiceCardDO c : cards) {
            out.add(toCardMap(c, shopNameMap));
        }
        return out;
    }

    @Override
    public Map<String, Object> getMyCard(Long userId, Long cardId) {
        ServiceCardDO c = TenantUtils.executeIgnore(() -> cardMapper.selectById(cardId));
        if (c == null || !Objects.equals(c.getUserId(), userId)) {
            throw ServiceExceptionUtil.exception0(1_031_005_002, "卡券不存在");
        }
        Map<String, Object> map = toCardMap(c, new HashMap<>());
        // 详情附带本卡核销流水
        List<ServiceCardVerifyDO> vs = TenantUtils.executeIgnore(() -> verifyMapper.selectListByCardId(cardId));
        List<Map<String, Object>> records = new ArrayList<>();
        for (ServiceCardVerifyDO v : vs) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("verifyTime", toEpochMilli(v.getVerifyTime()));
            rm.put("countAfter", v.getCountAfter());
            rm.put("remark", v.getRemark());
            records.add(rm);
        }
        map.put("verifyRecords", records);
        return map;
    }

    private Map<String, Object> toCardMap(ServiceCardDO c, Map<Long, String> shopNameMap) {
        String shopName = shopNameMap.get(c.getTenantId());
        if (shopName == null) {
            try {
                ShopInfoDO shop = shopInfoMapper.selectByTenantId(c.getTenantId());
                shopName = shop != null ? shop.getShopName() : ("店铺 #" + c.getTenantId());
            } catch (Exception e) {
                shopName = "店铺 #" + c.getTenantId();
            }
            shopNameMap.put(c.getTenantId(), shopName);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("tenantId", c.getTenantId());
        m.put("shopName", shopName);
        m.put("name", c.getName());
        m.put("cardNo", c.getCardNo());
        m.put("startTime", toEpochMilli(c.getStartTime()));
        m.put("expireTime", toEpochMilli(c.getExpireTime()));
        m.put("maxCount", c.getMaxCount()); // null = 不限次
        m.put("usedCount", c.getUsedCount());
        m.put("remainCount", c.getMaxCount() == null ? null : Math.max(0, c.getMaxCount() - c.getUsedCount()));
        m.put("unlimited", c.getMaxCount() == null);
        m.put("effectiveStatus", effectiveStatus(c)); // ACTIVE / USED_UP / EXPIRED
        return m;
    }

    /** 展示用有效状态：先判用尽，再判过期，否则可用 */
    private String effectiveStatus(ServiceCardDO c) {
        if (ServiceCardDO.STATUS_USED_UP.equals(c.getStatus())) {
            return ServiceCardDO.STATUS_USED_UP;
        }
        if (c.getMaxCount() != null && c.getUsedCount() != null && c.getUsedCount() >= c.getMaxCount()) {
            return ServiceCardDO.STATUS_USED_UP;
        }
        if (c.getExpireTime() != null && c.getExpireTime().isBefore(LocalDateTime.now())) {
            return ServiceCardDO.STATUS_EXPIRED;
        }
        return ServiceCardDO.STATUS_ACTIVE;
    }

    private Long toEpochMilli(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // ==================== 商家：核销 ====================

    @Override
    public Map<String, Object> verifyInfo(Long tenantId, String cardNo) {
        ServiceCardDO c = cardMapper.selectByCardNoAndTenant(trim(cardNo), tenantId);
        if (c == null) {
            throw ServiceExceptionUtil.exception0(1_031_005_003, "未找到该核销码（请确认是否本店的卡）");
        }
        String eff = effectiveStatus(c);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("cardNo", c.getCardNo());
        m.put("name", c.getName());
        m.put("userId", c.getUserId());
        m.put("userMobile", getUserMobile(c.getUserId()));
        m.put("startTime", toEpochMilli(c.getStartTime()));
        m.put("expireTime", toEpochMilli(c.getExpireTime()));
        m.put("maxCount", c.getMaxCount());
        m.put("usedCount", c.getUsedCount());
        m.put("remainCount", c.getMaxCount() == null ? null : Math.max(0, c.getMaxCount() - c.getUsedCount()));
        m.put("unlimited", c.getMaxCount() == null);
        m.put("effectiveStatus", eff);
        m.put("redeemable", ServiceCardDO.STATUS_ACTIVE.equals(eff));
        m.put("reason", ServiceCardDO.STATUS_USED_UP.equals(eff) ? "次数已用尽"
                : (ServiceCardDO.STATUS_EXPIRED.equals(eff) ? "已过期" : ""));
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> redeem(Long tenantId, String cardNo, Long verifierId, String remark) {
        String no = trim(cardNo);
        ServiceCardDO c = cardMapper.selectByCardNoAndTenant(no, tenantId);
        if (c == null) {
            throw ServiceExceptionUtil.exception0(1_031_005_003, "未找到该核销码（请确认是否本店的卡）");
        }
        // 友好预校验（真正的并发安全在 redeemAtomic 的 WHERE 里）
        String eff = effectiveStatus(c);
        if (ServiceCardDO.STATUS_USED_UP.equals(eff)) {
            throw ServiceExceptionUtil.exception0(1_031_005_004, "该卡次数已用尽，无法核销");
        }
        if (ServiceCardDO.STATUS_EXPIRED.equals(eff)) {
            throw ServiceExceptionUtil.exception0(1_031_005_005, "该卡已过有效期，无法核销");
        }
        int rows = cardMapper.redeemAtomic(c.getId(), tenantId);
        if (rows != 1) {
            throw ServiceExceptionUtil.exception0(1_031_005_006, "核销失败：该卡可能已过期/用尽，或正在被处理，请刷新重试");
        }
        int before = c.getUsedCount() == null ? 0 : c.getUsedCount();
        int after = before + 1;
        // 核销流水（@TenantIgnore 上下文，显式补 tenantId）
        ServiceCardVerifyDO v = ServiceCardVerifyDO.builder()
                .cardId(c.getId())
                .cardName(c.getName())   // 服务名快照，历史记录不随卡删除丢名
                .userId(c.getUserId())
                .verifierId(verifierId)
                .verifyTime(LocalDateTime.now())
                .countBefore(before)
                .countAfter(after)
                .remark(remark == null ? "" : remark)
                .build();
        v.setTenantId(tenantId);
        verifyMapper.insert(v);

        boolean usedUp = c.getMaxCount() != null && after >= c.getMaxCount();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cardNo", c.getCardNo());
        m.put("name", c.getName());
        m.put("usedCount", after);
        m.put("maxCount", c.getMaxCount());
        m.put("remainCount", c.getMaxCount() == null ? null : Math.max(0, c.getMaxCount() - after));
        m.put("unlimited", c.getMaxCount() == null);
        m.put("usedUp", usedUp);
        m.put("expireTime", toEpochMilli(c.getExpireTime()));
        log.info("[ServiceCard] 核销成功 cardNo={} tenantId={} verifierId={} used {}→{}", c.getCardNo(), tenantId, verifierId, before, after);
        return m;
    }

    @Override
    public PageResult<Map<String, Object>> listVerifyRecords(Long tenantId, PageParam pageParam) {
        PageResult<ServiceCardVerifyDO> page = verifyMapper.selectPageByTenant(tenantId, pageParam);
        List<Map<String, Object>> list = new ArrayList<>();
        // 关联卡名 + 用户手机号
        Map<Long, ServiceCardDO> cardCache = new HashMap<>();
        for (ServiceCardVerifyDO v : page.getList()) {
            ServiceCardDO card = cardCache.get(v.getCardId());
            if (card == null) {
                card = cardMapper.selectById(v.getCardId());
                if (card != null) {
                    cardCache.put(v.getCardId(), card);
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("cardId", v.getCardId());
            // 优先用流水里的服务名快照；老数据没快照再回退查活卡
            String cardName = v.getCardName() != null && !v.getCardName().isEmpty()
                    ? v.getCardName() : (card != null ? card.getName() : "");
            m.put("cardName", cardName);
            m.put("cardNo", card != null ? card.getCardNo() : "");
            m.put("userMobile", getUserMobile(v.getUserId()));
            m.put("verifyTime", toEpochMilli(v.getVerifyTime()));
            m.put("countAfter", v.getCountAfter());
            m.put("remark", v.getRemark());
            list.add(m);
        }
        return new PageResult<>(list, page.getTotal());
    }

    // ==================== 工具 ====================

    private String getUserMobile(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            Map<Long, MemberUserRespDTO> map = memberUserApi.getUserMap(Collections.singletonList(userId));
            MemberUserRespDTO u = map.get(userId);
            return u != null ? u.getMobile() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

}
