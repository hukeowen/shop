package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通联收付通 - 商户进件 / 状态查询客户端
 *
 * <p>实现类 {@link AllinpayMerchantClientImpl} 在 enabled=true 时调真接口；
 * enabled=false 时由 {@link AllinpayMerchantClientNoop} 兜底（log + return null），
 * 让没有通联资质的环境也能跑通编译/测试，业务层用状态字段标记"待通联接入"。</p>
 */
public interface AllinpayMerchantClient {

    /**
     * 提交商户进件申请
     *
     * <p>异步接口：通联接受后立即返回 outOrderId（业务流水号），实际开户结果通过
     * webhook (registerNotifyUrl) 异步推送。</p>
     *
     * @return 业务流水号（outOrderId / mchApplyId），存入 ShopInfoDO 用于回调时反查
     */
    OpenMerchantResult openMerchant(ShopInfoDO shop);

    /**
     * 主动查询某笔进件申请的当前状态（用于 webhook 漏接时兜底轮询）
     */
    OpenMerchantResult queryMerchantStatus(String outOrderId);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class OpenMerchantResult {
        /** 业务流水号 */
        private String outOrderId;
        /** 通联子商户号（仅审核通过后才有） */
        private String tlMchId;
        /** 通联子商户密钥（脱敏存储） */
        private String tlMchKey;
        /** 状态：PENDING / APPROVED / REJECTED */
        private Status status;
        /** 拒绝原因（仅 REJECTED 时） */
        private String rejectReason;

        public enum Status { PENDING, APPROVED, REJECTED }
    }
}
