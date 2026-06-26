package cn.iocoder.yudao.server.framework;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 平台跨租户总览：系统租户（id=1，平台超管）访问商城后台时，
 * 商品 / 订单 / 营销 / 统计 等 /admin-api 接口全程忽略租户过滤，
 * 默认聚合所有商户的数据 —— 超管无需一个个切换租户查看。
 *
 * <p>触发条件：当前请求租户 == 1（平台）。若超管在顶部切到某商户租户，
 * 则按该租户作用域（即「切租户筛选」），与产品预期一致。</p>
 *
 * <p>安全：每个请求结束时租户过滤器会调用 {@link TenantContextHolder#clear()}
 * （同时移除 TENANT_ID 与 IGNORE），故本拦截器设置的 ignore 不会泄漏到线程池复用的下个请求。
 * 商户端走 /app-api 带各自 JWT 租户，绝不会命中这里。</p>
 */
@Configuration
public class MallPlatformCrossTenantConfig implements WebMvcConfigurer {

    /** 平台跨租户聚合的商城后台路径前缀 */
    private static final String[] MALL_ADMIN_PREFIXES = {
            "/admin-api/product/", "/admin-api/trade/",
            "/admin-api/promotion/", "/admin-api/statistics/"
    };

    private static final long PLATFORM_TENANT_ID = 1L;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Long tenantId = TenantContextHolder.getTenantId();
                if (tenantId != null && tenantId == PLATFORM_TENANT_ID && isMallAdmin(request.getRequestURI())) {
                    TenantContextHolder.setIgnore(true);
                }
                return true;
            }
        }).addPathPatterns("/admin-api/product/**", "/admin-api/trade/**",
                "/admin-api/promotion/**", "/admin-api/statistics/**");
    }

    private static boolean isMallAdmin(String uri) {
        if (uri == null) {
            return false;
        }
        for (String prefix : MALL_ADMIN_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

}
