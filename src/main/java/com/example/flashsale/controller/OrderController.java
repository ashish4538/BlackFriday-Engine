package com.example.flashsale.controller;

import com.example.flashsale.service.InventoryService;
import com.example.flashsale.strategy.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OrderController {

    private final InventoryService inventoryService;
    private final Map<String, PaymentStrategy> paymentStrategies;

    public OrderController(InventoryService inventoryService, Map<String, PaymentStrategy> paymentStrategies) {
        this.inventoryService = inventoryService;
        this.paymentStrategies = paymentStrategies;
    }

    @PostMapping({"/purchase", "/api/orders"})
    public ResponseEntity<String> purchase(
            @RequestParam String userId,   
            @RequestParam String itemId, 
            @RequestParam String paymentType,
            @RequestParam double price      
    ) {
        boolean purchaseResult = inventoryService.purchase(userId, itemId, price);
        
        if (purchaseResult) {
            PaymentStrategy strategy = paymentStrategies.get(paymentType);
            if (strategy == null) {
                return ResponseEntity.badRequest().body("Invalid payment type");
            }
            
            boolean paymentSuccess = strategy.pay(price);
            if (paymentSuccess) {
                return ResponseEntity.ok("Purchase successful with " + paymentType);
            } else {
                return ResponseEntity.status(500).body("Payment failed");
            }
        }
        
        return ResponseEntity.status(409).body("Item out of stock or could not acquire lock");
    }

    @org.springframework.web.bind.annotation.GetMapping("/api/inventory/stock/{itemId}")
    public ResponseEntity<Integer> getStock(@org.springframework.web.bind.annotation.PathVariable String itemId) {
        return ResponseEntity.ok(inventoryService.getStock(itemId));
    }
}
