package com.example.banking.external;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;


@Slf4j
@Component
public class ExternalFraudCheckService {

    private static final Random random = new Random();

    // 테스트/데모용 스위치: true로 켜면 무조건 실패
    private volatile boolean forceFail = false;

    public void setForceFail(boolean forceFail) {
        this.forceFail = forceFail;
    }

    @CircuitBreaker(name = "fraudCheck", fallbackMethod = "fraudCheckFallback")
    public String checkFraud(Long accountId, BigDecimal amount) {
        log.info("[외부 API] 사기 탐지 요청 - accountId={}, amount={}", accountId, amount);

        if (forceFail) {
            log.warn("[외부 API] 강제 실패 모드 - 즉시 예외 발생");
            throw new ExternalServiceException("사기 탐지 서비스 강제 실패(테스트 모드)");
        }

        int delay = 100 + random.nextInt(1900); // 100~2000ms
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (random.nextDouble() < 0.3) { // 30% 확률로 실패
            log.warn("[외부 API] 사기 탐지 서비스 응답 실패 (지연={}ms)", delay);
            throw new ExternalServiceException("사기 탐지 서비스 응답 실패");
        }

        log.info("[외부 API] 사기 탐지 응답 성공 (지연={}ms)", delay);
        return "OK";
    }

    public String fraudCheckFallback(Long accountId, BigDecimal amount, Throwable t) {
        log.error("[서킷브레이커] fraudCheck 폴백 실행 - accountId={}, 원인={}", accountId, t.toString());
        throw new ExternalServiceException("사기 탐지 서비스 일시 장애로 이체를 처리할 수 없습니다.");
    }
}