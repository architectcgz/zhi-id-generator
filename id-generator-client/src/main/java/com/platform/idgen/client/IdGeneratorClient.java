package com.platform.idgen.client;

import com.platform.idgen.client.model.SnowflakeIdInfo;

import java.util.List;

/**
 * ID Generator Client Interface
 * 
 * Provides methods to generate distributed unique IDs using either
 * Snowflake mode (timestamp-based) or Segment mode (database sequence-based).
 * 
 * Thread-safe: All implementations must be thread-safe.
 */
public interface IdGeneratorClient {

    // ==================== Snowflake Mode ====================

    /**
     * Generate a single Snowflake ID.
     * 
     * Snowflake IDs are 64-bit integers composed of:
     * - 41 bits: timestamp (milliseconds since epoch)
     * - 5 bits: datacenter ID
     * - 5 bits: worker ID
     * - 12 bits: sequence number
     * 
     * @return a unique Snowflake ID
     * @throws IdGeneratorException if generation fails
     */
    long nextSnowflakeId();

    /**
     * Generate multiple Snowflake IDs in batch.
     * 
     * More efficient than calling nextSnowflakeId() multiple times
     * as it reduces network round-trips.
     * 
     * @param count number of IDs to generate (1-1000)
     * @return list of unique Snowflake IDs
     * @throws IllegalArgumentException if count is invalid
     * @throws IdGeneratorException if generation fails
     */
    List<Long> nextSnowflakeIds(int count);

    // ==================== Segment Mode ====================

    /**
     * Generate a single Segment ID for the specified business tag.
     * 
     * Segment IDs are monotonically increasing integers allocated
     * from database-backed sequences, grouped by business tag.
     * 
     * @param bizTag business tag identifier (e.g., "order", "user")
     * @return a unique Segment ID for this business tag
     * @throws IllegalArgumentException if bizTag is null or empty
     * @throws IdGeneratorException if generation fails or bizTag not found
     */
    long nextSegmentId(String bizTag);

    /**
     * Generate multiple Segment IDs for the specified business tag.
     * 
     * @param bizTag business tag identifier
     * @param count number of IDs to generate (1-1000)
     * @return list of unique Segment IDs
     * @throws IllegalArgumentException if bizTag is invalid or count is out of range
     * @throws IdGeneratorException if generation fails
     */
    List<Long> nextSegmentIds(String bizTag, int count);

    // ==================== Utility Methods ====================

    /**
     * Parse a Snowflake ID to extract its components.
     * 
     * @param id the Snowflake ID to parse
     * @return parsed components including timestamp, datacenter ID, worker ID, sequence
     * @throws IdGeneratorException if parsing fails
     */
    SnowflakeIdInfo parseSnowflakeId(long id);

    /**
     * Check if the ID Generator server is healthy and reachable.
     * 
     * @return true if server is healthy, false otherwise
     */
    boolean isHealthy();

    /**
     * Close the client and release resources.
     * 
     * After calling close(), the client should not be used anymore.
     */
    void close();
}
