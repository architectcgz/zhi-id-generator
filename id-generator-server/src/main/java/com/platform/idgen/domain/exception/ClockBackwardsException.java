package com.platform.idgen.domain.exception;

/**
 * Clock backwards exception.
 * 
 * Thrown when the system clock moves backwards.
 * Contains the offset of clock backwards (in milliseconds).
 * 
 * Handling strategy:
 * - If offset <= 5ms: wait for clock to catch up
 * - If offset > 5ms: use cached last timestamp and log warning
 */
public class ClockBackwardsException extends IdGenerationException {
    private final long offset;
    
    /**
     * Create exception with clock backwards offset.
     * 
     * @param offset clock backwards in milliseconds (positive value)
     */
    public ClockBackwardsException(long offset) {
        super(ErrorCode.CLOCK_BACKWARDS, "Clock moved backwards by " + offset + "ms");
        this.offset = offset;
    }
    
    /**
     * Get the clock backwards offset in milliseconds.
     * 
     * @return offset (positive value, representing how far the clock moved back)
     */
    public long getOffset() {
        return offset;
    }
}
