package org.charter.rewards.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.List;

public record CustomerRewards(@Schema(example = "1", minimum = "1") long customerId,
                              @Schema(description = "Exactly three chronological months, including months with no rewards")
                              List<MonthlyRewards> monthlyRewards,
                              @Schema(description = "Sum of the three monthly point totals", example = "290", minimum = "0") long totalPoints) {
    public CustomerRewards {
        monthlyRewards = List.copyOf(monthlyRewards);
    }

    public record MonthlyRewards(@Schema(type = "string", example = "2026-08", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$") YearMonth month,
                                 @Schema(example = "90", minimum = "0") long points) { }
}
