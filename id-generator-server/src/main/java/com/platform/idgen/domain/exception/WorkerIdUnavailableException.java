package com.platform.idgen.domain.exception;

/**
 * Exception thrown when WorkerId cannot be obtained.
 * 
 * This exception may occur in the following situations:
 * - ZooKeeper registration failed
 * - Local cache unavailable or corrupted
 * - WorkerId conflict detected
 * - All WorkerIds are in use
 */
public class WorkerIdUnavailableException extends IdGenerationException {
    private final String reason;
    
    /**
     * Create exception with detailed reason.
     * 
     * @param reason detailed explanation of why WorkerId is unavailable
     */
    public WorkerIdUnavailableException(String reason) {
        super(ErrorCode.WORKER_ID_UNAVAILABLE, reason);
        this.reason = reason;
    }
    
    /**
     * Create exception with reason and underlying cause.
     * 
     * @param reason detailed explanation of why WorkerId is unavailable
     * @param cause the underlying exception that caused this failure
     */
    public WorkerIdUnavailableException(String reason, Throwable cause) {
        super(ErrorCode.WORKER_ID_UNAVAILABLE, cause);
        this.reason = reason;
    }
    
    /**
     * Get the detailed reason for WorkerId unavailability.
     * 
     * @return detailed reason string
     */
    public String getReason() {
        return reason;
    }
}
