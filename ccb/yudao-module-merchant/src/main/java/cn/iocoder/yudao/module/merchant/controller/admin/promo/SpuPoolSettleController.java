package cn.iocoder.yudao.module.merchant.controller.admin.promo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.SpuPoolPayoutItemRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.SpuPoolSettleRecordRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolPayoutItemDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolSettleRecordDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolPayoutItemMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolSettleRecordMapper;
import cn.iocoder.yudao.module.merchant.service.promo.SpuPoolSettleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - SPU 星级奖池结算")
@RestController
@RequestMapping("/merchant/promo/pool")
@Validated
public class SpuPoolSettleController {

    @Resource
    private SpuPoolSettleService spuPoolSettleService;
    @Resource
    private SpuStarPoolMapper spuStarPoolMapper;
    @Resource
    private SpuStarPoolSettleRecordMapper settleRecordMapper;
    @Resource
    private SpuStarPoolPayoutItemMapper payoutItemMapper;

    @GetMapping("/balance")
    @Operation(summary = "查某 SPU 当前池余额 + 累计入/出")
    @Parameter(name = "spuId", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:product-promo-config:query')")
    public CommonResult<Map<String, Long>> getBalance(@RequestParam("spuId") @NotNull Long spuId) {
        SpuStarPoolDO pool = spuStarPoolMapper.selectBySpuId(spuId);
        Map<String, Long> resp = new HashMap<>();
        if (pool == null) {
            resp.put("poolBalance", 0L);
            resp.put("totalIn", 0L);
            resp.put("totalOut", 0L);
        } else {
            resp.put("poolBalance", pool.getPoolBalance() == null ? 0L : pool.getPoolBalance());
            resp.put("totalIn", pool.getTotalIn() == null ? 0L : pool.getTotalIn());
            resp.put("totalOut", pool.getTotalOut() == null ? 0L : pool.getTotalOut());
        }
        return success(resp);
    }

    @PostMapping("/settle")
    @Operation(summary = "立即结算某 SPU 的奖池")
    @PreAuthorize("@ss.hasPermission('merchant:product-promo-config:update')")
    public CommonResult<SpuPoolSettleRecordRespVO> settle(
            @RequestParam("spuId") @NotNull Long spuId,
            @RequestParam(value = "remark", required = false) String remark) {
        SpuStarPoolSettleRecordDO record = spuPoolSettleService.settle(spuId, remark);
        SpuPoolSettleRecordRespVO resp = new SpuPoolSettleRecordRespVO();
        BeanUtils.copyProperties(record, resp);
        return success(resp);
    }

    @GetMapping("/settle-records")
    @Operation(summary = "分页查某 SPU 的历次结算单（按时间倒序）")
    @PreAuthorize("@ss.hasPermission('merchant:product-promo-config:query')")
    public CommonResult<PageResult<SpuPoolSettleRecordRespVO>> listRecords(
            @RequestParam("spuId") @NotNull Long spuId,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        PageResult<SpuStarPoolSettleRecordDO> page = settleRecordMapper.selectPageBySpuId(spuId, pageNo, pageSize);
        List<SpuPoolSettleRecordRespVO> list = new ArrayList<>(page.getList().size());
        for (SpuStarPoolSettleRecordDO d : page.getList()) {
            SpuPoolSettleRecordRespVO v = new SpuPoolSettleRecordRespVO();
            BeanUtils.copyProperties(d, v);
            list.add(v);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/settle-record/payouts")
    @Operation(summary = "查某次结算的中奖/均分明细")
    @Parameter(name = "settleId", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:product-promo-config:query')")
    public CommonResult<List<SpuPoolPayoutItemRespVO>> listPayouts(
            @RequestParam("settleId") @NotNull Long settleId) {
        List<SpuStarPoolPayoutItemDO> items = payoutItemMapper.selectListBySettleId(settleId);
        List<SpuPoolPayoutItemRespVO> list = new ArrayList<>(items.size());
        for (SpuStarPoolPayoutItemDO d : items) {
            SpuPoolPayoutItemRespVO v = new SpuPoolPayoutItemRespVO();
            BeanUtils.copyProperties(d, v);
            list.add(v);
        }
        return success(list);
    }

}
