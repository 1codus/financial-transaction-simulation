package com.example.banking.concurrency;

import com.example.banking.domain.account.Account;
import com.example.banking.domain.account.AccountRepository;
import com.example.banking.domain.user.User;
import com.example.banking.domain.user.UserRepository;
import com.example.banking.dto.request.TransferRequest;
import com.example.banking.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.TestPropertySource;
import com.example.banking.dto.response.TransferResponse;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:banking_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=60",
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0LW9ubHktZG8tbm90LXVzZS1pbi1wcm9k"
})
class TransferConcurrencyTest {

    @Autowired private TransactionService transactionService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000000");
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("1000");
    private static final int THREAD_COUNT = 1000;
    private static final int POOL_SIZE = 64;

    private Long userId;
    private Long fromAccountId;
    private Long toAccountId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("tester+" + System.nanoTime() + "@test.com")
                .passwordHash("dummy")
                .name("tester")
                .role(User.Role.USER)
                .build());
        userId = user.getId();

        Account from = accountRepository.save(Account.builder()
                .user(user).accountNumber("FROM-" + System.nanoTime()).accountType("CHECKING").build());
        Account to = accountRepository.save(Account.builder()
                .user(user).accountNumber("TO-" + System.nanoTime()).accountType("CHECKING").build());

        fromAccountId = from.getId();
        toAccountId = to.getId();
        setBalance(fromAccountId, INITIAL_BALANCE);
    }

    @Test
    @DisplayName("[Before] 락 없이 read-modify-write 하면 동시 요청에서 잔액이 꼬인다")
    void withoutLock_causesLostUpdate() throws InterruptedException {
        long elapsed = runConcurrently(THREAD_COUNT, () -> unsafeWithdraw(fromAccountId, TRANSFER_AMOUNT));

        BigDecimal expected = INITIAL_BALANCE.subtract(TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(THREAD_COUNT)));
        BigDecimal actual = getBalance(fromAccountId);

        System.out.printf("[락 없음/Before] 소요시간=%dms, 기대잔액=%s, 실제잔액=%s, 유실된 금액=%s%n",
                elapsed, expected, actual, actual.subtract(expected));

        assertThat(actual).isNotEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("[After-비관적 락] 1000건 동시 이체에도 잔액이 정확히 맞는다")
    void withPessimisticLock_keepsBalanceConsistent() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        long elapsed = runConcurrently(THREAD_COUNT, () -> {
            try {
                transactionService.transfer(userId, transferRequest(), java.util.UUID.randomUUID().toString());                success.incrementAndGet();
            } catch (Exception e) {
                fail.incrementAndGet();
            }
        });

        BigDecimal expected = INITIAL_BALANCE.subtract(TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(success.get())));
        BigDecimal actual = getBalance(fromAccountId);

        System.out.printf("[비관적 락/After] 소요시간=%dms, 성공=%d, 실패=%d, 기대잔액=%s, 실제잔액=%s%n",
                elapsed, success.get(), fail.get(), expected, actual);

        assertThat(actual).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("[After-낙관적 락+재시도] 1000건 동시 이체에도 잔액이 정확히 맞는다")
    void withOptimisticLockAndRetry_keepsBalanceConsistent() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        long elapsed = runConcurrently(THREAD_COUNT, () -> {
            try {
                transactionService.transferWithOptimisticLock(userId, transferRequest(), java.util.UUID.randomUUID().toString());                success.incrementAndGet();
            } catch (Exception e) {
                fail.incrementAndGet();
            }
        });

        BigDecimal expected = INITIAL_BALANCE.subtract(TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(success.get())));
        BigDecimal actual = getBalance(fromAccountId);

        System.out.printf("[낙관적 락+재시도/After] 소요시간=%dms, 성공=%d, 실패=%d, 기대잔액=%s, 실제잔액=%s%n",
                elapsed, success.get(), fail.get(), expected, actual);

        assertThat(actual).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("[Idempotency] 같은 키로 순차적으로 두 번 요청해도 이체는 한 번만 발생한다")
    void sameIdempotencyKey_sequentialRetry_returnsExistingResult() {
        String idempotencyKey = java.util.UUID.randomUUID().toString();

        TransferResponse first = transactionService.transfer(userId, transferRequest(), idempotencyKey);
        TransferResponse second = transactionService.transfer(userId, transferRequest(), idempotencyKey);

        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(second.getFromBalance()).isEqualByComparingTo(first.getFromBalance());

        BigDecimal expected = INITIAL_BALANCE.subtract(TRANSFER_AMOUNT);
        BigDecimal actual = getBalance(fromAccountId);
        assertThat(actual).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("[Idempotency] 같은 키로 동시에 여러 번 요청해도 이체는 딱 한 번만 발생한다")
    void sameIdempotencyKey_concurrentRequests_onlyTransfersOnce() throws InterruptedException {
        String idempotencyKey = java.util.UUID.randomUUID().toString();
        int requestCount = 50;

        long elapsed = runConcurrently(requestCount, () -> {
            try {
                transactionService.transfer(userId, transferRequest(), idempotencyKey);
            } catch (Exception ignored) {
            }
        });

        BigDecimal expected = INITIAL_BALANCE.subtract(TRANSFER_AMOUNT);
        BigDecimal actual = getBalance(fromAccountId);

        System.out.printf("[동일 Idempotency-Key 동시 요청] 소요시간=%dms, 요청수=%d, 기대잔액=%s, 실제잔액=%s%n",
                elapsed, requestCount, expected, actual);

        assertThat(actual).isEqualByComparingTo(expected);
    }

    private TransferRequest transferRequest() {
        return new TransferRequest(fromAccountId, toAccountId, TRANSFER_AMOUNT);
    }

    /** @Version(낙관적 락)조차 없던 시절의 코드를 흉내낸, 잠금이 전혀 없는 raw JDBC 출금. 데모/재현 전용. */
    private void unsafeWithdraw(Long accountId, BigDecimal amount) {
        BigDecimal current = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
        try {
            Thread.sleep(1);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        jdbcTemplate.update("UPDATE accounts SET balance = ? WHERE id = ?", current.subtract(amount), accountId);
    }

    private void setBalance(Long accountId, BigDecimal balance) {
        jdbcTemplate.update("UPDATE accounts SET balance = ? WHERE id = ?", balance, accountId);
    }

    private BigDecimal getBalance(Long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private long runConcurrently(int count, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
        CountDownLatch doneLatch = new CountDownLatch(count);

        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                try {
                    task.run();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        doneLatch.await();
        long elapsed = System.currentTimeMillis() - start;
        executor.shutdown();
        return elapsed;
    }
}