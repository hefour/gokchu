package com.gokchu.gokchu.admin;

import com.gokchu.gokchu.admin.dto.AdminLoginRequest;
import com.gokchu.gokchu.admin.dto.AdminLoginResponse;
import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthControllerTest {

    private final AdminTokenService tokenService = new AdminTokenService("secret", 3600, Clock.systemUTC());

    @Mock
    private AdminLoginRateLimiter rateLimiter;

    @Mock
    private HttpServletRequest httpRequest;

    private AdminAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAuthController(tokenService, rateLimiter, "correct-password");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.1");
    }

    @Test
    void login_올바른_비밀번호면_유효한_토큰을_반환한다() {
        AdminLoginResponse response = controller.login(new AdminLoginRequest("correct-password"), httpRequest);

        assertThat(tokenService.isValid(response.token())).isTrue();
        verify(rateLimiter).recordSuccess("203.0.113.1");
    }

    @Test
    void login_틀린_비밀번호면_예외가_발생하고_실패를_기록한다() {
        assertThatThrownBy(() -> controller.login(new AdminLoginRequest("wrong-password"), httpRequest))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ADMIN_PASSWORD);

        verify(rateLimiter).recordFailure("203.0.113.1");
    }

    @Test
    void login_차단된_IP면_비밀번호_확인_없이_예외가_발생한다() {
        when(rateLimiter.isBlocked("203.0.113.1")).thenReturn(true);

        assertThatThrownBy(() -> controller.login(new AdminLoginRequest("correct-password"), httpRequest))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
    }

    @Test
    void login_XForwardedFor_헤더가_있으면_그_IP를_사용한다() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("198.51.100.7, 10.0.0.1");

        controller.login(new AdminLoginRequest("correct-password"), httpRequest);

        verify(rateLimiter).recordSuccess("198.51.100.7");
    }
}
