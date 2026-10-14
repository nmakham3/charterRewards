package org.charter.rewards.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/** The server assigns the transaction ID; clients supply only purchase details. */
public record CreateTransactionRequest(
        @NotNull @Positive
        @Schema(description = "Positive customer ID; an unknown ID is added automatically on the first valid purchase", example = "4", minimum = "1") Long customerId,
        @NotNull
        @Schema(description = "Purchase date in YYYY-MM-DD format; determines its reporting month", example = "2026-10-13") LocalDate date,
        @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2)
        @Schema(description = "USD amount, at most 15 integer digits and two decimal places",
                example = "120.00", minimum = "0", maximum = "999999999999999.99", multipleOf = 0.01) BigDecimal amount) { }
