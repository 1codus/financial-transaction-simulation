package com.example.banking.dto.response;

import com.example.banking.domain.account.Account;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private String accountType;
    private BigDecimal balance;
    private String status;

    public AccountResponse(Account account) {
        this.id = account.getId();
        this.accountNumber = account.getAccountNumber();
        this.accountType = account.getAccountType();
        this.balance = account.getBalance();
        this.status = account.getStatus().name();
    }
}