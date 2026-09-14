package com.comac.rpm.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 登录会话：Token（jti）存入 Redis 7，登出即失效。
 */
@Service
public class TokenSessionService {

    public static final String KEY_PREFIX = "rpm:token:";

    private final StringRedisTemplate redisTemplate;

    @Value("${rpm.jwt.expire-hours:12}")
    private long expireHours;

    public TokenSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String jti, Long userId) {
        if (jti == null || jti.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().set(
                KEY_PREFIX + jti,
                String.valueOf(userId == null ? 0 : userId),
                Duration.ofHours(expireHours)
        );
    }

    public boolean exists(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        Boolean ok = redisTemplate.hasKey(KEY_PREFIX + jti);
        return Boolean.TRUE.equals(ok);
    }

    public void remove(String jti) {
        if (jti == null || jti.isEmpty()) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + jti);
    }

    public void refreshTtl(String jti) {
        if (jti == null || jti.isEmpty()) {
            return;
        }
        redisTemplate.expire(KEY_PREFIX + jti, expireHours, TimeUnit.HOURS);
    }
}
