package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App 版本 DO（App 自动升级）。
 *
 * <p>全局表（非租户隔离）——一套商户端 APK 全平台共用，故继承 {@link BaseDO} 而非 TenantBaseDO，
 * 并已在 yudao.tenant.ignore-tables 登记 app_version，避免 mybatis 自动 where tenant_id 报错。</p>
 *
 * <p>后台（平台运营）上传新 APK、填写版本号即发布；商户端 App 启动时拉取 latest 比对 versionCode，
 * 若服务端更高则弹窗提示升级（可强更）。</p>
 */
@TableName("app_version")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVersionDO extends BaseDO {

    @TableId
    private Long id;

    /** 平台：android / ios */
    private String platform;

    /** 版本名（展示用），如 1.0.2 */
    private String versionName;

    /** 版本号（比对用，单调递增），如 2。App 端 plus.runtime 的 versionCode 与此比较 */
    private Integer versionCode;

    /** APK 下载地址（上传后回填 / 或外链） */
    private String downloadUrl;

    /** 更新说明（可多行） */
    private String updateLog;

    /** 是否强制更新（true 时用户不可跳过） */
    private Boolean forceUpdate;

    /** APK 文件大小（字节，选填，用于展示下载进度/大小） */
    private Long fileSize;

    /**
     * 状态：0=发布（启用），1=停用。
     * 见 {@link cn.iocoder.yudao.framework.common.enums.CommonStatusEnum}
     */
    private Integer status;

    /** 备注 */
    private String remark;

}
