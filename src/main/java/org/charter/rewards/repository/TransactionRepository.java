package org.charter.rewards.repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import org.charter.rewards.model.Customer;
import org.charter.rewards.model.Transaction;
import org.springframework.stereotype.Repository;

/** In-memory purchases seeded with demonstration data; writes and reads share a lock. */
@Repository
public class TransactionRepository {
    private final List<Customer> customers;
    private final List<Transaction> transactions;
    private long nextTransactionId = 10;

    public TransactionRepository(Clock clock) {
        YearMonth currentMonth = YearMonth.now(clock);
        customers = new ArrayList<>(List.of(new Customer(1), new Customer(2), new Customer(3)));
        transactions = new ArrayList<>(List.of(
                purchase(1, 1, currentMonth.minusMonths(2).atDay(1), "120.00"),
                purchase(2, 1, currentMonth.minusMonths(2).atDay(15), "50.00"),
                purchase(3, 1, currentMonth.minusMonths(1).atDay(10), "100.00"),
                purchase(4, 1, currentMonth.atDay(1), "150.75"),
                purchase(5, 2, currentMonth.minusMonths(2).atDay(5), "75.50"),
                purchase(6, 2, currentMonth.minusMonths(1).atDay(20), "200.00"),
                purchase(7, 2, currentMonth.atDay(1), "49.99"),
                purchase(8, 1, currentMonth.minusMonths(3).atEndOfMonth(), "1000.00"),
                purchase(9, 2, currentMonth.plusMonths(1).atDay(1), "1000.00")));
    }

    private static Transaction purchase(long id, long customerId, LocalDate date, String amount) {
        return new Transaction(id, customerId, date, new BigDecimal(amount));
    }

    public synchronized List<Customer> findCustomers() {
        return List.copyOf(customers);
    }

    public synchronized Optional<Customer> findCustomer(long customerId) {
        return customers.stream().filter(customer -> customer.id() == customerId).findFirst();
    }

    public synchronized Transaction save(long customerId, LocalDate date, BigDecimal amount) {
        Transaction transaction = new Transaction(nextTransactionId, customerId, date, amount);
        // Validate the purchase before introducing a customer; both writes share the same lock.
        if (findCustomer(customerId).isEmpty()) {
            customers.add(new Customer(customerId));
        }
        transactions.add(transaction);
        nextTransactionId++;
        return transaction;
    }

    public synchronized List<Transaction> findTransactions(Long customerId, LocalDate startInclusive, LocalDate endExclusive) {
        return transactions.stream()
                .filter(transaction -> customerId == null || transaction.customerId() == customerId)
                .filter(transaction -> !transaction.date().isBefore(startInclusive)
                        && transaction.date().isBefore(endExclusive))
                .toList();
    }
}
