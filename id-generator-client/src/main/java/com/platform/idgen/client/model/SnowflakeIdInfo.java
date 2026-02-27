package com.platform.idgen.client.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Parsed information from a Snowflake ID.
 * 
 * A Snowflake ID is a 64-bit integer composed of:
 * - 1 bit: unused (sign bit)
 * - 41 bits: timestamp in milliseconds (relative to epoch)
 * - 5 bits: datacenter ID (0-31)
 * - 5 bits: worker ID (0-31)
 * - 12 bits: sequence number (0-4095)
 */
public record SnowflakeIdInfo(
    long id,
    long timestamp,
    int datacenterId,
    int workerId,
    int sequence,
    long epoch
) {
    /**
     * Get the timestamp as a ZonedDateTime in UTC.
     * 
     * @return the timestamp as ZonedDateTime
     */
    public ZonedDateTime getDateTime() {
        return ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneId.of("UTC")
        );
    }

    /**
     * Get the timestamp as a ZonedDateTime in the specified timezone.
     * 
     * @param zoneId the timezone
     * @return the timestamp as ZonedDateTime
     */
    public ZonedDateTime getDateTime(ZoneId zoneId) {
        return ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            zoneId
        );
    }

    /**
     * Get the relative timestamp (milliseconds since epoch).
     * 
     * @return relative timestamp
     */
    public long getRelativeTimestamp() {
        return timestamp - epoch;
    }

    @Override
    public String toString() {
        return String.format(
            "SnowflakeIdInfo{id=%d, timestamp=%d (%s), datacenterId=%d, workerId=%d, sequence=%d}",
            id, timestamp, getDateTime(), datacenterId, workerId, sequence
        );
    }
}
