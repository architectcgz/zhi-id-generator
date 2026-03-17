package com.platform.idgen.domain.service;

import com.platform.idgen.domain.exception.IdGenerationException;
import com.platform.idgen.domain.model.aggregate.SegmentBuffer;
import com.platform.idgen.domain.model.aggregate.SegmentBuffer.Segment;
import com.platform.idgen.domain.model.aggregate.SegmentBuffer.NextIdResult;
import com.platform.idgen.domain.model.valueobject.BizTag;
import com.platform.idgen.domain.repository.LeafAllocRepository;
import com.platform.idgen.domain.model.valueobject.SegmentAllocation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Segment Domain Service - Manages segment-based ID generation.
 * 
 * This service:
 * - Manages SegmentBuffer cache for different business tags
 * - Coordinates async segment loading
 * - Implements dynamic step adjustment
 * - Handles segment initialization and updates
 */
public class SegmentDomainService {

    public record SegmentStateSnapshot(long value, long max, int step, long idle) {}

    public record SegmentCacheSnapshot(
            String bizTag,
            boolean initialized,
            int currentPos,
            boolean nextReady,
            boolean loadingNextSegment,
            int minStep,
            long updateTimestamp,
            SegmentStateSnapshot currentSegment,
            SegmentStateSnapshot nextSegment) {}
    
    private static final Logger log = LoggerFactory.getLogger(SegmentDomainService.class);
    
    private final LeafAllocRepository leafAllocRepository;
    private final MeterRegistry meterRegistry;
    
    /**
     * Cached SegmentBuffers by business tag
     */
    private final Map<String, SegmentBuffer> bufferCache = new ConcurrentHashMap<>();
    
    /**
     * Thread pool for async segment updates
     */
    private ExecutorService updateExecutor;
    
    /**
     * Scheduled executor for periodic cache updates
     */
    private ScheduledExecutorService scheduledExecutor;
    
    /**
     * Service initialization status
     */
    private volatile boolean initOk = false;

    // 通过构造函数注入的配置值
    private final int cacheUpdateInterval;
    private final long segmentDuration;
    private final int maxStep;
    private final int updateThreadPoolSize;
    
    // Monitoring metrics
    private final Counter bufferSwitchCount;
    private final Timer dbUpdateLatency;
    private final Counter dbUpdateFailure;
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);
    
    public SegmentDomainService(LeafAllocRepository leafAllocRepository,
                                MeterRegistry meterRegistry,
                                int cacheUpdateInterval,
                                long segmentDuration,
                                int maxStep,
                                int updateThreadPoolSize) {
        this.leafAllocRepository = leafAllocRepository;
        this.meterRegistry = meterRegistry;
        this.cacheUpdateInterval = cacheUpdateInterval;
        this.segmentDuration = segmentDuration;
        this.maxStep = maxStep;
        this.updateThreadPoolSize = updateThreadPoolSize;
        
        // Initialize monitoring metrics
        this.bufferSwitchCount = Counter.builder("segment.buffer.switch.count")
                .description("Number of segment buffer switches")
                .register(meterRegistry);
        
        this.dbUpdateLatency = Timer.builder("segment.db.update.latency")
                .description("Database update latency for segment allocation")
                .register(meterRegistry);
        
        this.dbUpdateFailure = Counter.builder("segment.db.update.failure")
                .description("Number of database update failures")
                .register(meterRegistry);
        
        // Cache hit rate metric
        Gauge.builder("segment.cache.hit.rate", this, service -> {
            long hits = service.cacheHitCount.get();
            long misses = service.cacheMissCount.get();
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        })
        .description("Segment cache hit rate")
        .register(meterRegistry);
    }
    
    /**
     * Initialize service on startup.
     * Creates thread pools and loads initial cache from database.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing SegmentDomainService...");
        
        // Initialize thread pool for async segment updates
        updateExecutor = new ThreadPoolExecutor(
            updateThreadPoolSize,
            updateThreadPoolSize * 2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> new Thread(r, "Segment-Update-" + System.currentTimeMillis())
        );
        
        // Initialize scheduled executor for periodic cache updates
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "Segment-Cache-Update");
                t.setDaemon(true);
                return t;
            }
        );
        
        // Load initial cache from database
        updateCacheFromDb();
        initOk = true;
        
        // Schedule periodic cache updates
        scheduledExecutor.scheduleWithFixedDelay(
            this::updateCacheFromDb,
            cacheUpdateInterval,
            cacheUpdateInterval,
            TimeUnit.SECONDS
        );
        
        log.info("SegmentDomainService initialized successfully");
    }
    
    /**
     * Graceful shutdown of the service.
     * Shuts down thread pools and releases resources.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SegmentDomainService...");
        
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (updateExecutor != null) {
            updateExecutor.shutdown();
            try {
                if (!updateExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    updateExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                updateExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("SegmentDomainService shutdown completed");
    }
    
    /**
     * Update cache from database.
     * Loads all business tags and creates SegmentBuffers for new tags.
     */
    private void updateCacheFromDb() {
        log.info("Updating cache from database...");
        try {
            List<BizTag> dbTags = leafAllocRepository.findAllBizTags();
            if (dbTags == null || dbTags.isEmpty()) {
                return;
            }
            
            // Add new tags to cache
            for (BizTag bizTag : dbTags) {
                String key = bizTag.value();
                if (!bufferCache.containsKey(key)) {
                    SegmentBuffer buffer = new SegmentBuffer(bizTag);
                    bufferCache.put(key, buffer);
                    log.info("Added new bizTag to cache: {}", key);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to update cache from database", e);
        }
    }
    
    /**
     * Get all business tags currently in cache.
     * 
     * @return list of business tag strings
     */
    public List<String> getAllBizTags() {
        return new ArrayList<>(bufferCache.keySet());
    }

    /**
     * 获取指定 bizTag 的当前缓存快照，用于观测和诊断。
     */
    public Optional<SegmentCacheSnapshot> getCacheSnapshot(BizTag bizTag) {
        if (bizTag == null) {
            throw new IllegalArgumentException("BizTag cannot be null");
        }

        SegmentBuffer buffer = bufferCache.get(bizTag.value());
        if (buffer == null) {
            return Optional.empty();
        }

        buffer.rLock().lock();
        try {
            Segment current = buffer.getCurrentSegment();
            Segment next = buffer.getNextSegment();
            return Optional.of(new SegmentCacheSnapshot(
                    bizTag.value(),
                    buffer.isInitOk(),
                    buffer.getCurrentPos(),
                    buffer.isNextReady(),
                    buffer.getThreadRunning().get(),
                    buffer.getMinStep(),
                    buffer.getUpdateTimestamp(),
                    new SegmentStateSnapshot(
                            current.getValue().get(),
                            current.getMax(),
                            current.getStep(),
                            current.getIdle()
                    ),
                    new SegmentStateSnapshot(
                            next.getValue().get(),
                            next.getMax(),
                            next.getStep(),
                            next.getIdle()
                    )
            ));
        } finally {
            buffer.rLock().unlock();
        }
    }
    
    /**
     * Check if service is initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initOk;
    }
    
    /**
     * Generate the next ID for the given business tag.
     * 
     * This method:
     * 1. Gets or creates SegmentBuffer for bizTag
     * 2. Initializes buffer if needed
     * 3. Delegates to SegmentBuffer.nextId() for ID allocation
     * 4. Triggers async segment loading if threshold reached
     * 
     * @param bizTag business tag identifier
     * @return next available ID
     * @throws IllegalStateException if service not initialized or buffer not ready
     */
    public long generateId(BizTag bizTag) {
        if (!initOk) {
            throw new IdGenerationException(
                    IdGenerationException.ErrorCode.CACHE_NOT_INITIALIZED,
                    "SegmentDomainService not initialized");
        }
        
        if (bizTag == null) {
            throw new IllegalArgumentException("BizTag cannot be null");
        }

        boolean created = false;
        SegmentBuffer buffer = bufferCache.get(bizTag.value());
        if (buffer == null) {
            cacheMissCount.incrementAndGet();
            SegmentBuffer newBuffer = new SegmentBuffer(bizTag);
            SegmentBuffer existing = bufferCache.putIfAbsent(bizTag.value(), newBuffer);
            buffer = existing != null ? existing : newBuffer;
            created = existing == null;
            if (created) {
                log.info("Lazily created SegmentBuffer for bizTag: {}", bizTag.value());
            }
        } else {
            cacheHitCount.incrementAndGet();
        }
        
        // Initialize buffer if needed
        if (!buffer.isInitOk()) {
            synchronized (buffer) {
                if (!buffer.isInitOk()) {
                    try {
                        initializeBuffer(bizTag, buffer);
                    } catch (RuntimeException e) {
                        // 首次懒创建但初始化失败时回滚缓存，避免把无效 buffer 长期留在内存里。
                        if (created) {
                            bufferCache.remove(bizTag.value(), buffer);
                        }
                        throw e;
                    }
                }
            }
        }
        
        // 由 nextId() 内部在锁内检查是否需要异步加载，避免外部检查的竞态
        SegmentBuffer.NextIdResult result;
        try {
            result = buffer.nextId();
        } catch (IllegalStateException e) {
            throw new IdGenerationException(
                    IdGenerationException.ErrorCode.SEGMENTS_NOT_READY,
                    "bizTag=" + bizTag.value() + ", reason=" + e.getMessage());
        }

        // 根据 nextId 返回的标志触发异步加载
        if (result.isShouldLoadNext()) {
            asyncLoadNextSegment(buffer);
        }

        if (result.isSegmentSwitched()) {
            bufferSwitchCount.increment();
            log.debug("Buffer switched segments for bizTag: {}", bizTag.value());
        }

        return result.getId();
    }
    
    /**
     * Initialize SegmentBuffer by loading initial segment from database.
     * 
     * @param bizTag business tag identifier
     * @param buffer the SegmentBuffer to initialize
     * @throws RuntimeException if initialization fails
     */
    private void initializeBuffer(BizTag bizTag, SegmentBuffer buffer) {
        log.info("Initializing buffer for bizTag: {}", bizTag.value());
        
        try {
            // 从数据库加载号段分配信息
            SegmentAllocation allocation = leafAllocRepository.findByBizTag(bizTag)
                    .orElseThrow(() -> new IdGenerationException(
                            IdGenerationException.ErrorCode.BIZ_TAG_NOT_EXISTS, bizTag.value()));

            // Set minimum step from database config
            buffer.setMinStep(allocation.getStep());
            buffer.setUpdateTimestamp(System.currentTimeMillis());
            
            // Load initial segment (current segment)
            Segment currentSegment = buffer.getCurrentSegment();
            updateSegmentFromDatabase(bizTag, currentSegment);
            
            // Mark buffer as initialized
            buffer.setInitOk(true);
            
            log.info("Buffer initialized successfully for bizTag: {}", bizTag.value());
        } catch (IdGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to initialize buffer for bizTag: {}", bizTag.value(), e);
            throw new IdGenerationException(
                    IdGenerationException.ErrorCode.SEGMENT_UPDATE_FAILED,
                    "Failed to initialize buffer for bizTag: " + bizTag.value());
        }
    }
    
    /**
     * Update segment from database with optimistic locking retry.
     * 
     * @param bizTag business tag identifier
     * @param segment the segment to update
     * @throws RuntimeException if update fails after all retries
     */
    private void updateSegmentFromDatabase(BizTag bizTag, Segment segment) {
        SegmentBuffer buffer = segment.getBuffer();
        
        dbUpdateLatency.record(() -> {
            try {
                int stepToUse;
                SegmentAllocation allocation;

                if (!buffer.isInitOk()) {
                    // First initialization: use database configured step
                    allocation = leafAllocRepository.findByBizTag(bizTag)
                            .orElseThrow(() -> new IdGenerationException(
                                    IdGenerationException.ErrorCode.BIZ_TAG_NOT_EXISTS, bizTag.value()));
                    stepToUse = allocation.getStep();
                    buffer.setMinStep(stepToUse);
                    buffer.setUpdateTimestamp(System.currentTimeMillis());
                    
                    // Update max_id in database with default step
                    allocation = leafAllocRepository.updateMaxId(bizTag);
                    
                } else {
                    // Dynamic step adjustment based on consumption rate
                    stepToUse = calculateOptimalStep(buffer);
                    
                    log.debug("Updating segment for bizTag: {} with step: {}", bizTag.value(), stepToUse);
                    
                    // Update max_id in database with calculated step
                    allocation = leafAllocRepository.updateMaxIdByCustomStep(bizTag, stepToUse);
                    
                    buffer.setUpdateTimestamp(System.currentTimeMillis());
                }
                
                // 号段范围 [maxId - step, maxId)
                long value = allocation.getMaxId() - allocation.getStep();
                segment.getValue().set(value);
                segment.setMax(allocation.getMaxId());
                segment.setStep(allocation.getStep());
                
                log.info("Updated segment from database - bizTag: {}, segment: {}", bizTag.value(), segment);
            } catch (IdGenerationException e) {
                throw e;
            } catch (Exception e) {
                // Record database update failure
                dbUpdateFailure.increment();
                log.error("Failed to update segment from database for bizTag: {}", bizTag.value(), e);
                throw new IdGenerationException(
                        IdGenerationException.ErrorCode.SEGMENT_UPDATE_FAILED,
                        "Failed to update segment from database for bizTag: " + bizTag.value());
            }
        });
    }
    
    /**
     * Asynchronously load next segment in background.
     * 
     * @param buffer the SegmentBuffer to load next segment for
     */
    private void asyncLoadNextSegment(SegmentBuffer buffer) {
        // Use CAS to ensure only one thread loads the next segment
        if (!buffer.getThreadRunning().compareAndSet(false, true)) {
            // Another thread is already loading
            return;
        }
        
        BizTag bizTag = buffer.getBizTag();
        
        // Submit async task to load next segment
        updateExecutor.execute(() -> {
            try {
                log.debug("Async loading next segment for bizTag: {}", bizTag.value());
                
                // Get next segment and update it from database
                Segment nextSegment = buffer.getNextSegment();
                updateSegmentFromDatabase(bizTag, nextSegment);
                
                // Mark next segment as ready
                buffer.setNextReady(true);
                
                log.info("Next segment loaded successfully for bizTag: {}", bizTag.value());
                
            } catch (Exception e) {
                log.error("Failed to load next segment for bizTag: {}", bizTag.value(), e);
            } finally {
                // Always reset thread running flag
                buffer.getThreadRunning().set(false);
            }
        });
    }
    
    /**
     * Calculate optimal step based on segment consumption rate.
     * 
     * This implements dynamic step adjustment:
     * - If segment consumed too fast (< segmentDuration): double step (up to maxStep)
     * - If segment consumed too slow (>= 2 * segmentDuration): halve step (down to minStep)
     * - Otherwise: keep current step
     * 
     * @param buffer the SegmentBuffer to calculate step for
     * @return optimal step size
     */
    private int calculateOptimalStep(SegmentBuffer buffer) {
        long duration = System.currentTimeMillis() - buffer.getUpdateTimestamp();
        int currentStep = buffer.getCurrentSegment().getStep();
        int minStep = buffer.getMinStep();
        int nextStep = currentStep;
        
        if (duration < segmentDuration) {
            // Consumed too fast - increase step
            if (nextStep * 2 <= maxStep) {
                nextStep = nextStep * 2;
            }
            log.debug("Segment consumed quickly ({}ms < {}ms), increasing step from {} to {}", 
                    duration, segmentDuration, currentStep, nextStep);
            
        } else if (duration >= segmentDuration * 2) {
            // Consumed too slow - decrease step
            nextStep = Math.max(nextStep / 2, minStep);
            log.debug("Segment consumed slowly ({}ms >= {}ms), decreasing step from {} to {}", 
                    duration, segmentDuration * 2, currentStep, nextStep);
            
        } else {
            // Consumption rate is optimal - keep current step
            log.debug("Segment consumption rate is optimal ({}ms), keeping step at {}", 
                    duration, currentStep);
        }
        
        return nextStep;
    }
}
