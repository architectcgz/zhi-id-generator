package com.platform.idgen.domain.model.valueobject;

/**
 * DatacenterId value object representing a unique datacenter identifier in Snowflake algorithm.
 * Valid range: 0-31 (5 bits)
 */
public record DatacenterId(int value) {
    
    public static final int MIN_VALUE = 0;
    public static final int MAX_VALUE = 31;
    
    /**
     * Compact constructor with validation.
     */
    public DatacenterId {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(
                "DatacenterId must be between " + MIN_VALUE + " and " + MAX_VALUE + 
                ", but got: " + value);
        }
    }
}
