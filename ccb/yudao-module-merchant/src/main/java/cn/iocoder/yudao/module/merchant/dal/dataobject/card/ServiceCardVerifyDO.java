package cn.iocoder.yudao.module.merchant.dal.dataobject.card;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 服务卡核销流水。每核销一次写一条。
 */
@TableName("shop_service_card_verify")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCardVerifyDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 卡实例 ID */
    private Long cardId;

    /** 服务卡名称快照（核销时记下，卡/商品后续被删也不丢历史名） */
    private String cardName;

    /** 持卡用户 ID */
    private Long userId;

    /** 核销操作人（商户登录用户）ID */
    private Long verifierId;

    /** 核销时间 */
    private LocalDateTime verifyTime;

    /** 核销前已用次数 */
    private Integer countBefore;

    /** 核销后已用次数 */
    private Integer countAfter;

    /** 核销备注 */
    private String remark;

}
