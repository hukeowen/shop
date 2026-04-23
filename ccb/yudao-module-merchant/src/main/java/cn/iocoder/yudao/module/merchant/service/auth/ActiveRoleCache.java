package cn.iocoder.yudao.module.merchant.service.auth;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;

/**
 * 保存会员当前激活角色（member / merchant）的缓存
 *
 * <p>因为 yudao starter 的 OAuth2 Token 不支持 extra claims，我们无法把 activeRole
 * 塞进 JWT，只能将其与 userId 绑定存到 Redis。TTL 取 7 天，远大于 AccessToken 时长。</p>
 */
@Service
public class ActiveRoleCache {

    private static final String KEY_PREFIX = "wx:active-role:";
    private static final Duration TTL = Duration.ofDays(7);

    public static final String ROLE_MEMBER = "member";
    public static final String ROLE_MERCHANT = "merchant";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public String get(Long userId) {
        if (userId == null) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
    }

    public void set(Long userId, String role) {
        if (userId == null || StrUtil.isBlank(role)) {
            return;
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId, role, TTL);
    }

    public static boolean isValidRole(String role) {
        return ROLE_MEMBER.equals(role) || ROLE_MERCHANT.equals(role);
    }
}
