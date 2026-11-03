package com.smartinsurance.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import com.smartinsurance.security.CorrelationIdFilter;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<Map<String, Object>> conflict(BusinessRuleException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "BUSINESS_RULE", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, e -> e.getDefaultMessage() == null ? "Invalid value" : e.getDefaultMessage(),
                        (first, ignored) -> first));
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "status", 400,
                "code", "VALIDATION_ERROR", "message", "Request validation failed", "fields", fields,
                "path", req.getRequestURI(), "correlationId", correlationId(req)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> constraint(ConstraintViolationException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> accessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", reason(ex, "Access is not permitted"), req);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> authentication(AuthenticationException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", reason(ex, "Authentication failed"), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest req) {
        String correlationId = correlationId(req);
        LOG.error("Unhandled request exception (correlationId={})", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "timestamp", Instant.now(), "status", 500, "code", "INTERNAL_SERVER_ERROR",
                "message", "An unexpected error occurred", "path", req.getRequestURI(),
                "correlationId", correlationId));
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message,
                                                       HttpServletRequest req) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(), "status", status.value(),
                "code", code, "message", message, "path", req.getRequestURI(),
                "correlationId", correlationId(req)));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    private String reason(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }
}
