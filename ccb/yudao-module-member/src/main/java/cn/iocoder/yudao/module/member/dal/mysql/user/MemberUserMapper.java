package cn.iocoder.yudao.module.member.dal.mysql.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会员 User Mapper
 *
 * @author 芋道源码
 */
@Mapper
public interface MemberUserMapper extends BaseMapperX<MemberUserDO> {

    default MemberUserDO selectByMobile(String mobile) {
        return selectOne(MemberUserDO::getMobile, mobile);
    }

    default List<MemberUserDO> selectListByNicknameLike(String nickname) {
        return selectList(new LambdaQueryWrapperX<MemberUserDO>()
                .likeIfPresent(MemberUserDO::getNickname, nickname));
    }

    default PageResult<MemberUserDO> selectPage(MemberUserPageReqVO reqVO) {
        // 处理 tagIds 过滤条件
        String tagIdSql = "";
        if (CollUtil.isNotEmpty(reqVO.getTagIds())) {
            tagIdSql = reqVO.getTagIds().stream()
                    .map(tagId -> "FIND_IN_SET(" + tagId + ", tag_ids)")
                    .collect(Collectors.joining(" OR "));
        }
        // 分页查询
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberUserDO>()
                .likeIfPresent(MemberUserDO::getMobile, reqVO.getMobile())
                .betweenIfPresent(MemberUserDO::getLoginDate, reqVO.getLoginDate())
                .likeIfPresent(MemberUserDO::getNickname, reqVO.getNickname())
                .betweenIfPresent(MemberUserDO::getCreateTime, reqVO.getCreateTime())
                .eqIfPresent(MemberUserDO::getLevelId, reqVO.getLevelId())
                .eqIfPresent(MemberUserDO::getGroupId, reqVO.getGroupId())
                .apply(StrUtil.isNotEmpty(tagIdSql), tagIdSql)
                .orderByDesc(MemberUserDO::getId));
    }

    default Long selectCountByGroupId(Long groupId) {
        return selectCount(MemberUserDO::getGroupId, groupId);
    }

    default Long selectCountByLevelId(Long levelId) {
        return selectCount(MemberUserDO::getLevelId, levelId);
    }

    default Long selectCountByTagId(Long tagId) {
        return selectCount(new LambdaQueryWrapperX<MemberUserDO>()
                .apply("FIND_IN_SET({0}, tag_ids)", tagId));
    }

    /**
     * 更新用户积分（增加）
     *
     * @param id        用户编号
     * @param incrCount 增加积分（正数）
     */
    default void updatePointIncr(Long id, Integer incrCount) {
        Assert.isTrue(incrCount > 0);
        LambdaUpdateWrapper<MemberUserDO> lambdaUpdateWrapper = new LambdaUpdateWrapper<MemberUserDO>()
                .setSql(" point = point + " + incrCount)
                .eq(MemberUserDO::getId, id);
        update(null, lambdaUpdateWrapper);
    }

    /**
     * 更新用户积分（减少）
     *
     * @param id        用户编号
     * @param incrCount 增加积分（负数）
     * @return 更新行数
     */
    default int updatePointDecr(Long id, Integer incrCount) {
        Assert.isTrue(incrCount < 0);
        LambdaUpdateWrapper<MemberUserDO> lambdaUpdateWrapper = new LambdaUpdateWrapper<MemberUserDO>()
                .setSql(" point = point + " + incrCount) // 负数，所以使用 + 号
                .eq(MemberUserDO::getId, id);
        return update(null, lambdaUpdateWrapper);
    }

    /**
     * 根据摊小二统一小程序 OpenID 查询会员。
     *
     * <p>注意：{@code member_user} 为全局表（tenant_id=0），调用方需通过
     * {@link cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore} 或
     * {@code TenantUtils.executeIgnore(...)} 绕开租户拦截。</p>
     */
    default MemberUserDO selectByMiniAppOpenId(String miniAppOpenId) {
        return selectOne(MemberUserDO::getMiniAppOpenId, miniAppOpenId);
    }

    /**
     * 幂等插入会员：若 {@code mini_app_open_id} 命中 UNIQUE 索引冲突，则静默忽略（不抛异常）。
     *
     * <p>用于 wxMiniLogin 并发竞态的兜底：两条请求同时 {@code selectByMiniAppOpenId} 都拿到 null 后
     * 双双进入 insert 分支，第二条会被 UNIQUE 约束拦截。{@code INSERT IGNORE} 让它静默失败返回 0，
     * 调用方随后重新 {@code selectByMiniAppOpenId} 即可拿到先成功那条记录。</p>
     *
     * <p>字段列表与 {@link cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO} 的
     * {@code miniAppOpenId / status / registerTerminal} 对齐，其余字段走 DB 默认值或 NULL。</p>
     *
     * @return 实际写入行数：1 表示本次插入成功；0 表示被 UNIQUE 索引忽略（已有记录）
     */
    @Insert("INSERT IGNORE INTO member_user(mini_app_open_id, status, register_terminal, tenant_id, create_time, update_time) " +
            "VALUES(#{openid}, #{status}, #{terminal}, 0, NOW(), NOW())")
    int insertIgnoreByMiniAppOpenId(@Param("openid") String openid,
                                    @Param("status") Integer status,
                                    @Param("terminal") Integer terminal);

}
