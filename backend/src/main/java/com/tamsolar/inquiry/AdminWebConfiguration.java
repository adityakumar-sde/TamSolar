package com.tamsolar.inquiry;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfiguration implements WebMvcConfigurer {

    private final AdminTokenInterceptor adminTokenInterceptor;

    public AdminWebConfiguration(
            AdminTokenInterceptor adminTokenInterceptor) {
        this.adminTokenInterceptor = adminTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/api/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Restrict CORS strictly to local development and trusted LAN clients (no wildcard '*')
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "http://localhost:[*]",
                    "http://127.0.0.1:[*]",
                    "http://192.168.*:[*]"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
