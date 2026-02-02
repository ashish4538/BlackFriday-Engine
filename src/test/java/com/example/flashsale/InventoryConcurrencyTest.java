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

    @Test
    void testConcurrentPurchase() throws InterruptedException {
        // Setup: Ensure stock is 1 for 'item1'
        inventoryService.setStock("item1", 1);
        
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        AtomicInteger successfulPurchases = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                if (inventoryService.purchase("item1")) {
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
