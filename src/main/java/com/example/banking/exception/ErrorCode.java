package com.example.banking.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    ACCOUNT_NOT_FOUND("ACCOUNT_NOT_FOUND", "존재하지 않는 계좌입니다."),
    ACCOUNT_FROZEN("ACCOUNT_FROZEN", "동결되었거나 정지된 계좌입니다."),
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "잔액이 부족합니다."),
    INVALID_AMOUNT("INVALID_AMOUNT", "유효하지 않은 금액입니다."),
    ACCESS_DENIED("ACCESS_DENIED", "본인 소유의 계좌가 아닙니다."),
    SAME_ACCOUNT_TRANSFER("SAME_ACCOUNT_TRANSFER", "동일 계좌로는 이체할 수 없습니다."),
    CONCURRENT_UPDATE_CONFLICT("CONCURRENT_UPDATE_CONFLICT", "동시 요청이 몰려 처리에 실패했습니다. 잠시 후 다시 시도해주세요.");


    private final String code;
    private final String message;


    ErrorCode(String code, String message){
        this.code = code;
        this.message = message;
    }


}
