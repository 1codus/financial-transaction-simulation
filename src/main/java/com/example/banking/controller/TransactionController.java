package com.example.banking.controller;

import com.example.banking.domain.transaction.Transaction;
import com.example.banking.dto.request.TransferRequest;
import com.example.banking.dto.response.TransferResponse;
import com.example.banking.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public TransferResponse transfer(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TransferRequest request){
        return transactionService.transfer(userId, request, idempotencyKey);
    }
}
