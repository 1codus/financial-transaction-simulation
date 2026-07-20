package com.example.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, String>> handleCustomException(CustomException e){
        HttpStatus status = switch (e.getErrorCode()){
            case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCOUNT_FROZEN, ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INSUFFICIENT_BALANCE, INVALID_AMOUNT,SAME_ACCOUNT_TRANSFER -> HttpStatus.BAD_REQUEST;
            case CONCURRENT_UPDATE_CONFLICT -> HttpStatus.CONFLICT;
        };

        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "errorCode", e.getErrorCode().getCode(),
                        "message", e.getMessage()
                ));
    }
}