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
     * nextId() 的返回结果，包含分配的 ID 和是否发生了 segment 切换、是否需要异步加载下一个 segment
     */
    public static class NextIdResult {
        private final long id;
        private final boolean segmentSwitched;
        private final boolean shouldLoadNext;

        public NextIdResult(long id, boolean segmentSwitched, boolean shouldLoadNext) {
            this.id = id;
            this.segmentSwitched = segmentSwitched;
            this.shouldLoadNext = shouldLoadNext;
        }

        public long getId() { return id; }
        public boolean isSegmentSwitched() { return segmentSwitched; }
        public boolean isShouldLoadNext() { return shouldLoadNext; }
    }

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
     * 从当前 segment 分配下一个 ID（线程安全）。
     *
     * 使用写锁保证 segment 切换与 ID 分配的原子性，
     * 消费 ID 之后在锁内检查是否需要异步加载下一个 segment，避免外部检查的竞态。
     *
     * @return NextIdResult 包含 ID 值、是否发生切换、是否需要异步加载
     * @throws IllegalStateException 如果 buffer 未初始化或 segment 耗尽且备用未就绪
     */
    public NextIdResult nextId() {
        if (!initOk) {
            throw new IllegalStateException("SegmentBuffer not initialized for bizTag: " + bizTag.value());
        }

        Lock writeLock = wLock();
        writeLock.lock();
        try {
            Segment currentSegment = getCurrentSegment();
            long value = currentSegment.getValue().getAndIncrement();
            boolean switched = false;

            if (value >= currentSegment.getMax()) {
                // 当前 segment 耗尽，尝试切换到备用 segment
                if (nextReady) {
                    switchToNextSegment();
                    switched = true;
                    currentSegment = getCurrentSegment();
                    value = currentSegment.getValue().getAndIncrement();
                    if (value >= currentSegment.getMax()) {
                        throw new IllegalStateException(
                            "Current segment exhausted and next segment not ready for bizTag: " + bizTag.value());
                    }
                } else {
                    throw new IllegalStateException(
                        "Current segment exhausted and next segment not ready for bizTag: " + bizTag.value());
                }
            }

            // 消费 ID 之后，在锁内检查是否需要触发异步加载
            boolean needLoad = checkShouldLoadNextSegment(currentSegment);
            return new NextIdResult(value, switched, needLoad);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 在锁内检查是否需要异步加载下一个 segment（仅供 nextId 内部调用）。
     */
    private boolean checkShouldLoadNextSegment(Segment currentSegment) {
        if (nextReady || threadRunning.get()) {
            return false;
        }
        long idle = currentSegment.getIdle();
        int step = currentSegment.getStep();
        return idle < (step * LOAD_THRESHOLD);
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
