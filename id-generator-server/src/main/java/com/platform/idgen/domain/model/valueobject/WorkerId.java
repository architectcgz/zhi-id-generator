package com.platform.idgen.domain.model.valueobject;

/**
 * WorkerId value object representing a unique worker node identifier in Snowflake algorithm.
 * Valid range: 0-31 (5 bits)
 */
public record WorkerId(int value) {
    
    public static final int MIN_VALUE = 0;
    public static final int MAX_VALUE = 31;
    
    /**
     * Compact constructor with validation.
     */
    public WorkerId {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(
                "WorkerId must be between " + MIN_VALUE + " and " + MAX_VALUE + 
                ", but got: " + value);
        }
    }
    
    /**
     * Create WorkerId from ZooKeeper sequence number using modulo operation,
     * ensuring it's always within valid range (0-31).
     * 
     * Note: Java's modulo operation may produce negative results for negative numbers.
     * For example, -1 % 32 = -1. We need to ensure the result is always non-negative,
     * adding MAX_VALUE + 1 if the result is negative.
     * 
     * @param sequenceNumber ZooKeeper sequential node number
     * @return WorkerId within valid range [0, 31]
     */
    public static WorkerId fromSequenceNumber(long sequenceNumber) {
        int workerId = (int) (sequenceNumber % (MAX_VALUE + 1));
        // Handle negative modulo results by adding 32
        if (workerId < 0) {
            workerId += (MAX_VALUE + 1);
        }
        return new WorkerId(workerId);
    }
}
