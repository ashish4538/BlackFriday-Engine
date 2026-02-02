package com.example.flashsale.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final RedissonClient redissonClient;

    @PostConstruct
    public void init() {
        // Initialize with some dummy stock for testing
        // In a real app, this would likely be done via an admin API or migration
        RAtomicLong stock1 = redissonClient.getAtomicLong("stock:item1");
        stock1.set(1);
        RAtomicLong stock2 = redissonClient.getAtomicLong("stock:item2");
        stock2.set(100);
    }

    public boolean purchase(String itemId) {
        String lockKey = "lock:" + itemId;
        String stockKey = "stock:" + itemId;
        
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // Try to acquire lock for up to 5 seconds, lease time 10 seconds
            boolean isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    RAtomicLong stock = redissonClient.getAtomicLong(stockKey);
                    long currentStock = stock.get();
                    
                    if (currentStock > 0) {
                        try {
                            // Simulate processing time
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        stock.decrementAndGet();
                        return true;
                    }
                    return false;
                } finally {
                    lock.unlock();
                }
            } else {
                return false; // Could not acquire lock
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public int getStock(String itemId) {
        RAtomicLong stock = redissonClient.getAtomicLong("stock:" + itemId);
        return (int) stock.get();
    }
}
