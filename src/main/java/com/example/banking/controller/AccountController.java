package com.example.banking.controller;

import com.example.banking.dto.request.AccountCreateRequest;
import com.example.banking.dto.response.AccountResponse;
import com.example.banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(
            @RequestParam Long userId,
            @RequestBody AccountCreateRequest request) {
        return accountService.createAccount(userId, request);
    }

    @GetMapping
    public List<AccountResponse> getAccounts(@RequestParam Long userId) {
        return accountService.getAccounts(userId);
    }
}