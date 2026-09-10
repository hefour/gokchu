package com.gokchu.gokchu.common.exception;

import lombok.Getter;

@Getter
public class GokchuException extends RuntimeException {

    private final ErrorCode errorCode;

    public GokchuException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
