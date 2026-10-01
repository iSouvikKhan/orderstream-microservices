package com.orderstream.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Stand-in for an external payment gateway. Deterministic so the saga's failure path can be
 * exercised: charges above {@code payment.provider.max-charge} are declined.
 */
@Component
public class PaymentProvider {

    private final BigDecimal maxCharge;

    public PaymentProvider(@Value("${payment.provider.max-charge:5000.00}") BigDecimal maxCharge) {
        this.maxCharge = maxCharge;
    }

    /** @return a decline reason, or empty if the charge succeeded. */
    public Optional<String> charge(String customerId, BigDecimal amount) {
        if (amount.compareTo(maxCharge) > 0) {
            return Optional.of("amount " + amount + " exceeds limit " + maxCharge);
        }
        return Optional.empty();
    }
}
