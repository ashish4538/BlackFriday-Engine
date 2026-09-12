package com.example.flashsale;

import com.example.flashsale.model.Product;
import com.example.flashsale.repository.*;
import com.example.flashsale.service.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"orders.publish-initial-delay-ms=3600000",
        "orders.topic=flash-sale-orders-it", "orders.consumer-group=flash-sale-orders-it",
        "spring.kafka.listener.auto-startup=false"})
class InventoryRedisIT {
    @Autowired InventoryService inventory;
    @Autowired StringRedisTemplate redis;
    @Autowired RedissonClient redisson;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired MeterRegistry metrics;

    String product(long stock) {
        String id = "it-" + UUID.randomUUID();
        products.saveAndFlush(new Product(id, "Test product", new BigDecimal("19.95"),
                "https://example.com/item.png", "Integration test"));
        assertTrue(inventory.initialize(id, stock));
        return id;
    }

    static <T> List<T> concurrent(int count, int workers, IntFunction<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(workers);
        var start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return action.apply(index);
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentDemandExceedsStockAcrossTwoRedisClients() throws Exception {
        String item = product(25);
        RedissonClient second = Redisson.create(redisson.getConfig());
        try {
            var other = new InventoryService(second, redis, orders, products, metrics, 500);
            var results = concurrent(200, 32, i -> {
                try {
                    (i % 2 == 0 ? inventory : other).purchase("buyer-" + i, item);
                    return true;
                } catch (ResponseStatusException e) {
                    assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
                    return false;
                }
            });
            long accepted = results.stream().filter(Boolean::booleanValue).count();
            long rejected = results.size() - accepted;
            long stock = inventory.getStock(item);
            long oversells = Math.max(0, accepted - 25);
            System.out.printf("INVENTORY attempts=200 concurrency=32 accepted=%d rejected=%d finalStock=%d oversells=%d%n",
                    accepted, rejected, stock, oversells);
            assertEquals(25, accepted);
            assertEquals(175, rejected);
            assertEquals(0, stock);
            assertEquals(0, oversells);
        } finally { second.shutdown(); }
    }

    @Test
    void duplicatePurchasesCreateOneOrderAndConsumeOneUnit() throws Exception {
        String item = product(10);
        var results = concurrent(30, 8, i -> {
            try {
                return inventory.purchase("same-buyer", item).getEventId();
            } catch (ResponseStatusException e) {
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
                return "BUSY";
            }
        });
        var accepted = results.stream().filter(id -> !"BUSY".equals(id)).toList();
        assertFalse(accepted.isEmpty());
        assertEquals(1, new HashSet<>(accepted).size());
        assertEquals(accepted.get(0), inventory.purchase("same-buyer", item).getEventId());
        assertEquals(9, inventory.getStock(item));
        System.out.printf("DUPLICATES attempts=30 accepted=%d busy=%d distinctOrders=1 finalStock=9 oversells=0%n",
                accepted.size(), 30 - accepted.size());
    }

    @Test
    void watchdogRenewsAHealthyLockPastItsInitialLease() throws Exception {
        var config = new org.redisson.config.Config(redisson.getConfig()).setLockWatchdogTimeout(600);
        var client = Redisson.create(config);
        var lock = client.getLock("watchdog-" + UUID.randomUUID());
        try {
            assertTrue(lock.tryLock(0, TimeUnit.MILLISECONDS));
            org.awaitility.Awaitility.await().pollInterval(java.time.Duration.ofMillis(100))
                    .during(java.time.Duration.ofMillis(1500)).atMost(java.time.Duration.ofSeconds(4))
                    .until(lock::isLocked);
            assertTrue(lock.isHeldByCurrentThread());
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
            client.shutdown();
        }
    }

    @Test
    void heldLockTimesOutWithoutReserving() throws Exception {
        String item = product(1);
        var held = redisson.getLock("purchase-lock:" + item + ":buyer");
        held.lock();
        try {
            var results = concurrent(1, 1, i -> assertThrows(ResponseStatusException.class,
                    () -> inventory.purchase("buyer", item)).getStatusCode());
            assertEquals(List.of(HttpStatus.SERVICE_UNAVAILABLE), results);
            assertEquals(1, inventory.getStock(item));
        } finally { held.unlock(); }
    }

    @Test
    void databaseFailureRetainsReservationAndRetryUsesSameEventId() {
        String item = product(1);
        var failed = mock(OrderRepository.class);
        when(failed.findByUserIdAndItemId("buyer", item)).thenReturn(Optional.empty());
        when(failed.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("lost response"));
        var service = new InventoryService(redisson, redis, failed, products, metrics, 500);
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.purchase("buyer", item));
        String eventId = redis.opsForValue().get("reservation:" + item + ":buyer");
        assertNotNull(eventId);
        assertEquals(0, inventory.getStock(item));
        assertFalse(redisson.getLock("purchase-lock:" + item + ":buyer").isLocked());
        var result = inventory.purchase("buyer", item);
        assertEquals(eventId, result.getEventId());
        assertEquals(0, inventory.getStock(item));
    }

    @Test
    void lostDatabaseCommitReplyDoesNotRefundCommittedOrder() {
        String item = product(1);
        var ambiguous = mock(OrderRepository.class);
        when(ambiguous.findByUserIdAndItemId("buyer", item)).thenReturn(Optional.empty());
        when(ambiguous.saveAndFlush(any())).thenAnswer(call -> {
            orders.saveAndFlush(call.getArgument(0));
            throw new org.springframework.dao.DataAccessResourceFailureException("commit reply lost");
        });
        var service = new InventoryService(redisson, redis, ambiguous, products, metrics, 500);
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.purchase("buyer", item));
        assertEquals(0, inventory.getStock(item));
        String eventId = orders.findByUserIdAndItemId("buyer", item).orElseThrow().getEventId();
        assertEquals(eventId, inventory.purchase("buyer", item).getEventId());
        assertEquals(0, inventory.getStock(item));
    }

    @Test
    void corruptCounterDoesNotReserveAndReleasesLock() {
        String item = product(1);
        redis.opsForValue().set("stock:" + item, "bad-counter");
        assertThrows(RuntimeException.class, () -> inventory.purchase("buyer", item));
        assertNull(redis.opsForValue().get("reservation:" + item + ":buyer"));
        assertEquals("bad-counter", redis.opsForValue().get("stock:" + item));
        assertFalse(redisson.getLock("purchase-lock:" + item + ":buyer").isLocked());
    }

    @Test
    void newServiceAndRepeatedInitializationPreserveSoldOutAndMissingState() {
        String item = product(0);
        var restarted = new InventoryService(redisson, redis, orders, products, metrics, 500);
        assertFalse(restarted.initialize(item, 100));
        assertEquals(0, restarted.getStock(item));
        assertThrows(ResponseStatusException.class, () -> restarted.getStock("missing-" + UUID.randomUUID()));
        String sold = product(1);
        inventory.purchase("buyer", sold);
        assertThrows(ResponseStatusException.class, () -> restarted.initialize(sold, 100));
        assertEquals(0, restarted.getStock(sold));
    }
}
