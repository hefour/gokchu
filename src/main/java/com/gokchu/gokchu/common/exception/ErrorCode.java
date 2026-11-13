package com.gokchu.gokchu.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    TABLE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 테이블입니다."),
    INVALID_YOUTUBE_LINK(HttpStatus.BAD_REQUEST, "올바른 유튜브 링크가 아닙니다."),
    VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 영상을 찾을 수 없습니다."),
    VIDEO_NOT_PLAYABLE(HttpStatus.UNPROCESSABLE_CONTENT, "이 영상은 매장에서 재생할 수 없습니다."),
    YOUTUBE_API_ERROR(HttpStatus.BAD_GATEWAY, "유튜브 API 호출에 실패했습니다."),
    QUEUE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 큐 항목입니다."),
    QUEUE_ITEM_NOT_PLAYING(HttpStatus.CONFLICT, "현재 재생 중인 곡이 아닙니다."),
    QUEUE_ITEM_ALREADY_FINISHED(HttpStatus.CONFLICT, "이미 종료된 큐 항목입니다."),
    FALLBACK_TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 폴백 트랙입니다."),
    FALLBACK_NOT_PLAYING(HttpStatus.CONFLICT, "현재 재생 중인 폴백 트랙이 없습니다."),
    FALLBACK_TRACK_ALREADY_FINISHED(HttpStatus.CONFLICT, "이미 다음 곡으로 넘어간 폴백 트랙입니다."),
    INVALID_ADMIN_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED_ADMIN(HttpStatus.UNAUTHORIZED, "관리자 인증이 필요합니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매장입니다."),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    SEARCH_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 검색 가능 횟수를 모두 사용했습니다. 링크로 추가해 주세요.");

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
