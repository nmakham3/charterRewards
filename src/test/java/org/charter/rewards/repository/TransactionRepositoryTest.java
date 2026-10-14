package org.charter.rewards.repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionRepositoryTest {
    @Test
    void concurrentPurchasesHaveUniqueIdsAndNoneAreLost() {
        var repository = new TransactionRepository(Clock.systemUTC());
        LocalDate date = LocalDate.of(2020, 1, 1);
        var writes = IntStream.range(0, 100)
                .mapToObj(index -> CompletableFuture.supplyAsync(() -> repository.save(999, date, new BigDecimal("120"))))
                .toList();
        var transactions = writes.stream().map(CompletableFuture::join).toList();
        assertEquals(100, transactions.stream().map(transaction -> transaction.id()).distinct().count());
        assertEquals(100, repository.findTransactions(999L, date, date.plusMonths(3)).size());
        assertEquals(1, repository.findCustomers().stream().filter(customer -> customer.id() == 999).count());
        assertEquals(4, repository.findCustomers().size());
        assertEquals(1, repository.findCustomer(1).orElseThrow().id());
    }

    @Test
    void invalidPurchaseDoesNotAddCustomerOrConsumeTransactionId() {
        var repository = new TransactionRepository(Clock.systemUTC());
        LocalDate date = LocalDate.of(2020, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> repository.save(999, date, new BigDecimal("-1")));
        assertTrue(repository.findCustomer(999).isEmpty());
        assertEquals(10, repository.save(999, date, new BigDecimal("120")).id());
    }
}
