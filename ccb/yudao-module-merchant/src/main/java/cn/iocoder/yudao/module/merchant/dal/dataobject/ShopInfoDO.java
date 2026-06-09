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
@TableName(value = "shop_info", autoResultMap = true)
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
    /** 店铺特色标签 CSV，如「炭火现烤,现做现卖,不赶时间」最多 6 个，单个 ≤ 8 字。
     *  原型 ④ feature-chips（user-h5.html line 2572-2577）。 */
    private String featureTags;

    // ========== 位置 ==========
    /** 经度 */
    private BigDecimal longitude;
    /** 纬度 */
    private BigDecimal latitude;
    /** 详细地址 */
    private String address;

    // ========== 运营信息 ==========
    /** 营业时间（旧版纯文本，如 09:00-22:00；保留兼容） */
    private String businessHours;
    /** 营业时间 JSON：{"start":"09:00","end":"22:00","days":[1..7]}；
     *  机器可解析，用于用户侧 isOpenNow 判断。NULL 视为 24/7。 */
    private String businessHoursJson;
    /** 客服电话 */
    private String mobile;
    /** 店铺状态：1正常 2暂停营业 3违规关闭（平台管理员控制） */
    private Integer status;

    /** 行业类型（V040）：bbq/snack/drink/restaurant/fruit/super/tea/tea_house/bakery/clothing/massage/beauty/other；
     *  NULL=未选；AI 视频 BFF 按此值注入对应行业 cinematography prompt */
    private String businessType;

    // ========== 营业打卡 / 主动打烊（V039） ==========
    /** 商户主动打烊开关：true=已打烊（用户侧不显示且不可下单，无视其他闸门） */
    private Boolean manualClosed;
    /** 今日营业打卡日期：== LocalDate.now() 时算"今日已打卡"；商户每日点"开始营业"后写入。
     *  未打卡 → 用户侧不显示，不可下单。强制商户活跃。 */
    private java.time.LocalDate todayOpenAt;

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
    @TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    private String tlMchKey;
    /** 通联进件业务流水号（platform 端 outOrderId）；通联回调按这个反查店铺 */
    private String tlOpenOrderId;
    /** 驳回原因 */
    private String payApplyRejectReason;

    // ========== 通联收付通（每商户独立直清模式）==========
    // 模式：商户在通联自己开户 → 拿到 cusId（落 tl_mch_id）+ appId + RSA 私钥
    // 平台下载通联公钥用于验签
    /** 通联是否启用（0=关 1=开） */
    private Boolean tlEnabled;
    /** 通联 appId */
    private String tlAppId;
    /** 商户 RSA 私钥 PEM（签名请求用，AES 加密存储） */
    @TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    private String tlRsaPrivateKey;
    /** 通联 RSA 公钥 PEM（验通联回调签名，AES 加密存储 — 防 DB 泄露后被冒签回调） */
    @TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    private String tlRsaPublicKey;
    /** 商户 SM2 私钥 PEM（国密签名请求；AES 加密存储） */
    @TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    private String tlSm2PrivateKey;
    /** 通联 SM2 公钥 PEM（验回调签名；AES 加密存储） */
    @TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler.class)
    private String tlSm2PublicKey;
    /** 异步回调地址，空则走全局默认 */
    private String tlNotifyUrl;
    /** 签名算法 RSA / RSA2 */
    private String tlSignType;

    // ========== 线下转账收款码（未开通在线支付通道时用） ==========
    /** 微信收款码图片 URL */
    private String wechatPayQrUrl;
    /** 支付宝收款码图片 URL */
    private String alipayPayQrUrl;

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
