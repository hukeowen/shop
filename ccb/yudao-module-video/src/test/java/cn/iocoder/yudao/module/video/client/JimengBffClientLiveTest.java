package cn.iocoder.yudao.module.video.client;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真打火山即梦（用真实 AK/SK），验证 BFF Java 代码与 BFF Node.js / shell
 * 本地脚本签名是否一致、能否拿到 task_id。
 *
 * <p>默认 disabled — 需 env JIMENG_LIVE=1 才跑（避免 CI 触发收费）。本地：</p>
 * <pre>
 *   JIMENG_LIVE=1 \
 *   JIMENG_AK=AKLT... \
 *   JIMENG_SK=Tnp... \
 *   mvn -pl yudao-module-video test -Dtest=JimengBffClientLiveTest
 * </pre>
 */
class JimengBffClientLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "JIMENG_LIVE", matches = "1")
    void liveSubmitReturns200WithRealCredentials() throws Exception {
        String ak = System.getenv("JIMENG_AK");
        String sk = System.getenv("JIMENG_SK");
        assertNotNull(ak, "JIMENG_AK 必须从 env 提供");
        assertNotNull(sk, "JIMENG_SK 必须从 env 提供");

        VolcanoEngineProperties props = new VolcanoEngineProperties();
        props.setJimengAccessKey(ak);
        props.setJimengSecretKey(sk);
        props.setJimengEndpoint("https://visual.volcengineapi.com");
        props.setJimengVersion("2022-08-31");
        props.setJimengRegion("cn-north-1");
        props.setJimengService("cv");

        JimengBffClient client = new JimengBffClient();
        ReflectionTestUtils.setField(client, "props", props);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("req_key", "jimeng_i2v_first_v30");
        payload.put("image_urls",
                Collections.singletonList(
                        "https://tanxiaoer.tos-s3-cn-beijing.volces.com/tanxiaoer/1777902996951-75rvoo.jpg"));
        payload.put("prompt",
                "Extreme close - up of skewered meats held against a roaring flame, fat sizzling and popping in slow motion 120fps, smoke billowing around the skewers, handheld vlog shake with shallow depth of field, golden hour rim light, cinematic street food shot");
        payload.put("frames", 121);
        payload.put("seed", -1);

        String resp = client.callAction("CVSync2AsyncSubmitTask", JsonUtils.toJsonString(payload));
        System.out.println("[live] resp=" + resp);
        assertTrue(resp.contains("\"code\":10000"),
                "火山应返 code=10000；实际响应：" + resp);
        assertTrue(resp.contains("\"task_id\":\""),
                "应拿到 task_id；实际响应：" + resp);
    }
}
