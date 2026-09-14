package com.gokchu.gokchu.fallback.dto;

import jakarta.validation.constraints.NotBlank;

public record AddFallbackTrackRequest(@NotBlank(message = "유튜브 링크를 입력해 주세요.") String link) {
}
