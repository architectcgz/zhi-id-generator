package com.platform.idgen.interfaces.rest.advice;

import com.platform.idgen.domain.exception.ClockBackwardsException;
import com.platform.idgen.domain.exception.IdGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for REST API.
 * 
 * Provides consistent error responses for all exceptions.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle IdGenerationException
     */
    @ExceptionHandler(IdGenerationException.class)
    public ResponseEntity<Map<String, Object>> handleIdGenerationException(IdGenerationException e) {
        log.error("ID generation error: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", e.getErrorCode().getCode());
        response.put("message", e.getMessage());
        response.put("errorCode", e.getErrorCode().name());
        
        HttpStatus status = switch (e.getErrorCode()) {
            case BIZ_TAG_NOT_EXISTS -> HttpStatus.NOT_FOUND;
            case CACHE_NOT_INITIALIZED, SEGMENTS_NOT_READY, WORKER_ID_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case CLOCK_BACKWARDS -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Handle ClockBackwardsException
     */
    @ExceptionHandler(ClockBackwardsException.class)
    public ResponseEntity<Map<String, Object>> handleClockBackwardsException(ClockBackwardsException e) {
        log.error("Clock backwards detected: offset={}ms", e.getOffset());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 50001);
        response.put("message", e.getMessage());
        response.put("errorCode", "CLOCK_BACKWARDS");
        response.put("offset", e.getOffset());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Handle IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 40001);
        response.put("message", e.getMessage());
        response.put("errorCode", "INVALID_ARGUMENT");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle IllegalStateException
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException e) {
        log.error("Invalid state: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 50302);
        response.put("message", e.getMessage());
        response.put("errorCode", "SERVICE_UNAVAILABLE");
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Handle general exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 50000);
        response.put("message", "Internal server error");
        response.put("errorCode", "INTERNAL_ERROR");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
