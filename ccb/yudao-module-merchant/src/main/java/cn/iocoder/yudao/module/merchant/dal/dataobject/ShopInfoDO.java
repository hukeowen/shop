package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 店铺详情 DO
 *
 * 平台级表（不继承 TenantBaseDO），支持跨租户查询（用户小程序附近/分类页需要）。
 * tenant_id 作为普通字段标识归属商户。
 */
@TableName("shop_info")
@KeySequence("shop_info_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopInfoDO extends BaseDO {

    @TableId
    private Long id;

    /** 所属租户ID（一租户一店铺，唯一） */
    private Long tenantId;
    /** 店铺名称 */
    private String shopName;
    /** 经营类目ID */
    private Long categoryId;
    /** 店铺封面图 */
    private String coverUrl;
    /** 店铺简介 */
    private String description;
    /** 店铺公告 */
    private String notice;

    // ========== 位置 ==========
    /** 经度 */
    private BigDecimal longitude;
    /** 纬度 */
    private BigDecimal latitude;
    /** 详细地址 */
    private String address;

    // ========== 运营信息 ==========
    /** 营业时间（如 09:00-22:00） */
    private String businessHours;
    /** 客服电话 */
    private String mobile;
    /** 店铺状态：1正常 2暂停营业 3违规关闭 */
    private Integer status;

    // ========== 排名缓存（每日定时更新） ==========
    /** 近30天销量（字段名含数字，MP 驼峰转下划线不生效，必须显式指定列名） */
    @TableField("sales_30d")
    private Integer sales30d;
    /** 平均评分 */
    private BigDecimal avgRating;

    /** 商户余额（分），来源：订单完成后的收入，扣除佣金后结算 */
    private Integer balance;

    // ========== 在线支付开通 ==========
    /** 在线支付是否已开通（平台审核通过后置 true） */
    private Boolean onlinePayEnabled;
    /** 在线支付申请状态：0未申请 1审核中 2已开通 3已驳回 4通联进件中（异步等通联回调） */
    private Integer payApplyStatus;
    /** 通联支付商户号（通联回调下发后写入） */
    private String tlMchId;
    /** 通联支付密钥（AES 加密存储，展示前 4 后 4 脱敏） */
    private String tlMchKey;
    /** 通联进件业务流水号（platform 端 outOrderId）；通联回调按这个反查店铺 */
    private String tlOpenOrderId;
    /** 驳回原因 */
    private String payApplyRejectReason;

    // ========== 进件 KYC 资质（TOS key 私有存储） ==========
    // 仅存 TOS key（VARCHAR(512)），不存 URL：
    //   · 上传走 sidecar /oss/upload acl='private'，TOS 不开放公网读
    //   · 显示时由调用方调 sidecar /oss/sign?key=xxx 现签 1h 预签名 URL
    //   · 这样 KYC 证件落库后，就算 DB 漏出去也无法直接拼出可访问 URL（无 SK 签不了）
    /** 法人身份证正面 TOS key */
    private String idCardFrontKey;
    /** 法人身份证背面 TOS key */
    private String idCardBackKey;
    /** 营业执照 TOS key */
    private String businessLicenseKey;

}
