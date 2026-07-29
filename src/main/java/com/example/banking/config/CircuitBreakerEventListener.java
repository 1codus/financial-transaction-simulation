package com.example.banking.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerEventListener {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerListener() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("fraudCheck");
        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("[서킷브레이커] 상태 전이: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()));
    }
}