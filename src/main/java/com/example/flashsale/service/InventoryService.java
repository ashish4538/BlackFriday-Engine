package com.example.flashsale.service;

import com.example.flashsale.model.Order;
import com.example.flashsale.repository.OrderRepository;
import com.example.flashsale.repository.ProductRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class InventoryService {
    private static final DefaultRedisScript<String> RESERVE = new DefaultRedisScript<>();
    static {
        RESERVE.setLocation(new ClassPathResource("redis/reserve.lua"));
        RESERVE.setResultType(String.class);
    }
    private final RedissonClient redisson;
    private final StringRedisTemplate redis;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final MeterRegistry metrics;
    private final long lockWaitMs;

    public InventoryService(RedissonClient redisson, StringRedisTemplate redis, OrderRepository orders,
                            ProductRepository products, MeterRegistry metrics,
                            @Value("${inventory.lock-wait-ms:500}") long lockWaitMs) {
        this.redisson = redisson;
        this.redis = redis;
        this.orders = orders;
        this.products = products;
        this.metrics = metrics;
        this.lockWaitMs = lockWaitMs;
    }

    // One order per authenticated user/product. Inventory and dedup keys have no TTL.
    public Order purchase(String userId, String itemId) {
        validateItemId(itemId);
        RLock lock = redisson.getLock("purchase-lock:" + itemId + ":" + userId);
        boolean acquired = false;
        try {
            // Watchdog renews the lease; Lua and SQL uniqueness remain the correctness boundary.
            acquired = lock.tryLock(lockWaitMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                metrics.counter("purchase.outcomes", "result", "busy").increment();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Purchase busy; retry");
            }
            var existing = orders.findByUserIdAndItemId(userId, itemId);
            if (existing.isPresent()) {
                metrics.counter("purchase.outcomes", "result", "duplicate").increment();
                return existing.get();
            }
            var product = products.findById(itemId).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
            String eventId = redis.execute(RESERVE,
                    List.of("stock:" + itemId, "reservation:" + itemId + ":" + userId),
                    UUID.randomUUID().toString());
            if ("SOLD_OUT".equals(eventId)) {
                metrics.counter("purchase.outcomes", "result", "sold_out").increment();
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Item out of stock");
            }
            if (eventId == null || "UNINITIALIZED".equals(eventId)) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable");
            }
            // Retain reservation on ambiguous SQL failure: a lost commit response cannot
            // safely be compensated. A same-user/item retry reuses this stable event ID.
            Order order = orders.saveAndFlush(new Order(eventId, userId, itemId, product.getPrice()));
            metrics.counter("purchase.outcomes", "result", "accepted").increment();
            return order;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Purchase interrupted");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    public long getStock(String itemId) {
        validateItemId(itemId);
        String value = redis.opsForValue().get("stock:" + itemId);
        if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not initialized");
        return Long.parseLong(value);
    }

    public boolean initialize(String itemId, long stock) {
        validateItemId(itemId);
        if (stock < 0 || stock > 1_000_000_000L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stock");
        }
        if (!products.existsById(itemId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        // Missing Redis state after sales requires reconciliation, never guessed replenishment.
        if (orders.existsByItemId(itemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Existing orders require inventory reconciliation");
        }
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("stock:" + itemId, Long.toString(stock)));
    }

    private static void validateItemId(String itemId) {
        if (itemId == null || !itemId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product ID");
        }
    }
}
