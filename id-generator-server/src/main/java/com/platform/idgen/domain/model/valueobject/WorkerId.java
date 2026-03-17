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
    
}
