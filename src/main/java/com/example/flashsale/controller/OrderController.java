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
@RequiredArgsConstructor
public class OrderController {

    private final InventoryService inventoryService;
    private final Map<String, PaymentStrategy> paymentStrategies;

    @PostMapping("/purchase")
    public ResponseEntity<String> purchase(@RequestParam String itemId, @RequestParam String paymentType) {
        boolean purchaseResult = inventoryService.purchase(itemId);
        
        if (purchaseResult) {
            PaymentStrategy strategy = paymentStrategies.get(paymentType);
            if (strategy == null) {
                // In a real scenario, we might want to rollback the stock here or handle it better
                return ResponseEntity.badRequest().body("Invalid payment type");
            }
            
            boolean paymentSuccess = strategy.pay(100.0); // Dummy amount
            if (paymentSuccess) {
                return ResponseEntity.ok("Purchase successful with " + paymentType);
            } else {
                return ResponseEntity.status(500).body("Payment failed");
            }
        }
        
        return ResponseEntity.status(409).body("Item out of stock or could not acquire lock");
    }
}
