package com.company.smartsupport.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final RequestContext requestContext;

    public GlobalExceptionHandler(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @ExceptionHandler(SmartSupportException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(SmartSupportException exception) {
        HttpStatus status = statusOf(exception.code());
        return ResponseEntity.status(status).body(ApiResponse.failure(exception.code(), exception.getMessage(), requestContext.requestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.failure("ICSS-COMMON-400-INVALID_PARAMETER", message, requestContext.requestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("ICSS-SYS-500-INTERNAL_ERROR", exception.getMessage(), requestContext.requestId()));
    }

    private HttpStatus statusOf(String code) {
        if (code.contains("-401-")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code.contains("-403-")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.contains("-404-")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.contains("-409-")) {
            return HttpStatus.CONFLICT;
        }
        if (code.contains("-413-")) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        if (code.contains("-422-")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        if (code.contains("-429-")) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code.contains("-500-")) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (code.contains("-502-")) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (code.contains("-503-")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
