package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * 商户 AI 视频配额流水 Mapper。按 merchant_id 过滤，不做租户过滤。
 */
@Mapper
public interface MerchantVideoQuotaLogMapper extends BaseMapperX<MerchantVideoQuotaLogDO> {

    /**
     * Admin 分页：支持 merchantId / bizType / createTime 范围筛选。
     */
    default PageResult<MerchantVideoQuotaLogDO> selectPage(AiVideoQuotaLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantVideoQuotaLogDO>()
                .eqIfPresent(MerchantVideoQuotaLogDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(MerchantVideoQuotaLogDO::getBizType, reqVO.getBizType())
                .betweenIfPresent(MerchantVideoQuotaLogDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MerchantVideoQuotaLogDO::getId));
    }

    /**
     * App 分页：限定 merchantId，商户只能看自己的流水。
     *
     * @param merchantId 商户 ID，必传；强制 eq（非 eqIfPresent）防止越权
     * @param bizType    可选筛选
     */
    default PageResult<MerchantVideoQuotaLogDO> selectPageByMerchant(Long merchantId, Integer bizType,
                                                                    AiVideoQuotaLogPageReqVO pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MerchantVideoQuotaLogDO>()
                .eq(MerchantVideoQuotaLogDO::getMerchantId, merchantId)
                .eqIfPresent(MerchantVideoQuotaLogDO::getBizType, bizType)
                .orderByDesc(MerchantVideoQuotaLogDO::getId));
    }

    /**
     * 扫描潜在"丢失的扣减"：
     * biz_type=2 (VIDEO_GEN) 的扣减流水，未被 biz_type=3 (VIDEO_REFUND) 按 biz_id 回补，
     * 且 remark 里没有 {@code taskId=...}（说明 BFF 提交后没能成功回写审计链）。
     * 时间窗交给调用方——约定传"足够让任务完成的提交时间已过，但仍在观测期内"的区间。
     */
    @Select("SELECT * FROM merchant_video_quota_log " +
            "WHERE biz_type = 2 AND deleted = 0 " +
            "AND create_time BETWEEN #{start} AND #{end} " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM merchant_video_quota_log b " +
            "  WHERE b.merchant_id = merchant_video_quota_log.merchant_id " +
            "  AND b.biz_type = 3 AND b.remark LIKE CONCAT('%', merchant_video_quota_log.biz_id, '%')" +
            ") " +
            "AND (remark IS NULL OR remark NOT LIKE '%taskId=%') " +
            "ORDER BY create_time DESC LIMIT 100")
    List<MerchantVideoQuotaLogDO> findOrphanDebits(@Param("start") Date start, @Param("end") Date end);

    /**
     * 把 taskId 追加到预扣流水的 remark，完成审计链。
     * 不使用 MyBatis 动态 SQL 的原因：只改一行，直接 {@code LIMIT 1} 更直观。
     */
    @Update("UPDATE merchant_video_quota_log " +
            "SET remark = CONCAT(IFNULL(remark, ''), ' taskId=', #{taskId}) " +
            "WHERE merchant_id = #{merchantId} AND biz_id = #{bizId} AND biz_type = 2 " +
            "LIMIT 1")
    int appendTaskIdToRemark(@Param("merchantId") Long merchantId,
                             @Param("bizId") String bizId,
                             @Param("taskId") String taskId);

}
