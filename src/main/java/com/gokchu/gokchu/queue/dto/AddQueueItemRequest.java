package com.gokchu.gokchu.queue.dto;

import jakarta.validation.constraints.NotBlank;

public record AddQueueItemRequest(@NotBlank(message = "유튜브 링크를 입력해 주세요.") String link) {
}
