package org.charter.rewards.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class RewardCalculatorTest {
    private final RewardCalculator calculator = new RewardCalculator();

    @ParameterizedTest(name = "${0} earns {1} points")
    @CsvSource({"0,0", "49.99,0", "50,0", "50.99,0", "51,1", "75.50,25",
            "99.99,49", "100,50", "100.99,50", "101,52", "120,90", "150.75,150", "200,250"})
    void awardsWholeDollarPointsAtEveryThreshold(String amount, long expectedPoints) {
        assertEquals(expectedPoints, calculator.calculatePoints(new BigDecimal(amount)));
    }

    @Test
    void rejectsMissingAndNegativeAmounts() {
        assertThrows(NullPointerException.class, () -> calculator.calculatePoints(null));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculatePoints(new BigDecimal("-1")));
    }
}
