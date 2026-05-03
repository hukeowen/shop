package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.module.merchant.config.AllinpayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 通联 H5 收银台 / 交易查询 RestTemplate 单例。
 *
 * <p>独立 bean 避免污染全局 RestTemplate 配置；超时按 AllinpayProperties 走，
 * 默认连接 5s / 读取 15s。</p>
 */
@Configuration
public class AllinpayHttpConfig {

    @Bean(name = "allinpayRestTemplate")
    public RestTemplate allinpayRestTemplate(AllinpayProperties props) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(Math.max(1, props.getConnectTimeoutSec())).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(Math.max(1, props.getReadTimeoutSec())).toMillis());
        return new RestTemplate(f);
    }
}
