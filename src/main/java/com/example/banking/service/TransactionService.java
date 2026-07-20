package com.example.banking.service;

import com.example.banking.domain.account.Account;
import com.example.banking.domain.account.AccountRepository;
import com.example.banking.domain.transaction.Transaction;
import com.example.banking.domain.transaction.TransactionRepository;
import com.example.banking.dto.request.TransferRequest;
import com.example.banking.dto.response.TransferResponse;
import com.example.banking.exception.CustomException;
import com.example.banking.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

@Slf4j
@Service
public class TransactionService {

    private static final int MAX_OPTIMISTIC_RETRY = 5;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }


    @Transactional
    public TransferResponse transfer(Long userId, TransferRequest request) {
        validateAmount(request.getAmount());

        Long fromId = request.getFromAccountId();
        Long toId = request.getToAccountId();

        if (fromId.equals(toId)) {
            throw new CustomException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }

        Long firstLockId = Math.min(fromId, toId);
        Long secondLockId = Math.max(fromId, toId);

        Account first = accountRepository.findByIdWithLock(firstLockId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
        Account second = accountRepository.findByIdWithLock(secondLockId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));

        Account fromAccount = fromId.equals(firstLockId) ? first : second;
        Account toAccount = fromId.equals(firstLockId) ? second : first;

        return doTransfer(userId, fromAccount, toAccount, request.getAmount());
    }

    public TransferResponse transferWithOptimisticLock(Long userId, TransferRequest request) {
        validateAmount(request.getAmount());

        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new CustomException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }

        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRY; attempt++) {
            try {
                int currentAttempt = attempt;
                return transactionTemplate.execute(status ->
                        doTransferOptimistic(userId, request, currentAttempt));
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("낙관적 락 충돌 발생 (attempt={}/{}) - fromAccountId={}, toAccountId={}",
                        attempt, MAX_OPTIMISTIC_RETRY, request.getFromAccountId(), request.getToAccountId());
                if (attempt == MAX_OPTIMISTIC_RETRY) {
                    throw new CustomException(ErrorCode.CONCURRENT_UPDATE_CONFLICT);
                }
                backoff(attempt);
            }
        }
        throw new CustomException(ErrorCode.CONCURRENT_UPDATE_CONFLICT);
    }

    private TransferResponse doTransferOptimistic(Long userId, TransferRequest request, int attempt) {
        Account fromAccount = accountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
        Account toAccount = accountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));

        TransferResponse response = doTransfer(userId, fromAccount, toAccount, request.getAmount());

        accountRepository.flush();

        if (attempt > 1) {
            log.info("낙관적 락 재시도 성공 (attempt={})", attempt);
        }
        return response;
    }

    private TransferResponse doTransfer(Long userId, Account fromAccount, Account toAccount, BigDecimal amount) {
        if (!fromAccount.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (fromAccount.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new CustomException(ErrorCode.ACCOUNT_FROZEN);
        }

        try {
            fromAccount.withdraw(amount);
        } catch (IllegalStateException e) {
            throw new CustomException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        toAccount.deposit(amount);

        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(amount)
                .idempotencyKey(java.util.UUID.randomUUID().toString())
                .build();
        transaction.markSuccess();
        Transaction saved = transactionRepository.save(transaction);

        return new TransferResponse(
                saved.getId(), saved.getStatus().name(),
                fromAccount.getBalance(), toAccount.getBalance()
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_AMOUNT);
        }
    }

    private void backoff(int attempt) {
        try {
            long base = 10L * attempt;
            long jitter = (long) (Math.random() * 10);
            Thread.sleep(base + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}