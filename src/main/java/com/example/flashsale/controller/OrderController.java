package com.example.flashsale.controller;

import com.example.flashsale.model.Order;
import com.example.flashsale.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrderController {
    private final InventoryService inventory;

    public OrderController(InventoryService inventory) { this.inventory = inventory; }

    @PostMapping({"/purchase", "/api/orders"})
    public ResponseEntity<Order> purchase(@RequestParam String itemId, Authentication auth) {
        // Order acceptance does not represent a payment or fulfilment confirmation.
        return ResponseEntity.accepted().body(inventory.purchase(auth.getName(), itemId));
    }

    @GetMapping("/api/inventory/stock/{itemId}")
    public long stock(@PathVariable String itemId) { return inventory.getStock(itemId); }

    @PostMapping("/api/inventory/{itemId}/initialize")
    public ResponseEntity<Void> initialize(@PathVariable String itemId, @RequestParam long stock) {
        if (!inventory.initialize(itemId, stock)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory already initialized");
        }
        return ResponseEntity.status(201).build();
    }
}
