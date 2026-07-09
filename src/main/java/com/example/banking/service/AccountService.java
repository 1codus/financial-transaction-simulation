package com.example.banking.service;

import com.example.banking.domain.account.Account;
import com.example.banking.domain.account.AccountRepository;
import com.example.banking.domain.user.User;
import com.example.banking.domain.user.UserRepository;
import com.example.banking.dto.request.AccountCreateRequest;
import com.example.banking.dto.response.AccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    public AccountResponse createAccount(Long userId, AccountCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Account account = Account.builder()
                .user(user)
                .accountNumber(generateAccountNumber())
                .accountType(request.getAccountType())
                .build();

        Account saved = accountRepository.save(account);
        return new AccountResponse(saved);
    }

    public List<AccountResponse> getAccounts(Long userId) {
        return accountRepository.findAll().stream()
                .filter(account -> account.getUser().getId().equals(userId))
                .map(AccountResponse::new)
                .collect(Collectors.toList());
    }

    private String generateAccountNumber() {
        return "110-" + UUID.randomUUID().toString().substring(0, 8);
    }
}