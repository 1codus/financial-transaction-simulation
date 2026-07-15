package com.example.banking.dto.response;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class TransferResponse {
    private Long transactionId;
    private String status;
    private BigDecimal fromBalance;
    private BigDecimal toBalance;

    public TransferResponse(Long transactionId, String status, BigDecimal fromBalance, BigDecimal toBalance){
        this.transactionId = transactionId;
        this.status = status;
        this.fromBalance = fromBalance;
        this.toBalance = toBalance;    }

}
