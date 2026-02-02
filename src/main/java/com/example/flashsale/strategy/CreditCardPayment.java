package com.example.flashsale.strategy;

import org.springframework.stereotype.Component;

@Component("creditCard")
public class CreditCardPayment implements PaymentStrategy {
    @Override
    public boolean pay(double amount) {
        System.out.println("Paying " + amount + " using Credit Card.");
        return true;
    }
}
