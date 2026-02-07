package com.example.flashsale;

import com.example.flashsale.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class InventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private org.redisson.api.RedissonClient redissonClient;

    @Test
    void testConcurrentPurchase() throws InterruptedException {
        // Setup: Ensure stock is 1 for 'item1'
        redissonClient.getAtomicLong("stock:item1").set(1);
        
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        AtomicInteger successfulPurchases = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            int userId = i;
            executor.submit(() -> {
                // Use dummy user ID and price
                if (inventoryService.purchase("user" + userId, "item1", 100.0)) {
                    successfulPurchases.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Expect exactly 1 successful purchase
        assertEquals(1, successfulPurchases.get());
        // Expect stock to be 0
        assertEquals(0, inventoryService.getStock("item1"));
    }
}
