package org.charter.rewards.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RewardCalculator {
    public long calculatePoints(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must be nonnegative");
        }
        long dollars = amount.setScale(0, RoundingMode.DOWN).longValueExact();
        long singlePoints = Math.min(Math.max(dollars - 50, 0), 50);
        long doublePoints = Math.multiplyExact(Math.max(dollars - 100, 0), 2);
        return Math.addExact(singlePoints, doublePoints);
    }
}
