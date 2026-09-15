package com.gokchu.gokchu.store.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateStoreRequest(
        @NotBlank(message = "매장 이름을 입력해 주세요.") String name,
        String regionCode
) {
}
