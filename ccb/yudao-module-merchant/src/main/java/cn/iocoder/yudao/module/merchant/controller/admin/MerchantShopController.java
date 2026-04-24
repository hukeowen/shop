package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.shop.ShopPayApplyAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.shop.ShopPayApplyPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 店铺在线支付开通审核")
@RestController
@RequestMapping("/merchant/shop")
@Validated
public class MerchantShopController {

    @Resource
    private ShopInfoMapper shopInfoMapper;

    @GetMapping("/pay-apply/page")
    @Operation(summary = "分页查询在线支付开通申请")
    @PreAuthorize("@ss.hasPermission('merchant:shop:pay-apply:query')")
    public CommonResult<PageResult<ShopInfoDO>> getPayApplyPage(@Valid ShopPayApplyPageReqVO pageReqVO) {
        LambdaQueryWrapper<ShopInfoDO> wrapper = new LambdaQueryWrapper<ShopInfoDO>()
                .isNotNull(ShopInfoDO::getPayApplyStatus)
                .orderByDesc(ShopInfoDO::getCreateTime);
        if (pageReqVO.getStatus() != null) {
            wrapper.eq(ShopInfoDO::getPayApplyStatus, pageReqVO.getStatus());
        }

        // 手动分页
        long total = shopInfoMapper.selectCount(wrapper);
        int offset = (pageReqVO.getPageNo() - 1) * pageReqVO.getPageSize();
        wrapper.last("LIMIT " + pageReqVO.getPageSize() + " OFFSET " + offset);
        List<ShopInfoDO> list = shopInfoMapper.selectList(wrapper);

        return success(new PageResult<>(list, total));
    }

    @PutMapping("/pay-apply/audit")
    @Operation(summary = "审核在线支付开通申请（通过或驳回）")
    @PreAuthorize("@ss.hasPermission('merchant:shop:pay-apply:audit')")
    public CommonResult<Boolean> auditPayApply(@Valid @RequestBody ShopPayApplyAuditReqVO reqVO) {
        ShopInfoDO shop = shopInfoMapper.selectById(reqVO.getShopId());
        if (shop == null) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "店铺不存在");
        }
        if (!Integer.valueOf(1).equals(shop.getPayApplyStatus())) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "该申请不在审核中状态");
        }

        ShopInfoDO update = new ShopInfoDO();
        update.setId(reqVO.getShopId());
        if (Boolean.TRUE.equals(reqVO.getApproved())) {
            update.setOnlinePayEnabled(true);
            update.setPayApplyStatus(2);
        } else {
            if (reqVO.getRejectReason() == null || reqVO.getRejectReason().isEmpty()) {
                throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "驳回原因不能为空");
            }
            update.setOnlinePayEnabled(false);
            update.setPayApplyStatus(3);
            update.setPayApplyRejectReason(reqVO.getRejectReason());
        }
        shopInfoMapper.updateById(update);
        return success(true);
    }

}
