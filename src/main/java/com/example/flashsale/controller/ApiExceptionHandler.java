package com.example.flashsale.controller;

import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final MeterRegistry metrics;

    public ApiExceptionHandler(MeterRegistry metrics) { this.metrics = metrics; }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<?> invalidRequest(Exception error) {
        // Do not log rejected request values: registration includes plaintext passwords.
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(Map.of("error", "Conflicting request; retry to retrieve an existing order"));
    }

    @ExceptionHandler({DataAccessException.class, RedisException.class})
    ResponseEntity<?> unavailable(RuntimeException error) {
        metrics.counter("purchase.storage.failures").increment();
        log.warn("Storage operation failed ({})", error.getClass().getSimpleName());
        return ResponseEntity.status(503).header("Retry-After", "1")
                .body(Map.of("error", "Service unavailable; retry the same request"));
    }
}
