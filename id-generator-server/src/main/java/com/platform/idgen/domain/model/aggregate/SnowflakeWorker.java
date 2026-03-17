package com.platform.idgen.domain.model.aggregate;

import com.platform.idgen.domain.exception.ClockBackwardsException;
import com.platform.idgen.domain.model.valueobject.DatacenterId;
import com.platform.idgen.domain.model.valueobject.SnowflakeId;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.port.WorkerTimestampCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.LongSupplier;

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

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /**
     * generateId() 的返回结果，包含生成的 ID 和是否发生了 sequence overflow
     */
    public static class GenerateResult {
        private final SnowflakeId id;
        private final boolean sequenceOverflow;

        public GenerateResult(SnowflakeId id, boolean sequenceOverflow) {
            this.id = id;
            this.sequenceOverflow = sequenceOverflow;
        }

        public SnowflakeId getId() { return id; }
        /** 是否因 sequence 溢出而等待了下一毫秒 */
        public boolean isSequenceOverflow() { return sequenceOverflow; }
    }
    
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
    private static final long DEFAULT_CLOCK_BACKWARDS_WAIT_THRESHOLD_MS = 5L;
    private static final long DEFAULT_MAX_STARTUP_WAIT_MS = 5000L;

    // 不可变字段
    private final DatacenterId datacenterId;
    private final long epoch;
    private final WorkerTimestampCache cache;
    private final long clockBackwardsWaitThresholdMs;
    private final long maxStartupWaitMs;
    /**
     * 默认使用基于 nanoTime 的单调时间源，规避运行期墙钟回拨导致的大量拒绝。
     */
    private final LongSupplier currentTimestampSupplier;
    private final Sleeper sleeper;

    // 可变状态（由 synchronized 保护）
    /** 当前使用的 Worker ID，时钟回拨时可通过 switchWorkerId 切换；volatile 保证非 synchronized 读取的可见性 */
    private volatile WorkerId workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    /**
     * Create a new SnowflakeWorker
     * 
     * @param workerId The worker ID (0-31)
     * @param datacenterId The datacenter ID (0-31)
     * @param epoch The epoch timestamp (milliseconds since 1970-01-01)
     * @param cache 时间戳缓存端口，用于持久化/恢复时间戳
     */
    public SnowflakeWorker(WorkerId workerId, DatacenterId datacenterId, long epoch, WorkerTimestampCache cache) {
        this(workerId, datacenterId, epoch, cache, DEFAULT_CLOCK_BACKWARDS_WAIT_THRESHOLD_MS,
                DEFAULT_MAX_STARTUP_WAIT_MS, defaultTimestampSupplier(epoch), Thread::sleep);
    }

    /**
     * @param clockBackwardsWaitThresholdMs 时钟回拨等待阈值（毫秒），超过此值直接拒绝生成
     * @param maxStartupWaitMs 启动时等待缓存时间戳追赶的最大毫秒数
     */
    public SnowflakeWorker(WorkerId workerId, DatacenterId datacenterId, long epoch,
                           WorkerTimestampCache cache, long clockBackwardsWaitThresholdMs,
                           long maxStartupWaitMs) {
        this(workerId, datacenterId, epoch, cache, clockBackwardsWaitThresholdMs,
                maxStartupWaitMs, defaultTimestampSupplier(epoch), Thread::sleep);
    }

    SnowflakeWorker(WorkerId workerId, DatacenterId datacenterId, long epoch,
                    WorkerTimestampCache cache, long clockBackwardsWaitThresholdMs,
                    long maxStartupWaitMs, LongSupplier currentTimestampSupplier,
                    Sleeper sleeper) {
        if (epoch < 0) {
            throw new IllegalArgumentException("Epoch must be non-negative, but got: " + epoch);
        }
        if (cache == null) {
            throw new IllegalArgumentException("WorkerTimestampCache cannot be null");
        }
        if (currentTimestampSupplier == null) {
            throw new IllegalArgumentException("Current timestamp supplier cannot be null");
        }
        if (sleeper == null) {
            throw new IllegalArgumentException("Sleeper cannot be null");
        }

        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.epoch = epoch;
        this.cache = cache;
        this.clockBackwardsWaitThresholdMs = clockBackwardsWaitThresholdMs;
        this.maxStartupWaitMs = maxStartupWaitMs;
        this.currentTimestampSupplier = currentTimestampSupplier;
        this.sleeper = sleeper;

        // Load cached timestamp for recovery after restart
        cache.loadLastUsedTimestamp().ifPresent(cachedTimestamp -> {
            this.lastTimestamp = cachedTimestamp;
            log.info("Loaded cached last timestamp: {} for recovery", cachedTimestamp);
            
            // If current time is still at or before the cached timestamp,
            // wait for the next millisecond to ensure no duplicate IDs
            long currentTimestamp = getCurrentTimestamp();
            if (currentTimestamp <= cachedTimestamp) {
                long waitTime = cachedTimestamp - currentTimestamp + 1;
                // 启动等待上限，超过说明缓存时间戳异常，直接用当前时间
                if (waitTime > this.maxStartupWaitMs) {
                    log.warn("Startup wait time {}ms exceeds max {}ms, cached timestamp may be corrupted. Using current time.",
                            waitTime, maxStartupWaitMs);
                    this.lastTimestamp = currentTimestamp;
                } else {
                    log.info("Current timestamp ({}) <= cached timestamp ({}), waiting {}ms for next millisecond",
                            currentTimestamp, cachedTimestamp, waitTime);
                    try {
                        sleep(waitTime);
                        this.lastTimestamp = waitForNextMillisecond(cachedTimestamp);
                        log.info("After waiting, updated lastTimestamp to: {}", this.lastTimestamp);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting for next millisecond during initialization", e);
                    }
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
     * 运行时切换 Worker ID（用于时钟回拨场景）。
     * 切换后重置 sequence 和 lastTimestamp，因为不同 Worker ID 的 ID 空间完全独立，
     * 不存在时间重叠导致重复的风险。重置 lastTimestamp 后，切换后的首次生成不会
     * 因旧 lastTimestamp 仍大于当前时间而再次触发 ClockBackwardsException。
     *
     * @param newWorkerId 新的 Worker ID
     */
    public synchronized void switchWorkerId(WorkerId newWorkerId) {
        WorkerId oldWorkerId = this.workerId;
        this.workerId = newWorkerId;
        this.sequence = 0;
        this.lastTimestamp = -1; // 新 Worker ID 空间独立，重置时间戳避免切换后重试再次触发异常
        log.info("Worker ID 切换：{} -> {}", oldWorkerId.value(), newWorkerId.value());
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
     * @return GenerateResult 包含生成的 ID 和是否发生了 sequence overflow
     * @throws ClockBackwardsException if clock moves backwards significantly
     */
    public synchronized GenerateResult generateId() {
        long currentTimestamp = normalizeTimestamp(getCurrentTimestamp());
        boolean overflow = false;

        // Handle sequence within the same millisecond
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;

            if (sequence == 0) {
                // Sequence overflow - wait for next millisecond
                overflow = true;
                currentTimestamp = waitForNextMillisecond(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        long id = composeId(currentTimestamp, sequence);
        return new GenerateResult(new SnowflakeId(id), overflow);
    }
    
    /**
     * Get current timestamp in milliseconds since epoch
     * 
     * @return Current timestamp relative to epoch
     */
    private long getCurrentTimestamp() {
        return currentTimestampSupplier.getAsLong();
    }

    /**
     * Validate that the current timestamp is not earlier than the last timestamp
     * 
     * @param currentTimestamp The current timestamp to validate
     * @return 可安全用于生成 ID 的时间戳
     * @throws ClockBackwardsException if clock moves backwards significantly
     */
    private long normalizeTimestamp(long currentTimestamp) {
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            return handleClockBackwards(currentTimestamp, offset);
        }
        return currentTimestamp;
    }
    
    /**
     * Handle clock backwards scenario
     * 
     * Strategy:
     * - If offset <= 5ms: Wait for clock to catch up
     * - If offset > 5ms: Use cached last timestamp and log warning (maintain availability)
     * 
     * @param currentTimestamp 当前读取到的时间戳
     * @param offset The number of milliseconds the clock moved backwards
     */
    private long handleClockBackwards(long currentTimestamp, long offset) {
        log.warn("Clock moved backwards by {}ms. Last timestamp: {}, Current: {}",
                 offset, lastTimestamp, currentTimestamp);

        if (offset <= clockBackwardsWaitThresholdMs) {
            log.info("Clock drift is small ({}ms <= {}ms), waiting for clock to catch up",
                     offset, clockBackwardsWaitThresholdMs);
            try {
                sleep(offset);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClockBackwardsException(offset);
            }
            long waitedTimestamp = getCurrentTimestamp();
            if (waitedTimestamp < lastTimestamp) {
                log.warn("Clock is still behind after waiting. Clamping timestamp from {} to {} to keep IDs monotonic",
                        waitedTimestamp, lastTimestamp);
                return lastTimestamp;
            }
            return waitedTimestamp;
        } else {
            // 大回拨直接拒绝生成，避免时间重叠区间产生重复 ID
            log.error("Clock drift is large ({}ms > {}ms), refusing to generate ID to prevent duplicates",
                     offset, clockBackwardsWaitThresholdMs);
            cache.saveLastUsedTimestamp(lastTimestamp);
            throw new ClockBackwardsException(offset);
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

    private void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            sleeper.sleep(millis);
        }
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

    private static LongSupplier defaultTimestampSupplier(long epoch) {
        return new MonotonicTimestampSupplier(epoch);
    }

    private static final class MonotonicTimestampSupplier implements LongSupplier {
        private final long baseTimestamp;
        private final long baseNanoTime;

        private MonotonicTimestampSupplier(long epoch) {
            this.baseTimestamp = System.currentTimeMillis() - epoch;
            this.baseNanoTime = System.nanoTime();
        }

        @Override
        public long getAsLong() {
            long elapsedNanos = System.nanoTime() - baseNanoTime;
            if (elapsedNanos <= 0) {
                return baseTimestamp;
            }
            return baseTimestamp + (elapsedNanos / 1_000_000L);
        }
    }
}
