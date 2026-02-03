package com.example.flashsale.model;

public class OrderEvent {
    private String userId;
    private String itemId;
    private double price;

    public OrderEvent() {}

    public OrderEvent(String userId, String itemId, double price) {
        this.userId = userId;
        this.itemId = itemId;
        this.price = price;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}