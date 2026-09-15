package com.gokchu.gokchu.admin;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminTokenService adminTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith(BEARER_PREFIX))
                ? header.substring(BEARER_PREFIX.length())
                : null;

        if (!adminTokenService.isValid(token)) {
            throw new GokchuException(ErrorCode.UNAUTHORIZED_ADMIN);
        }
        return true;
    }
}
