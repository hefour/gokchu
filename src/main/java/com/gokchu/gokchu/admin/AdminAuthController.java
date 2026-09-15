package com.gokchu.gokchu.admin;

import com.gokchu.gokchu.admin.dto.AdminLoginRequest;
import com.gokchu.gokchu.admin.dto.AdminLoginResponse;
import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminTokenService adminTokenService;
    private final String adminPassword;

    public AdminAuthController(
            AdminTokenService adminTokenService, @Value("${admin.password}") String adminPassword) {
        this.adminTokenService = adminTokenService;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/login")
    public AdminLoginResponse login(@Valid @RequestBody AdminLoginRequest request) {
        if (!constantTimeEquals(adminPassword, request.password())) {
            throw new GokchuException(ErrorCode.INVALID_ADMIN_PASSWORD);
        }
        return new AdminLoginResponse(adminTokenService.issueToken());
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
