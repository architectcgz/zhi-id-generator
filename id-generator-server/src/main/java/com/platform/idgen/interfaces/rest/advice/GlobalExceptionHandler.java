package com.platform.idgen.interfaces.rest.advice;

import com.platform.idgen.domain.exception.ClockBackwardsException;
import com.platform.idgen.domain.exception.IdGenerationException;
import com.platform.idgen.interfaces.rest.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理器，统一使用 ApiResponse 格式返回错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IdGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdGenerationException(IdGenerationException e) {
        log.error("ID generation error: {}", e.getMessage());

        HttpStatus status = switch (e.getErrorCode()) {
            case BIZ_TAG_NOT_EXISTS -> HttpStatus.NOT_FOUND;
            case CACHE_NOT_INITIALIZED, SEGMENTS_NOT_READY, WORKER_ID_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case CLOCK_BACKWARDS -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status)
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(ClockBackwardsException.class)
    public ResponseEntity<ApiResponse<Void>> handleClockBackwardsException(ClockBackwardsException e) {
        log.error("Clock backwards detected: offset={}ms", e.getOffset());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>error(50001, "CLOCK_BACKWARDS", e.getMessage())
                        .withExtra(Map.of("offset", e.getOffset())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(40001, "INVALID_ARGUMENT", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
        log.error("Invalid state: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(50302, "SERVICE_UNAVAILABLE", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "INTERNAL_ERROR", "Internal server error"));
    }
}
