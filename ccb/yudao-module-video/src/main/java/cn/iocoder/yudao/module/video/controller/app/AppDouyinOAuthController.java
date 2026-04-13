package cn.iocoder.yudao.module.video.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.video.service.DouyinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 抖音授权")
@RestController
@RequestMapping("/video/douyin/oauth")
@Validated
public class AppDouyinOAuthController {

    @Resource
    private DouyinService douyinService;

    @GetMapping("/url")
    @Operation(summary = "获取抖音授权URL")
    @Parameter(name = "merchantId", description = "商户编号", required = true)
    @Parameter(name = "redirectUri", description = "回调地址", required = true)
    public CommonResult<String> getOAuthUrl(@RequestParam("merchantId") Long merchantId,
                                             @RequestParam("redirectUri") String redirectUri) {
        return success(douyinService.getOAuthUrl(merchantId, redirectUri));
    }

    @GetMapping("/callback")
    @Operation(summary = "抖音授权回调 - 返回H5页面通知小程序")
    public void handleCallback(@RequestParam("code") String code,
                                @RequestParam("state") Long merchantId,
                                HttpServletResponse response) throws IOException {
        // 处理 OAuth 回调，保存 token 到商户表
        douyinService.handleOAuthCallback(merchantId, code);
        // 返回 H5 页面，通过微信 JS-SDK 通知小程序授权完成并自动返回
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>授权成功</title></head><body>"
            + "<div style=\"text-align:center;padding:100px 20px;\">"
            + "<h2 style=\"color:#07c160;\">抖音授权成功!</h2>"
            + "<p style=\"color:#666;\">正在返回小程序...</p>"
            + "</div>"
            + "<script src=\"https://res.wx.qq.com/open/js/jweixin-1.6.0.js\"></script>"
            + "<script>"
            + "wx.miniProgram.postMessage({data:{type:'douyin_bindback',success:true}});"
            + "setTimeout(function(){wx.miniProgram.navigateBack();},1500);"
            + "</script></body></html>"
        );
    }

}
