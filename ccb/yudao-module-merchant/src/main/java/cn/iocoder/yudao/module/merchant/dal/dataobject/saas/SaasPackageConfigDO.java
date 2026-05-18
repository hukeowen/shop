package cn.iocoder.yudao.module.merchant.dal.dataobject.saas;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * SaaS 套餐配置 — 平台运营在 admin Vue3 维护，商户在续费页看到。
 *
 * <p>种子数据（V034 已插）：</p>
 * <ul>
 *   <li>BASIC：298 元 / 365 天 / 赠 10 条 AI 视频 / 仅订单系统+推 N 反 1</li>
 *   <li>PRO：1688 元 / 365 天 / 赠 30 条 AI 视频 / 全功能（含团队/星级/奖池）</li>
 * </ul>
 */
@TableName("saas_package_config")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaasPackageConfigDO extends BaseDO {

    public static final String LEVEL_BASIC = "BASIC";
    public static final String LEVEL_PRO = "PRO";
    public static final String LEVEL_TRIAL = "TRIAL";
    public static final String LEVEL_PLATFORM = "PLATFORM";

    @TableId
    private Long id;
    /** BASIC / PRO（TRIAL / PLATFORM 是商户级，不在套餐表里） */
    private String level;
    private String name;
    private Integer priceFen;
    private Integer durationDays;
    private Integer aiVideoGrant;
    /** JSON 数组：可用功能 key — order / tuijian / team / star / pool / brokerage */
    private String features;
    private Integer sort;
    /** 0=上架 1=下架 */
    private Integer status;
    /** 关联的 product_spu.id（V042：套餐作为 tenant=999 平台店铺的商品 SPU，复用 trade 流程） */
    private Long spuId;
}
