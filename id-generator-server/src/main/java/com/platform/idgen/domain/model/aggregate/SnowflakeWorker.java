package com.platform.idgen.domain.model.aggregate;

import com.platform.idgen.domain.exception.ClockBackwardsException;
import com.platform.idgen.domain.model.valueobject.DatacenterId;
import com.platform.idgen.domain.model.valueobject.SnowflakeId;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.infrastructure.zookeeper.WorkerIdCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SnowflakeWorker Aggregate Root
 * 
 * Encapsulates the Snowflake ID generation algorithm with clock backwards handling.
 * This is the core domain aggregate responsible for generating unique 64-bit IDs.
 * 
 * Snowflake ID Structure (64 bits):
 * - 1 bit: unused (always 0)
 * - 41 bits: timestamp (milliseconds since epoch)
 * - 5 bits: datacenter ID
 * - 5 bits: worker ID
 * - 12 bits: sequence number
 * 
 * Thread Safety: This class is thread-safe. The generateId() method is synchronized.
 */
public class SnowflakeWorker {
    
    private static final Logger log = LoggerFactory.getLogger(SnowflakeWorker.class);
    
    // Bit allocations
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    
    // Max values for each component
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    
    // Bit shifts
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    
    // Clock backwards handling thresholds
    private static final long CLOCK_BACKWARDS_WAIT_THRESHOLD_MS = 5L;
    
    // Immutable fields
    private final WorkerId workerId;
    private final DatacenterId datacenterId;
    private final long epoch;
    private final WorkerIdCache cache;
    
    // Mutable state (protected by synchronization)
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    /**
     * Create a new SnowflakeWorker
     * 
     * @param workerId The worker ID (0-31)
     * @param datacenterId The datacenter ID (0-31)
     * @param epoch The epoch timestamp (milliseconds since 1970-01-01)
     * @param cache The cache for persisting timestamps
     */
    public SnowflakeWorker(WorkerId workerId, DatacenterId datacenterId, long epoch, WorkerIdCache cache) {
        if (epoch < 0) {
            throw new IllegalArgumentException("Epoch must be non-negative, but got: " + epoch);
        }
        if (cache == null) {
            throw new IllegalArgumentException("WorkerIdCache cannot be null");
        }
        
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.epoch = epoch;
        this.cache = cache;
        
        // Load cached timestamp for recovery after restart
        cache.loadLastUsedTimestamp().ifPresent(cachedTimestamp -> {
            this.lastTimestamp = cachedTimestamp;
            log.info("Loaded cached last timestamp: {} for recovery", cachedTimestamp);
            
            // If current time is still at or before the cached timestamp,
            // wait for the next millisecond to ensure no duplicate IDs
            long currentTimestamp = getCurrentTimestamp();
            if (currentTimestamp <= cachedTimestamp) {
                long waitTime = cachedTimestamp - currentTimestamp + 1;
                log.info("Current timestamp ({}) <= cached timestamp ({}), waiting {}ms for next millisecond",
                        currentTimestamp, cachedTimestamp, waitTime);
                try {
                    Thread.sleep(waitTime);
                    this.lastTimestamp = getCurrentTimestamp();
                    log.info("After waiting, updated lastTimestamp to: {}", this.lastTimestamp);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for next millisecond during initialization", e);
                }
            }
        });
        
        log.info("SnowflakeWorker initialized: workerId={}, datacenterId={}, epoch={}, lastTimestamp={}", 
                 workerId.value(), datacenterId.value(), epoch, lastTimestamp);
    }
    
    /**
     * Get the last used timestamp
     * Used for persisting timestamp during shutdown
     * 
     * @return The last timestamp used for ID generation
     */
    public long getLastTimestamp() {
        return lastTimestamp;
    }
    
    /**
     * Get the worker ID
     * 
     * @return The worker ID
     */
    public WorkerId getWorkerId() {
        return workerId;
    }
    
    /**
     * Get the datacenter ID
     * 
     * @return The datacenter ID
     */
    public DatacenterId getDatacenterId() {
        return datacenterId;
    }
    
    /**
     * Get the epoch
     * 
     * @return The epoch timestamp
     */
    public long getEpoch() {
        return epoch;
    }
    
    /**
     * Generate a unique Snowflake ID
     * 
     * This method is thread-safe and handles:
     * - Timestamp validation (must not go backwards)
     * - Clock backwards detection and recovery
     * - Sequence overflow handling
     * 
     * @return A unique SnowflakeId
     * @throws ClockBackwardsException if clock moves backwards significantly
     */
    public synchronized SnowflakeId generateId() {
        long currentTimestamp = getCurrentTimestamp();
        
        // Validate timestamp and handle clock backwards
        validateTimestamp(currentTimestamp);
        
        // Handle sequence within the same millisecond
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            
            if (sequence == 0) {
                // Sequence overflow - wait for next millisecond
                currentTimestamp = waitForNextMillisecond(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        
        lastTimestamp = currentTimestamp;
        
        long id = composeId(currentTimestamp, sequence);
        return new SnowflakeId(id);
    }
    
    /**
     * Get current timestamp in milliseconds since epoch
     * 
     * @return Current timestamp relative to epoch
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis() - epoch;
    }
    
    /**
     * Validate that the current timestamp is not earlier than the last timestamp
     * 
     * @param currentTimestamp The current timestamp to validate
     * @throws ClockBackwardsException if clock moves backwards significantly
     */
    private void validateTimestamp(long currentTimestamp) {
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            handleClockBackwards(offset);
        }
    }
    
    /**
     * Handle clock backwards scenario
     * 
     * Strategy:
     * - If offset <= 5ms: Wait for clock to catch up
     * - If offset > 5ms: Use cached last timestamp and log warning (maintain availability)
     * 
     * @param offset The number of milliseconds the clock moved backwards
     */
    private void handleClockBackwards(long offset) {
        log.warn("Clock moved backwards by {}ms. Last timestamp: {}, Current: {}", 
                 offset, lastTimestamp, getCurrentTimestamp());
        
        if (offset <= CLOCK_BACKWARDS_WAIT_THRESHOLD_MS) {
            log.info("Clock drift is small ({}ms <= {}ms), waiting for clock to catch up", 
                     offset, CLOCK_BACKWARDS_WAIT_THRESHOLD_MS);
            try {
                Thread.sleep(offset);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClockBackwardsException(offset);
            }
        } else {
            log.warn("Clock drift is large ({}ms > {}ms), using cached last timestamp to maintain availability", 
                     offset, CLOCK_BACKWARDS_WAIT_THRESHOLD_MS);
            cache.saveLastUsedTimestamp(lastTimestamp);
        }
    }
    
    /**
     * Wait for the next millisecond when sequence overflows
     * 
     * @param lastTimestamp The last timestamp used
     * @return The next millisecond timestamp
     */
    private long waitForNextMillisecond(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        
        log.debug("Sequence overflow handled, waited for next millisecond. Last: {}, Current: {}", 
                  lastTimestamp, timestamp);
        
        return timestamp;
    }
    
    /**
     * Compose a 64-bit Snowflake ID from its components
     * 
     * @param timestamp The timestamp (relative to epoch)
     * @param sequence The sequence number
     * @return The composed 64-bit ID
     */
    private long composeId(long timestamp, long sequence) {
        return (timestamp << TIMESTAMP_SHIFT)
                | (datacenterId.value() << DATACENTER_ID_SHIFT)
                | (workerId.value() << WORKER_ID_SHIFT)
                | sequence;
    }
}
