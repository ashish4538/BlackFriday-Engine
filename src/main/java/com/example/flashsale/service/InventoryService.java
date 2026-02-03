package com.example.flashsale.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import com.example.flashsale.producer.KafkaOrderProducer;
import com.example.flashsale.model.OrderEvent;

import java.util.concurrent.TimeUnit;

@Service
public class InventoryService {

    private final RedissonClient redissonClient;
    private final KafkaOrderProducer kafkaOrderProducer;

    public InventoryService(RedissonClient redissonClient, KafkaOrderProducer kafkaOrderProducer) {
        this.redissonClient = redissonClient;
        this.kafkaOrderProducer = kafkaOrderProducer;
    }

    @PostConstruct
    public void init() {
        // Initialize with some dummy stock for testing
        // In a real app, this would likely be done via an admin API or migration
        RAtomicLong stock1 = redissonClient.getAtomicLong("stock:item1");
        stock1.set(1);
        RAtomicLong stock2 = redissonClient.getAtomicLong("stock:item2");
        stock2.set(100);
    }

    public boolean purchase(String userId, String itemId, double price) {
        String lockKey = "lock:" + itemId;
        String stockKey = "stock:" + itemId;
        
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    RAtomicLong stock = redissonClient.getAtomicLong(stockKey);
                    long currentStock = stock.get();
                    
                    if (currentStock > 0) {
                        try {
                             Thread.sleep(10); 
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        stock.decrementAndGet();
                        
                        OrderEvent event = new OrderEvent(userId, itemId, price);
                        kafkaOrderProducer.sendOrderEvent(event);
                        
                        return true;
                    }
                    return false;
                } finally {
                    lock.unlock();
                }
            } else {
                return false; 
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
