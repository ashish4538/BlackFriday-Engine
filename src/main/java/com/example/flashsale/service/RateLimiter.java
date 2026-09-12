package com.example.flashsale.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RateLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setLocation(new ClassPathResource("redis/rate-limit.lua"));
        SCRIPT.setResultType(Long.class);
    }
    private final StringRedisTemplate redis;
    private final int limit;
    private final long windowMs;

    public RateLimiter(StringRedisTemplate redis, @Value("${purchase.rate-limit.requests:20}") int limit,
                       @Value("${purchase.rate-limit.window-ms:1000}") long windowMs) {
        if (limit < 1 || windowMs < 1) throw new IllegalArgumentException("Rate limit must be positive");
        this.redis = redis;
        this.limit = limit;
        this.windowMs = windowMs;
    }

    // Window starts at the first accepted request; rejected requests do not extend it.
    public long retryAfterMs(String username) {
        Long result = redis.execute(SCRIPT, List.of("purchase-rate:" + username),
                Integer.toString(limit), Long.toString(windowMs));
        if (result == null) throw new IllegalStateException("Missing rate-limit decision");
        return result;
    }
}
