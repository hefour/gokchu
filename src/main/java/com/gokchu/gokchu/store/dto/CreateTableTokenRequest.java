package com.gokchu.gokchu.store.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTableTokenRequest(@NotBlank(message = "테이블 이름을 입력해 주세요.") String label) {
}
