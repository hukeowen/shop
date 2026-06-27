package cn.iocoder.yudao.module.merchant.dal.dataobject.card;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 服务卡定义（挂在商品 SPU 上，商家建商品时配置）。
 *
 * <p>例：汽车美容 ¥1288 服务包 → 「洗车卡」(validity_days=730, max_count=null 不限次)
 * + 「保养卡」(validity_days=730, max_count=10)。</p>
 *
 * <p>每个 SPU 可挂 0..N 张卡；用户购买后按本定义生成 {@link ServiceCardDO} 卡实例。</p>
 */
@TableName("shop_service_card_def")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCardDefDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 所属商品 SPU ID（= 服务包/套餐商品，购买它才发卡） */
    private Long spuId;

    /** 该卡对应的「单项服务商品」SPU ID（如 洗车/保养 的商品）；null=纯自定义卡（老数据兼容） */
    private Long itemSpuId;

    /** 卡名称，如「洗车卡」（选了 itemSpuId 时为该商品名快照） */
    private String name;

    /** 有效天数（从用户付款日起算） */
    private Integer validityDays;

    /** 可核销次数；null = 不限次数 */
    private Integer maxCount;

    /** 卡说明 / 使用须知 */
    private String description;

    /** 排序 */
    private Integer sort;

}
