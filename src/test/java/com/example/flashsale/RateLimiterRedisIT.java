package com.example.flashsale;

import com.example.flashsale.service.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"orders.publish-initial-delay-ms=3600000",
        "orders.topic=flash-sale-orders-it", "orders.consumer-group=flash-sale-orders-it",
        "spring.kafka.listener.auto-startup=false"})
class RateLimiterRedisIT {
    @Autowired StringRedisTemplate redis;

    @Test
    void unavailableRedisNeverProducesAnAllowDecision() throws Exception {
        // Reserve a real TCP port without serving Redis responses; no mocks or data mutation.
        try (var socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            var config = new org.redisson.config.Config().setLazyInitialization(true);
            config.useSingleServer().setAddress("redis://127.0.0.1:" + socket.getLocalPort())
                    .setConnectTimeout(200).setTimeout(200).setRetryAttempts(0)
                    .setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);
            var client = org.redisson.Redisson.create(config);
            try {
                var template = new StringRedisTemplate(new org.redisson.spring.data.connection.RedissonConnectionFactory(client));
                var limiter = new RateLimiter(template, 1, 1000);
                var filter = new com.example.flashsale.security.PurchaseRateLimitFilter(limiter,
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
                var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/orders");
                var response = new org.springframework.mock.web.MockHttpServletResponse();
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                                "buyer", null, java.util.List.of()));
                filter.doFilter(request, response, (req, res) -> fail("Unavailable Redis admitted a purchase"));
                assertEquals(503, response.getStatus());
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
                client.shutdown();
            }
        }
    }

    @Test
    void exactLimitIsAllowedUnderConcurrency() throws Exception {
        String user = "rate-" + UUID.randomUUID();
        var limiter = new RateLimiter(redis, 37, 60000);
        var results = InventoryRedisIT.concurrent(300, 32, i -> limiter.retryAfterMs(user));
        assertEquals(37, results.stream().filter(v -> v == 0).count());
        assertEquals("37", redis.opsForValue().get("purchase-rate:" + user));
        assertTrue(redis.getExpire("purchase-rate:" + user, TimeUnit.MILLISECONDS) > 0);
        System.out.println("RATE attempts=300 concurrency=32 allowed=37 rejected=263");
    }

    @Test
    void rejectedRequestsDoNotExtendWindowAndExpiryAllowsNextWindow() {
        String user = "expiry-" + UUID.randomUUID();
        var limiter = new RateLimiter(redis, 1, 250);
        assertEquals(0, limiter.retryAfterMs(user));
        long before = redis.getExpire("purchase-rate:" + user, TimeUnit.MILLISECONDS);
        assertTrue(limiter.retryAfterMs(user) > 0);
        assertTrue(redis.getExpire("purchase-rate:" + user, TimeUnit.MILLISECONDS) <= before);
        await().atMost(3, TimeUnit.SECONDS).until(() -> !Boolean.TRUE.equals(redis.hasKey("purchase-rate:" + user)));
        assertEquals(0, limiter.retryAfterMs(user));
    }

    @Test
    void scriptCacheFlushRecoversAutomatically() {
        String user = "scripts-" + UUID.randomUUID();
        var limiter = new RateLimiter(redis, 1, 60000);
        assertEquals(0, limiter.retryAfterMs(user));
        // SCRIPT FLUSH affects the dedicated local test Redis only; data is retained.
        try (var connection = redis.getConnectionFactory().getConnection()) {
            connection.scriptingCommands().scriptFlush();
        }
        assertTrue(limiter.retryAfterMs(user) > 0);
    }
}
