package com.gokchu.gokchu.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
}
