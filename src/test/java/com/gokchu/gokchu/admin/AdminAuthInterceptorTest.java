package com.gokchu.gokchu.admin;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private AdminTokenService adminTokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AdminAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(adminTokenService);
    }

    @Test
    void preHandle_유효한_토큰이면_통과한다() {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(adminTokenService.isValid("valid-token")).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void preHandle_Authorization_헤더가_없으면_예외가_발생한다() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(adminTokenService.isValid(null)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_ADMIN);
    }

    @Test
    void preHandle_유효하지_않은_토큰이면_예외가_발생한다() {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(adminTokenService.isValid("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_ADMIN);
    }
}
