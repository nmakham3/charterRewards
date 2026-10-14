package org.charter.rewards.service;

import java.time.Clock;
import java.time.YearMonth;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.charter.rewards.model.Customer;
import org.charter.rewards.model.CustomerRewards;
import org.charter.rewards.model.CustomerRewards.MonthlyRewards;
import org.charter.rewards.model.Transaction;
import org.charter.rewards.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RewardsService {
    private final TransactionRepository repository;
    private final RewardCalculator calculator;
    private final Clock clock;

    public RewardsService(TransactionRepository repository, RewardCalculator calculator, Clock clock) {
        this.repository = repository;
        this.calculator = calculator;
        this.clock = clock;
    }

    public YearMonth reportingStart(YearMonth startMonth) {
        YearMonth start = startMonth == null ? YearMonth.now(clock).minusMonths(2) : startMonth;
        if (start.getYear() < 1 || start.getYear() > 9998) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startMonth year must be between 0001 and 9998");
        }
        return start;
    }

    public List<CustomerRewards> allRewards(YearMonth start) {
        return repository.findCustomers().stream().map(customer -> rewards(customer, start)).toList();
    }

    public Transaction createTransaction(long customerId, LocalDate date, BigDecimal amount) {
        if (customerId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId must be positive");
        }
        if (date.getYear() < 1 || date.getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction date year must be between 0001 and 9999");
        }
        return repository.save(customerId, date, amount);
    }

    public CustomerRewards customerRewards(long customerId, YearMonth start) {
        return rewards(requireCustomer(customerId), start);
    }

    public List<Transaction> transactions(Long customerId, YearMonth start) {
        if (customerId != null) {
            requireCustomer(customerId);
        }
        return repository.findTransactions(customerId, start.atDay(1), start.plusMonths(3).atDay(1));
    }

    private Customer requireCustomer(long customerId) {
        if (customerId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId must be positive");
        }
        return repository.findCustomer(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId));
    }

    private CustomerRewards rewards(Customer customer, YearMonth start) {
        List<Transaction> transactions = transactions(customer.id(), start);
        List<MonthlyRewards> months = IntStream.range(0, 3).mapToObj(offset -> {
            YearMonth month = start.plusMonths(offset);
            long points = transactions.stream()
                    .filter(transaction -> YearMonth.from(transaction.date()).equals(month))
                    .mapToLong(transaction -> calculator.calculatePoints(transaction.amount())).sum();
            return new MonthlyRewards(month, points);
        }).toList();
        return new CustomerRewards(customer.id(), months,
                months.stream().mapToLong(MonthlyRewards::points).sum());
    }
}
