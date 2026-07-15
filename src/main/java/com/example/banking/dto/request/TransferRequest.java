package com.example.banking.dto.request;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class TransferRequest {
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
}
