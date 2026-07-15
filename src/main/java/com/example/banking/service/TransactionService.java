package com.example.banking.service;

import com.example.banking.domain.account.Account;
import com.example.banking.domain.account.AccountRepository;
import com.example.banking.domain.transaction.Transaction;
import com.example.banking.domain.transaction.TransactionRepository;
import com.example.banking.dto.request.TransferRequest;
import com.example.banking.dto.response.TransferResponse;
import com.example.banking.exception.CustomException;
import com.example.banking.exception.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransferResponse transfer(Long userId, TransferRequest request){
        if(request.getAmount().compareTo(BigDecimal.ZERO)<=0){
            throw new CustomException(ErrorCode.INVALID_AMOUNT);
        }

        Account fromAccount = accountRepository.findById(request.getFromAccountId())
                .orElseThrow(()-> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));

        Account toAccount = accountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));

        if(!fromAccount.getUser().getId().equals(userId)){
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        if(fromAccount.getStatus() != Account.AccountStatus.ACTIVE){
            throw new CustomException(ErrorCode.ACCOUNT_FROZEN);
        }

        try{
            fromAccount.withdraw(request.getAmount());
        } catch (IllegalStateException e){
            throw new CustomException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        toAccount.deposit(request.getAmount());

        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.getAmount())
                .idempotencyKey(java.util.UUID.randomUUID().toString())
                .build();

        transaction.markSuccess();
        Transaction saved = transactionRepository.save(transaction);

        return new TransferResponse(
                saved.getId(),
                saved.getStatus().name(),
                fromAccount.getBalance(),
                toAccount.getBalance()
        );

    }

}

