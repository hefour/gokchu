package com.gokchu.gokchu.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    TABLE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 테이블입니다."),
    INVALID_YOUTUBE_LINK(HttpStatus.BAD_REQUEST, "올바른 유튜브 링크가 아닙니다."),
    VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 영상을 찾을 수 없습니다."),
    VIDEO_NOT_PLAYABLE(HttpStatus.UNPROCESSABLE_CONTENT, "이 영상은 매장에서 재생할 수 없습니다."),
    YOUTUBE_API_ERROR(HttpStatus.BAD_GATEWAY, "유튜브 API 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
