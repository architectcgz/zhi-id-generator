package com.platform.idgen.client;

/**
 * Exception thrown when ID generation fails.
 * 
 * This is the base exception for all ID Generator client errors.
 */
public class IdGeneratorException extends RuntimeException {

    private final ErrorCode errorCode;

    public IdGeneratorException(String message) {
        super(message);
        this.errorCode = ErrorCode.UNKNOWN;
    }

    public IdGeneratorException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.UNKNOWN;
    }

    public IdGeneratorException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public IdGeneratorException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Error codes for ID Generator client exceptions.
     */
    public enum ErrorCode {
        /** Unknown error */
        UNKNOWN(50000, "Unknown error"),

        /** Server is not reachable */
        CONNECTION_FAILED(50001, "Failed to connect to ID Generator server"),

        /** Server returned an error response */
        SERVER_ERROR(50002, "ID Generator server returned an error"),

        /** Request timed out */
        TIMEOUT(50003, "Request to ID Generator server timed out"),

        /** Invalid response from server */
        INVALID_RESPONSE(50004, "Invalid response from ID Generator server"),

        /** Business tag not found (Segment mode) */
        BIZ_TAG_NOT_FOUND(40401, "Business tag not found"),

        /** Service not initialized */
        NOT_INITIALIZED(50301, "ID Generator service not initialized"),

        /** Buffer is empty and refill failed */
        BUFFER_EMPTY(50302, "ID buffer is empty"),

        /** Client is closed */
        CLIENT_CLOSED(50303, "ID Generator client is closed"),

        /** Invalid argument */
        INVALID_ARGUMENT(40001, "Invalid argument");

        private final int code;
        private final String description;

        ErrorCode(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }
}
