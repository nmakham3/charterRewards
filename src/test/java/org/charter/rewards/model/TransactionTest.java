package org.charter.rewards.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {
    @Test
    void validatesRecordedPurchases() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        assertThrows(NullPointerException.class, () -> new Transaction(1, 1, date, null));
        assertThrows(NullPointerException.class, () -> new Transaction(1, 1, null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(1, 1, date, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(1, 1, date, new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(0, 1, date, BigDecimal.ONE));
        assertDoesNotThrow(() -> new Transaction(1, 1, date, new BigDecimal("120.000")));
    }
}
