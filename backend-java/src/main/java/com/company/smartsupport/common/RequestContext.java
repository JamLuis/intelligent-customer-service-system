package com.company.smartsupport.common;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RequestContext {

    public String requestId() {
        return request().map(req -> req.getHeader("X-Request-Id"))
                .filter(StringUtils::hasText)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    public String requireProjectId() {
        return request().map(req -> req.getHeader("X-Project-Id"))
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new SmartSupportException("ICSS-AUTH-403-PROJECT_DENIED", "缺少项目上下文或无项目权限"));
    }

    public void requireIdempotencyKey() {
        boolean present = request().map(req -> req.getHeader("X-Idempotency-Key"))
                .filter(StringUtils::hasText)
                .isPresent();
        if (!present) {
            throw new SmartSupportException("ICSS-COMMON-400-INVALID_PARAMETER", "写接口必须提供 X-Idempotency-Key");
        }
    }

    public void requirePermission(String permission, String errorCode, String message) {
        boolean allowed = request()
                .map(req -> req.getHeader("X-Permissions"))
                .filter(StringUtils::hasText)
                .map(header -> java.util.Arrays.stream(header.split(","))
                        .map(String::trim)
                        .anyMatch(item -> item.equals(permission) || item.equals("*")))
                .orElse(false);
        if (!allowed) {
            throw new SmartSupportException(errorCode, message);
        }
    }

    public String actorId() {
        return request().map(req -> req.getHeader("X-User-Id"))
                .filter(StringUtils::hasText)
                .orElse("system");
    }

    private Optional<HttpServletRequest> request() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return Optional.of(servletRequestAttributes.getRequest());
        }
        return Optional.empty();
    }
}
