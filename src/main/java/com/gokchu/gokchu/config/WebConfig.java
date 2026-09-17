package com.gokchu.gokchu.config;

import com.gokchu.gokchu.admin.AdminAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final List<String> allowedOrigins;

    public WebConfig(
            AdminAuthInterceptor adminAuthInterceptor,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns(
                        "/api/tables/*/queue-items/*/skip",
                        "/api/tables/*/fallback-tracks",
                        "/api/tables/*/fallback-tracks/*",
                        "/api/tables/*/volume/update",
                        "/api/stores",
                        "/api/stores/*/tables"
                );
    }
}
