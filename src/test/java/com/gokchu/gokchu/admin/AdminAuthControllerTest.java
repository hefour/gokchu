package com.gokchu.gokchu.admin;

import com.gokchu.gokchu.admin.dto.AdminLoginRequest;
import com.gokchu.gokchu.admin.dto.AdminLoginResponse;
import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthControllerTest {

    private final AdminTokenService tokenService = new AdminTokenService("secret", 3600, Clock.systemUTC());
    private final AdminAuthController controller = new AdminAuthController(tokenService, "correct-password");

    @Test
    void login_올바른_비밀번호면_유효한_토큰을_반환한다() {
        AdminLoginResponse response = controller.login(new AdminLoginRequest("correct-password"));

        assertThat(tokenService.isValid(response.token())).isTrue();
    }

    @Test
    void login_틀린_비밀번호면_예외가_발생한다() {
        assertThatThrownBy(() -> controller.login(new AdminLoginRequest("wrong-password")))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ADMIN_PASSWORD);
    }
}
