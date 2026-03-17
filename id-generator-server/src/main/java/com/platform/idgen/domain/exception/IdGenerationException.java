package com.platform.idgen.domain.exception;

/**
 * Base exception class for ID generation errors.
 * 
 * Provides structured error codes for different failure scenarios.
 */
public class IdGenerationException extends RuntimeException {
    private final ErrorCode errorCode;
    
    /**
     * Error codes for ID generation failures.
     */
    public enum ErrorCode {
        CACHE_NOT_INITIALIZED(50301, "ID cache not initialized"),
        BIZ_TAG_NOT_EXISTS(40401, "Business tag does not exist"),
        SEGMENTS_NOT_READY(50302, "Both segments are not ready"),
        CLOCK_BACKWARDS(50001, "Clock moved backwards"),
        WORKER_ID_UNAVAILABLE(50303, "WorkerId unavailable"),
        /** Worker ID 租约续期失败，当前 Worker ID 可能已被其他实例占用 */
        WORKER_ID_INVALID(50304, "Worker ID is invalid, lease renewal failed"),
        SEGMENT_UPDATE_FAILED(50305, "Segment allocation update failed"),
        SERVICE_SHUTTING_DOWN(50306, "Service is shutting down"),
        SNOWFLAKE_NOT_INITIALIZED(50307, "Snowflake worker not initialized");
        
        private final int code;
        private final String message;
        
        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    public IdGenerationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public IdGenerationException(ErrorCode errorCode, String additionalMessage) {
        super(errorCode.getMessage() + ": " + additionalMessage);
        this.errorCode = errorCode;
    }
    
    public IdGenerationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public IdGenerationException(ErrorCode errorCode, String additionalMessage, Throwable cause) {
        super(errorCode.getMessage() + ": " + additionalMessage, cause);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
