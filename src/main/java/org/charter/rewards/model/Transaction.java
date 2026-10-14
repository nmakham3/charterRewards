package org.charter.rewards.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record Transaction(@Schema(example = "1", minimum = "1") long id,
                          @Schema(example = "1", minimum = "1") long customerId,
                          @Schema(description = "Purchase date", example = "2026-08-01") LocalDate date,
                          @Schema(description = "Purchase amount in USD", example = "120.00", minimum = "0", multipleOf = 0.01) BigDecimal amount) {
    public Transaction {
        Objects.requireNonNull(date, "Transaction date is required");
        Objects.requireNonNull(amount, "Transaction amount is required");
        if (id <= 0 || customerId <= 0) {
            throw new IllegalArgumentException("Transaction and customer IDs must be positive");
        }
        if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount must be nonnegative with at most two decimal places");
        }
    }
}
