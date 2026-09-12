package com.example.flashsale.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "item_id"}))
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 36)
    private String eventId;
    @Column(nullable = false, length = 64)
    private String userId;
    @Column(nullable = false, length = 64)
    private String itemId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    @Column(nullable = false)
    private LocalDateTime orderTime;
    @Column(nullable = false)
    private boolean published;
    private LocalDateTime deliveredAt;

    protected Order() {}

    public Order(String eventId, String userId, String itemId, BigDecimal price) {
        this.eventId = eventId;
        this.userId = userId;
        this.itemId = itemId;
        this.price = price;
        this.orderTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getUserId() { return userId; }
    public String getItemId() { return itemId; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getOrderTime() { return orderTime; }
    public boolean isPublished() { return published; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
}
