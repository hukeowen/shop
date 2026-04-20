package cn.iocoder.yudao.module.video.service;

import java.util.List;

/**
 * 短视频文案 Service
 *
 * <p>调用火山方舟（Ark）豆包 LLM，根据商户输入生成抖音/微信风格的逐句文案。</p>
 */
public interface CopywritingService {

    /**
     * 生成逐句文案。
     *
     * @param shopName        店铺名（用于结尾引导"扫码进店"等话术）
     * @param userDescription 商户的口语化描述（如"我是卖烤地瓜的，香甜软糯，5块钱一个"）
     * @return 逐句文案列表（每句 ≤ 15 字，共 8-15 句，总字数 150-250），调用失败将抛运行时异常
     */
    List<String> generateCopywriting(String shopName, String userDescription);

}
