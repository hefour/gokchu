package com.gokchu.gokchu.queue.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 관리자가 건너뛸 폴백 트랙. 관리자 화면에 보이던 now-playing의 videoId를 보낸다. 그 사이 곡이 바뀌었으면
 * 새 곡을 건너뛰지 않도록 서버가 이 값으로 대상을 확인한다.
 */
public record SkipFallbackRequest(@NotBlank(message = "건너뛸 폴백 트랙의 videoId가 필요합니다.") String videoId) {
}
