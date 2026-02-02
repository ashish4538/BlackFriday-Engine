package com.example.flashsale.strategy;

import org.springframework.stereotype.Component;

@Component("crypto")
public class CryptoPayment implements PaymentStrategy {
    @Override
    public boolean pay(double amount) {
        System.out.println("Paying " + amount + " using Cryptocurrency.");
        return true;
    }
}
