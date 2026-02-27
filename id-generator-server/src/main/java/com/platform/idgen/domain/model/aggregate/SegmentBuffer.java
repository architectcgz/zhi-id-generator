package com.platform.idgen.domain.model.aggregate;

import com.platform.idgen.domain.model.valueobject.BizTag;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SegmentBuffer Aggregate Root - Manages dual-buffer segment switching for ID allocation.
 * 
 * This aggregate encapsulates the logic for:
 * - Thread-safe ID allocation from current segment
 * - Determining when to trigger async segment loading
 * - Switching between current and next segments
 * - Tracking segment usage statistics
 */
public class SegmentBuffer {
    
    /**
     * Business tag identifier
     */
    private final BizTag bizTag;
    
    /**
     * Dual buffer segments (current and next)
     */
    private final Segment[] segments;
    
    /**
     * Current segment position (0 or 1)
     */
    private volatile int currentPos;
    
    /**
     * Whether next segment is ready for use
     */
    private volatile boolean nextReady;
    
    /**
     * Whether buffer has been initialized
     */
    private volatile boolean initOk;
    
    /**
     * Whether background thread is loading next segment
     */
    private final AtomicBoolean threadRunning;
    
    /**
     * Read-write lock for thread-safe operations
     */
    private final ReadWriteLock lock;
    
    /**
     * Minimum step size for segment allocation
     */
    private volatile int minStep;
    
    /**
     * Timestamp of last segment update
     */
    private volatile long updateTimestamp;
    
    /**
     * Threshold percentage to trigger async segment loading (90%)
     */
    private static final double LOAD_THRESHOLD = 0.9;
    
    /**
     * Inner Segment class representing a single ID segment
     */
    public static class Segment {
        /**
         * Current value (atomically incremented)
         */
        private final AtomicLong value = new AtomicLong(0);
        
        /**
         * Maximum value for this segment
         */
        private volatile long max;
        
        /**
         * Step size for this segment
         */
        private volatile int step;
        
        /**
         * Parent buffer reference
         */
        private final SegmentBuffer buffer;
        
        public Segment(SegmentBuffer buffer) {
            this.buffer = buffer;
        }
        
        public AtomicLong getValue() {
            return value;
        }
        
        public long getMax() {
            return max;
        }
        
        public void setMax(long max) {
            this.max = max;
        }
        
        public int getStep() {
            return step;
        }
        
        public void setStep(int step) {
            this.step = step;
        }
        
        public SegmentBuffer getBuffer() {
            return buffer;
        }
        
        /**
         * Get remaining IDs in this segment
         * 
         * @return idle capacity
         */
        public long getIdle() {
            return this.max - this.value.get();
        }
        
        @Override
        public String toString() {
            return "Segment(value:" + value.get() + 
                   ",max:" + max + 
                   ",step:" + step + ")";
        }
    }
    
    /**
     * Constructor - creates a new SegmentBuffer with dual segments
     * 
     * @param bizTag business tag identifier
     */
    public SegmentBuffer(BizTag bizTag) {
        this.bizTag = bizTag;
        this.segments = new Segment[]{new Segment(this), new Segment(this)};
        this.currentPos = 0;
        this.nextReady = false;
        this.initOk = false;
        this.threadRunning = new AtomicBoolean(false);
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Get current active segment
     * 
     * @return current segment
     */
    public Segment getCurrentSegment() {
        return segments[currentPos];
    }
    
    /**
     * Get next segment (for loading)
     * 
     * @return next segment
     */
    public Segment getNextSegment() {
        return segments[nextPos()];
    }
    
    /**
     * Calculate next position index
     * 
     * @return next position (0 or 1)
     */
    private int nextPos() {
        return (currentPos + 1) % 2;
    }
    
    /**
     * Get read lock for thread-safe read operations
     * 
     * @return read lock
     */
    public Lock rLock() {
        return lock.readLock();
    }
    
    /**
     * Get write lock for thread-safe write operations
     * 
     * @return write lock
     */
    public Lock wLock() {
        return lock.writeLock();
    }
    
    // Getters and setters
    
    public BizTag getBizTag() {
        return bizTag;
    }
    
    public String getKey() {
        return bizTag.value();
    }
    
    public Segment[] getSegments() {
        return segments;
    }
    
    public int getCurrentPos() {
        return currentPos;
    }
    
    public boolean isNextReady() {
        return nextReady;
    }
    
    public void setNextReady(boolean nextReady) {
        this.nextReady = nextReady;
    }
    
    public boolean isInitOk() {
        return initOk;
    }
    
    public void setInitOk(boolean initOk) {
        this.initOk = initOk;
    }
    
    public AtomicBoolean getThreadRunning() {
        return threadRunning;
    }
    
    public int getMinStep() {
        return minStep;
    }
    
    public void setMinStep(int minStep) {
        this.minStep = minStep;
    }
    
    public long getUpdateTimestamp() {
        return updateTimestamp;
    }
    
    public void setUpdateTimestamp(long updateTimestamp) {
        this.updateTimestamp = updateTimestamp;
    }
    
    /**
     * Allocate next ID from current segment in a thread-safe manner.
     * 
     * This method:
     * 1. Acquires read lock for thread safety
     * 2. Gets current segment
     * 3. Atomically increments and returns next ID
     * 4. Checks if segment is exhausted and switches if needed
     * 
     * @return next available ID
     * @throws IllegalStateException if buffer not initialized or segment exhausted
     */
    public long nextId() {
        if (!initOk) {
            throw new IllegalStateException("SegmentBuffer not initialized for bizTag: " + bizTag.value());
        }
        
        Lock readLock = rLock();
        readLock.lock();
        try {
            Segment currentSegment = getCurrentSegment();
            long value = currentSegment.getValue().getAndIncrement();
            
            // Check if within segment max
            if (value < currentSegment.getMax()) {
                return value;
            }
            
            // Current segment exhausted, need to switch
            readLock.unlock();
            Lock writeLock = wLock();
            writeLock.lock();
            try {
                // Re-check after acquiring write lock
                currentSegment = getCurrentSegment();
                value = currentSegment.getValue().getAndIncrement();
                
                if (value < currentSegment.getMax()) {
                    return value;
                }
                
                // If next segment is ready, switch
                if (nextReady) {
                    switchToNextSegment();
                    return nextId(); // Recursively get ID from new segment
                } else {
                    throw new IllegalStateException(
                        "Current segment exhausted and next segment not ready for bizTag: " + bizTag.value());
                }
            } finally {
                writeLock.unlock();
                readLock.lock(); // Re-acquire read lock before returning
            }
        } finally {
            readLock.unlock();
        }
    }
    
    /**
     * Determine if next segment should be loaded asynchronously.
     * 
     * Triggers async loading when:
     * 1. Next segment is not yet ready
     * 2. No background thread is loading
     * 3. Current segment idle capacity < 90% of step
     * 
     * This ensures next segment is ready before current exhausts.
     * 
     * @return true if next segment should be loaded, false otherwise
     */
    public boolean shouldLoadNextSegment() {
        // Don't load if next is already ready
        if (nextReady) {
            return false;
        }
        
        // Don't load if thread is already loading
        if (threadRunning.get()) {
            return false;
        }
        
        // Check if current segment idle is below threshold
        Segment currentSegment = getCurrentSegment();
        long idle = currentSegment.getIdle();
        int step = currentSegment.getStep();
        
        // Trigger when idle < 90% of step
        return idle < (step * LOAD_THRESHOLD);
    }
    
    /**
     * Switch from current segment to next segment.
     * 
     * This method:
     * 1. Validates next segment is ready
     * 2. Switches current position to next segment
     * 3. Marks next segment as not ready (will be loaded again)
     * 
     * Should be called while holding write lock.
     * 
     * @throws IllegalStateException if next segment not ready
     */
    public void switchToNextSegment() {
        if (!nextReady) {
            throw new IllegalStateException(
                "Cannot switch to next segment - next segment not ready for bizTag: " + bizTag.value());
        }
        
        // Switch to next position
        currentPos = nextPos();
        
        // Mark next segment as not ready (needs to be loaded again)
        nextReady = false;
    }
}
