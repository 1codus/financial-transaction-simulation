package com.example.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.banking.external.ExternalServiceException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "errorCode", "INVALID_REQUEST",
                        "message", e.getMessage()
                ));
    }
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, String>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "errorCode", "MISSING_HEADER",
                        "message", e.getHeaderName() + " 헤더가 필요합니다."
                ));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, String>> handleCustomException(CustomException e){
        HttpStatus status = switch (e.getErrorCode()){
            case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCOUNT_FROZEN, ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INSUFFICIENT_BALANCE, INVALID_AMOUNT,SAME_ACCOUNT_TRANSFER -> HttpStatus.BAD_REQUEST;
            case CONCURRENT_UPDATE_CONFLICT, DUPLICATE_REQUEST -> HttpStatus.CONFLICT;
        };

        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "errorCode", e.getErrorCode().getCode(),
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, String>> handleExternalServiceException(ExternalServiceException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("errorCode", "EXTERNAL_SERVICE_UNAVAILABLE", "message", e.getMessage()));
    }
}