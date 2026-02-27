package com.platform.idgen.domain.model.valueobject;

/**
 * SnowflakeId value object representing a 64-bit distributed ID.
 * 
 * Bit structure (64 bits total):
 * - 1 bit: unused (always 0)
 * - 41 bits: timestamp (milliseconds since epoch)
 * - 5 bits: datacenter ID
 * - 5 bits: worker ID
 * - 12 bits: sequence number
 */
public record SnowflakeId(long value) {
    
    // Bit allocations
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    
    // Bit shifts
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    
    // Bit masks
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_MASK = ~(-1L << WORKER_ID_BITS);
    private static final long DATACENTER_ID_MASK = ~(-1L << DATACENTER_ID_BITS);
    
    /**
     * Extract timestamp from Snowflake ID.
     * 
     * @param epoch epoch timestamp (milliseconds)
     * @return timestamp since epoch (milliseconds)
     */
    public long getTimestamp(long epoch) {
        return ((value >> TIMESTAMP_SHIFT) & ~(-1L << 41)) + epoch;
    }
    
    /**
     * Extract datacenter ID from Snowflake ID.
     * 
     * @return datacenter ID (0-31)
     */
    public int getDatacenterId() {
        return (int) ((value >> DATACENTER_ID_SHIFT) & DATACENTER_ID_MASK);
    }
    
    /**
     * Extract worker ID from Snowflake ID.
     * 
     * @return worker ID (0-31)
     */
    public int getWorkerId() {
        return (int) ((value >> WORKER_ID_SHIFT) & WORKER_ID_MASK);
    }
    
    /**
     * Extract sequence number from Snowflake ID.
     * 
     * @return sequence number (0-4095)
     */
    public int getSequence() {
        return (int) (value & SEQUENCE_MASK);
    }
}
