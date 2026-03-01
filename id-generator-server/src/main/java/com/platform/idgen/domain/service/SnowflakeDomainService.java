package com.platform.idgen.domain.service;

import com.platform.idgen.domain.exception.ClockBackwardsException;
import com.platform.idgen.domain.exception.IdGenerationException;
import com.platform.idgen.domain.model.aggregate.SnowflakeWorker;
import com.platform.idgen.domain.model.valueobject.DatacenterId;
import com.platform.idgen.domain.model.valueobject.SnowflakeId;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.repository.WorkerIdRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Snowflake ID 生成领域服务。
 * 管理 SnowflakeWorker 的生命周期，协调 WorkerIdRepository。
 * 通过 SnowflakeDomainServiceConfig 装配，不直接依赖基础设施层。
 */
public class SnowflakeDomainService {
    
    private static final Logger log = LoggerFactory.getLogger(SnowflakeDomainService.class);
    
    private final WorkerIdRepository workerIdRepository;
    private final MeterRegistry meterRegistry;

    // 从配置注入的原始值，避免领域层直接依赖基础设施层的 Properties 类
    private final int configDatacenterId;
    private final String configServiceName;
    private final long configEpoch;
    private final long alertThresholdMs;
    private final long clockBackwardsWaitThresholdMs;
    private final long maxStartupWaitMs;
    
    private SnowflakeWorker worker;
    private volatile boolean accepting = true;
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);
    
    // Monitoring metrics
    private final Counter workerIdRegistrationSuccess;
    private final Counter workerIdRegistrationFailure;
    private final AtomicLong clockDriftMs = new AtomicLong(0);
    private final Counter clockBackwardsCount;
    private final Counter sequenceOverflowCount;
    private final Timer idGenerationLatency;
    
    public SnowflakeDomainService(WorkerIdRepository workerIdRepository,
                                   MeterRegistry meterRegistry,
                                   int configDatacenterId,
                                   String configServiceName,
                                   long configEpoch,
                                   long alertThresholdMs,
                                   long clockBackwardsWaitThresholdMs,
                                   long maxStartupWaitMs) {
        this.workerIdRepository = workerIdRepository;
        this.meterRegistry = meterRegistry;
        this.configDatacenterId = configDatacenterId;
        this.configServiceName = configServiceName;
        this.configEpoch = configEpoch;
        this.alertThresholdMs = alertThresholdMs;
        this.clockBackwardsWaitThresholdMs = clockBackwardsWaitThresholdMs;
        this.maxStartupWaitMs = maxStartupWaitMs;
        
        // Initialize monitoring metrics
        this.workerIdRegistrationSuccess = Counter.builder("snowflake.workerid.registration.success")
                .description("Number of successful WorkerId registrations")
                .register(meterRegistry);
        
        this.workerIdRegistrationFailure = Counter.builder("snowflake.workerid.registration.failure")
                .description("Number of failed WorkerId registrations")
                .register(meterRegistry);
        
        Gauge.builder("snowflake.clock.drift.ms", clockDriftMs, AtomicLong::get)
                .description("Clock drift in milliseconds")
                .register(meterRegistry);
        
        this.clockBackwardsCount = Counter.builder("snowflake.clock.backwards.count")
                .description("Number of clock backwards detections")
                .register(meterRegistry);
        
        this.sequenceOverflowCount = Counter.builder("snowflake.sequence.overflow.count")
                .description("Number of sequence overflows per millisecond")
                .register(meterRegistry);
        
        this.idGenerationLatency = Timer.builder("snowflake.id.generation.latency")
                .description("ID generation latency")
                .register(meterRegistry);
    }
    
    /**
     * Auto-initialize Snowflake Worker on application startup.
     * Uses configuration from SnowflakeProperties and ZooKeeperProperties.
     */
    @PostConstruct
    public void autoInitialize() {
        try {
            DatacenterId datacenterId = new DatacenterId(configDatacenterId);
            initialize(datacenterId, configServiceName, configEpoch);
        } catch (Exception e) {
            log.warn("Failed to auto-initialize SnowflakeDomainService: {}", e.getMessage());
            log.warn("Snowflake ID generation will not be available until manually initialized");
            // Don't throw - allow service to start in degraded mode
        }
    }
    
    /**
     * Initialize Snowflake Worker including WorkerId registration and cached timestamp recovery.
     * 
     * @param datacenterId Datacenter ID for this instance
     * @param serviceName Service name for ZooKeeper registration
     * @param epoch Epoch timestamp for Snowflake algorithm
     * @throws IdGenerationException if initialization fails
     */
    public void initialize(DatacenterId datacenterId, String serviceName, long epoch) {
        log.info("Initializing SnowflakeDomainService with datacenterId={}, serviceName={}, epoch={}", 
                 datacenterId.value(), serviceName, epoch);
        
        try {
            // Register WorkerId through repository (ZooKeeper or cache fallback)
            WorkerId workerId = workerIdRepository.registerWorkerId(serviceName);
            log.info("Successfully registered WorkerId: {}", workerId.value());
            
            // Record successful registration
            workerIdRegistrationSuccess.increment();
            
            // Load cached last used timestamp if available, for clock backwards detection
            Optional<Long> cachedTimestamp = workerIdRepository.loadLastUsedTimestamp();
            
            if (cachedTimestamp.isPresent()) {
                log.info("Loaded cached last timestamp: {}", cachedTimestamp.get());
            } else {
                log.info("No cached timestamp found, starting fresh");
            }
            
            // WorkerIdRepository 已继承 WorkerTimestampCache，直接传入即可
            this.worker = new SnowflakeWorker(workerId, datacenterId, epoch,
                    workerIdRepository, clockBackwardsWaitThresholdMs, maxStartupWaitMs);
            
            log.info("SnowflakeDomainService initialized successfully with WorkerId={}, DatacenterId={}", 
                     workerId.value(), datacenterId.value());
        } catch (Exception e) {
            // Record registration failure
            workerIdRegistrationFailure.increment();
            log.error("Failed to initialize SnowflakeDomainService", e);
            throw e;
        }
    }
    
    /**
     * Generate a new Snowflake ID.
     * 
     * @return generated Snowflake ID
     * @throws IdGenerationException if service not accepting requests or generation fails
     */
    public SnowflakeId generateId() {
        if (!accepting) {
            throw new IdGenerationException(IdGenerationException.ErrorCode.CACHE_NOT_INITIALIZED,
                    "Service is shutting down, not accepting new requests");
        }

        if (worker == null) {
            throw new IdGenerationException(IdGenerationException.ErrorCode.CACHE_NOT_INITIALIZED,
                    "SnowflakeWorker not initialized");
        }

        // DB 模式下，续期连续失败达到阈值后拒绝生成，防止与其他实例产生重复 ID
        if (!workerIdRepository.isWorkerIdValid()) {
            throw new IdGenerationException(IdGenerationException.ErrorCode.CACHE_NOT_INITIALIZED,
                    "Worker ID 租约续期失败，当前 Worker ID 可能已被其他实例占用，拒绝生成 ID 以防止冲突");
        }

        inFlightRequests.incrementAndGet();
        try {
            return idGenerationLatency.record(() -> {
                try {
                    SnowflakeWorker.GenerateResult result = worker.generateId();

                    if (result.isSequenceOverflow()) {
                        sequenceOverflowCount.increment();
                    }

                    return result.getId();
                } catch (ClockBackwardsException e) {
                    // 记录时钟回拨事件
                    clockBackwardsCount.increment();
                    clockDriftMs.set(e.getOffset());

                    // 检查回拨偏移是否超过告警阈值
                    long alertThreshold = this.alertThresholdMs;
                    if (e.getOffset() > alertThreshold) {
                        log.error("[ALERT] Clock backwards detected: offset={}ms exceeds threshold={}ms. "
                                        + "WorkerId={}, DatacenterId={}",
                                e.getOffset(), alertThreshold,
                                worker.getWorkerId().value(), worker.getDatacenterId().value());
                    } else {
                        log.warn("Clock backwards detected: offset={}ms (within threshold={}ms)",
                                e.getOffset(), alertThreshold);
                    }

                    throw e;
                }
            });
        } finally {
            inFlightRequests.decrementAndGet();
        }
    }

    /**
     * Parse a Snowflake ID into its components.
     * 
     * @param id the Snowflake ID to parse
     * @return parsed SnowflakeId value object
     */
    public SnowflakeId parseId(long id) {
        return new SnowflakeId(id);
    }
    
    /**
     * Check if service is initialized and ready to generate IDs.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return worker != null && accepting;
    }
    
    /**
     * Get current WorkerId (for monitoring/debugging).
     * 
     * @return WorkerId if initialized, empty otherwise
     */
    public Optional<WorkerId> getWorkerId() {
        return worker != null ? Optional.of(worker.getWorkerId()) : Optional.empty();
    }
    
    /**
     * Get current DatacenterId (for monitoring/debugging).
     * 
     * @return DatacenterId if initialized, empty otherwise
     */
    public Optional<DatacenterId> getDatacenterId() {
        return worker != null ? Optional.of(worker.getDatacenterId()) : Optional.empty();
    }
    
    /**
     * Get Epoch timestamp (for ID parsing).
     * 
     * @return Epoch timestamp if initialized, empty otherwise
     */
    public Optional<Long> getEpoch() {
        return worker != null ? Optional.of(worker.getEpoch()) : Optional.empty();
    }
    
    /**
     * Graceful shutdown of the service.
     * Called automatically by Spring on application shutdown.
     * 
     * Steps:
     * 1. Stop accepting new requests
     * 2. Wait for in-flight requests (max 100ms)
     * 3. Persist last used timestamp
     * 4. Release WorkerId (mark ZooKeeper node as offline)
     */
    @PreDestroy
    public void shutdown() {
        log.info("Starting graceful shutdown of SnowflakeDomainService...");
        
        // Step 1: Stop accepting new requests
        accepting = false;
        log.info("Stopped accepting new ID generation requests");
        
        // Step 2: 等待飞行中请求完成（最多 5 秒）
        long deadline = System.currentTimeMillis() + 5000;
        while (inFlightRequests.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for in-flight requests", e);
                break;
            }
        }
        if (inFlightRequests.get() > 0) {
            log.warn("Shutdown timeout, {} requests still in-flight", inFlightRequests.get());
        } else {
            log.info("All in-flight requests completed");
        }
        
        // Step 3: Persist last used timestamp
        if (worker != null) {
            long lastTimestamp = worker.getLastTimestamp();
            workerIdRepository.saveLastUsedTimestamp(lastTimestamp);
            log.info("Persisted last used timestamp: {}", lastTimestamp);
        }
        
        // Step 4: Release WorkerId (mark ZooKeeper node as offline)
        workerIdRepository.releaseWorkerId();
        log.info("Released WorkerId and marked ZooKeeper node as offline");
        
        log.info("Graceful shutdown of SnowflakeDomainService completed");
    }
}
