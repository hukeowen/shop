package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageUserDO;
import cn.iocoder.yudao.module.trade.dal.mysql.brokerage.BrokerageUserMapper;
import cn.iocoder.yudao.module.trade.service.brokerage.BrokerageUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户小程序 - 扫码入店绑定推广关系 (#32) + 推广中心 (#36)
 */
@Tag(name = "用户小程序 - 推广与分销")
@RestController
@RequestMapping("/user/brokerage")
@Validated
public class AppUserBrokerageController {

    @Resource
    private BrokerageUserService brokerageUserService;
    @Resource
    private BrokerageUserMapper brokerageUserMapper;

    // ==================== #32 扫码入店绑定推广关系 ====================

    @PostMapping("/bind")
    @Operation(summary = "绑定推广关系（扫码入店时调用，shareCode→推广人）")
    @Parameter(name = "shareCode", description = "推广大使分享码", required = true)
    public CommonResult<Boolean> bindPromoter(@RequestParam("shareCode") String shareCode) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long promoterUserId = findUserIdByShareCode(shareCode);
        if (promoterUserId == null) {
            return success(false); // 分享码无效，跳过绑定
        }
        brokerageUserService.bindBrokerageUser(userId, promoterUserId);
        return success(true);
    }

    // ==================== #36 推广中心 ====================

    @GetMapping("/my")
    @Operation(summary = "获取我的推广信息（佣金余额/冻结金额/推广人数）")
    public CommonResult<BrokerageUserDO> getMyBrokerage() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(brokerageUserService.getBrokerageUser(userId));
    }

    @GetMapping("/my-child-count")
    @Operation(summary = "获取我的下级人数（一级+二级）")
    public CommonResult<Long[]> getMyChildCount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long firstLevel = brokerageUserService.getBrokerageUserCountByBindUserId(userId, 1);
        Long secondLevel = brokerageUserService.getBrokerageUserCountByBindUserId(userId, 2);
        return success(new Long[]{firstLevel, secondLevel});
    }

    // ==================== 私有方法 ====================

    /**
     * 通过分享码查找推广人用户ID
     * share_code 存储在 trade_brokerage_user 表
     */
    private Long findUserIdByShareCode(String shareCode) {
        // share_code 是我们 ALTER TABLE 新增的字段，通过 mapper 直接查询
        BrokerageUserDO user = brokerageUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrokerageUserDO>()
                        .eq(BrokerageUserDO::getShareCode, shareCode));
        if (user == null) {
            return null; // 找不到推广人则跳过绑定
        }
        return user.getId(); // BrokerageUserDO 的 id 就是 userId
    }

}
