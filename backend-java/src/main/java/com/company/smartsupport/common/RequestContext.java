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

    private Optional<HttpServletRequest> request() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return Optional.of(servletRequestAttributes.getRequest());
        }
        return Optional.empty();
    }
}
