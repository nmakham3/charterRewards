package org.charter.rewards.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.charter.rewards.model.Customer;
import org.charter.rewards.model.Transaction;
import org.charter.rewards.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardsServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private final YearMonth start = YearMonth.of(2026, 8);
    private final RewardsService service = new RewardsService(new TransactionRepository(clock), new RewardCalculator(), clock);

    @Test
    void calculatesMonthlyAndTotalPointsAndExcludesOutsideTransactions() {
        var rewards = service.customerRewards(1, start);
        assertEquals(List.of(90L, 50L, 150L), rewards.monthlyRewards().stream().map(month -> month.points()).toList());
        assertEquals(List.of(start, start.plusMonths(1), start.plusMonths(2)),
                rewards.monthlyRewards().stream().map(month -> month.month()).toList());
        assertEquals(290, rewards.totalPoints());
        assertEquals(275, service.customerRewards(2, start).totalPoints());
        assertEquals(7, service.transactions(null, start).size());
        assertEquals(4, service.transactions(1L, start).size());
    }

    @Test
    void returnsThreeZeroMonthsForKnownCustomerWithoutTransactions() {
        var rewards = service.customerRewards(3, start);
        assertEquals(3, rewards.monthlyRewards().size());
        assertTrue(rewards.monthlyRewards().stream().allMatch(month -> month.points() == 0));
        assertEquals(0, rewards.totalPoints());
        assertTrue(service.transactions(3L, start).isEmpty());
        assertEquals(3, service.allRewards(start).size());
    }

    @Test
    void defaultsToCurrentAndPreviousTwoMonthsAndSupportsYearRollover() {
        assertEquals(start, service.reportingStart(null));
        Clock january = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var januaryService = new RewardsService(new TransactionRepository(january), new RewardCalculator(), january);
        assertEquals(YearMonth.of(2025, 11), januaryService.reportingStart(null));
        assertEquals(290, januaryService.customerRewards(1, januaryService.reportingStart(null)).totalPoints());
    }

    @Test
    void queriesOnlyRequestedCustomerAndUsesInclusiveStartExclusiveEnd() {
        var repository = mock(TransactionRepository.class);
        when(repository.findCustomer(7)).thenReturn(java.util.Optional.of(new Customer(7)));
        when(repository.findTransactions(7L, start.atDay(1), start.plusMonths(3).atDay(1)))
                .thenReturn(List.of(new Transaction(1, 7, start.atDay(1), new BigDecimal("120"))));
        var scopedService = new RewardsService(repository, new RewardCalculator(), clock);
        assertEquals(90, scopedService.customerRewards(7, start).totalPoints());
        verify(repository).findTransactions(7L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 1));
        verify(repository, never()).findCustomers();
    }

    @Test
    void reportingPeriodAcrossYearsKeepsTransactionsInTheirCorrectMonths() {
        service.createTransaction(4, LocalDate.of(2025, 11, 1), new BigDecimal("120"));
        service.createTransaction(4, LocalDate.of(2025, 12, 31), new BigDecimal("100"));
        service.createTransaction(4, LocalDate.of(2026, 1, 31), new BigDecimal("75"));
        service.createTransaction(4, LocalDate.of(2026, 2, 1), new BigDecimal("1000"));
        var rewards = service.customerRewards(4, YearMonth.of(2025, 11));
        assertEquals(List.of(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1)),
                rewards.monthlyRewards().stream().map(month -> month.month()).toList());
        assertEquals(List.of(90L, 50L, 25L), rewards.monthlyRewards().stream().map(month -> month.points()).toList());
        assertEquals(165, rewards.totalPoints());
    }

    @Test
    void leapDayBelongsToFebruaryAndEndOfReportingPeriodIsExcluded() {
        service.createTransaction(4, LocalDate.of(2024, 2, 29), new BigDecimal("120"));
        service.createTransaction(4, LocalDate.of(2024, 3, 1), new BigDecimal("1000"));
        var rewards = service.customerRewards(4, YearMonth.of(2023, 12));
        assertEquals(List.of(0L, 0L, 90L), rewards.monthlyRewards().stream().map(month -> month.points()).toList());
        assertEquals(90, rewards.totalPoints());
        assertEquals(1, service.transactions(4L, YearMonth.of(2023, 12)).size());
    }

    @ParameterizedTest(name = "Separate purchases of ${0} and ${1} earn {2} points")
    @CsvSource({"60,60,20", "80,80,60", "50.99,50.99,0", "120,120,180"})
    void calculatesEachPurchaseBeforeAddingMonthlyPoints(String firstAmount, String secondAmount, long expectedPoints) {
        service.createTransaction(4, start.atDay(1), new BigDecimal(firstAmount));
        service.createTransaction(4, start.atDay(2), new BigDecimal(secondAmount));
        var rewards = service.customerRewards(4, start);
        assertEquals(expectedPoints, rewards.monthlyRewards().getFirst().points());
        assertEquals(expectedPoints, rewards.totalPoints());
    }

    @Test
    void reportsUnknownAndInvalidCustomers() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.customerRewards(999, start)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.transactions(0L, start)).getStatusCode().value());
    }
}
