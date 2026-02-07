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
    private final com.example.flashsale.repository.OrderRepository orderRepository;
    private final com.example.flashsale.repository.ProductRepository productRepository;

    public InventoryService(RedissonClient redissonClient, KafkaOrderProducer kafkaOrderProducer, 
                            com.example.flashsale.repository.OrderRepository orderRepository,
                            com.example.flashsale.repository.ProductRepository productRepository) {
        this.redissonClient = redissonClient;
        this.kafkaOrderProducer = kafkaOrderProducer;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @PostConstruct
    public void init() {
        // Initialize Default Products in DB if empty
        if (productRepository.count() == 0) {
            productRepository.save(new com.example.flashsale.model.Product("item1", "Gaming Laptop X", 1999.99, "https://placehold.co/600x400/2d2d2d/FFF?text=Laptop", "High-performance gaming beast."));
            productRepository.save(new com.example.flashsale.model.Product("item2", "VR Headset Pro", 499.99, "https://placehold.co/600x400/2d2d2d/FFF?text=VR+Set", "Immersive virtual reality experience."));
            productRepository.save(new com.example.flashsale.model.Product("item3", "4K Monitor", 349.99, "https://placehold.co/600x400/2d2d2d/FFF?text=Monitor", "Crystal clear display for creatives."));
            productRepository.save(new com.example.flashsale.model.Product("item4", "Mechanical Keyboard", 129.99, "https://placehold.co/600x400/2d2d2d/FFF?text=Keyboard", "Tactile switches for typing bliss."));
            productRepository.save(new com.example.flashsale.model.Product("item5", "Wireless Mouse", 79.99, "https://placehold.co/600x400/2d2d2d/FFF?text=Mouse", "Ultra-fast response time."));
            productRepository.save(new com.example.flashsale.model.Product("item6", "Noise Cancelling Headphones", 299.99, "https://placehold.co/600x400/2d2d2d/FFF?text=Headphones", "Focus on your music, not the noise."));
        }

        // Initialize Stock in Redis
        redissonClient.getAtomicLong("stock:item1").compareAndSet(0, 5); // Only set if 0 to avoid overwrite on restart
        redissonClient.getAtomicLong("stock:item2").compareAndSet(0, 50);
        redissonClient.getAtomicLong("stock:item3").compareAndSet(0, 20);
        redissonClient.getAtomicLong("stock:item4").compareAndSet(0, 100);
        redissonClient.getAtomicLong("stock:item5").compareAndSet(0, 150);
        redissonClient.getAtomicLong("stock:item6").compareAndSet(0, 30);
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
                        

                        // Persist Order to DB (MySQL)
                        com.example.flashsale.model.Order order = new com.example.flashsale.model.Order(userId, itemId, price);
                        orderRepository.save(order);

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
