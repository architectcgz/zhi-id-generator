package com.platform.idgen.domain.service;

import com.platform.idgen.domain.exception.ClockBackwardsException;
import com.platform.idgen.domain.exception.IdGenerationException;
import com.platform.idgen.domain.model.aggregate.SnowflakeWorker;
import com.platform.idgen.domain.model.valueobject.DatacenterId;
import com.platform.idgen.domain.model.valueobject.SnowflakeId;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.repository.WorkerIdRepository;
import com.platform.idgen.infrastructure.config.SnowflakeProperties;
import com.platform.idgen.infrastructure.config.ZooKeeperProperties;
import com.platform.idgen.infrastructure.zookeeper.WorkerIdCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Snowflake ID Generation Domain Service
 * 
 * Manages the lifecycle of SnowflakeWorker and coordinates with WorkerIdRepository.
 */
@Service
public class SnowflakeDomainService {
    
    private static final Logger log = LoggerFactory.getLogger(SnowflakeDomainService.class);
    
    private final WorkerIdRepository workerIdRepository;
    private final MeterRegistry meterRegistry;
    private final SnowflakeProperties properties;
    private final ZooKeeperProperties zkProperties;
    
    private SnowflakeWorker worker;
    private volatile boolean accepting = true;
    
    // Monitoring metrics
    private final Counter workerIdRegistrationSuccess;
    private final Counter workerIdRegistrationFailure;
    private final AtomicLong clockDriftMs = new AtomicLong(0);
    private final Counter clockBackwardsCount;
    private final Counter sequenceOverflowCount;
    private final Timer idGenerationLatency;
    
    public SnowflakeDomainService(WorkerIdRepository workerIdRepository, 
                                   MeterRegistry meterRegistry,
                                   SnowflakeProperties properties,
                                   ZooKeeperProperties zkProperties) {
        this.workerIdRepository = workerIdRepository;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.zkProperties = zkProperties;
        
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
            DatacenterId datacenterId = new DatacenterId(properties.getDatacenterId());
            String serviceName = zkProperties.getServiceName();
            long epoch = properties.getEpoch();
            
            initialize(datacenterId, serviceName, epoch);
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
            
            // Create SnowflakeWorker instance
            // Create adapter that makes WorkerIdRepository work as WorkerIdCache
            WorkerIdCache cacheAdapter = new WorkerIdCache() {
                @Override
                public Optional<Long> loadLastUsedTimestamp() {
                    return workerIdRepository.loadLastUsedTimestamp();
                }
                
                @Override
                public void saveLastUsedTimestamp(long timestamp) {
                    workerIdRepository.saveLastUsedTimestamp(timestamp);
                }
            };
            
            this.worker = new SnowflakeWorker(workerId, datacenterId, epoch, cacheAdapter);
            
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
        
        return idGenerationLatency.record(() -> {
            long startTime = System.currentTimeMillis();
            try {
                SnowflakeId id = worker.generateId();
                long endTime = System.currentTimeMillis();
                
                // If generation took more than expected, might be due to sequence overflow
                // (waiting for next millisecond) or clock backwards handling
                if (endTime - startTime > 1) {
                    // Heuristic - if it took more than 1ms, likely sequence overflow or clock wait
                    sequenceOverflowCount.increment();
                }
                
                return id;
            } catch (ClockBackwardsException e) {
                // Record clock backwards event
                clockBackwardsCount.increment();
                clockDriftMs.set(e.getOffset());
                
                // Check if offset exceeds alert threshold
                long alertThreshold = properties.getClockBackwards().getAlertThresholdMs();
                if (e.getOffset() > alertThreshold) {
                    // Log error for alerting system to catch
                    log.error("[ALERT] Clock backwards detected: offset={}ms exceeds threshold={}ms. " +
                             "This may indicate system clock issues. WorkerId={}, DatacenterId={}", 
                             e.getOffset(), alertThreshold, 
                             worker.getWorkerId().value(), worker.getDatacenterId().value());
                } else {
                    log.warn("Clock backwards detected: offset={}ms (within threshold={}ms)", 
                            e.getOffset(), alertThreshold);
                }
                
                throw e;
            }
        });
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
        
        // Step 2: Wait for in-flight requests (max 100ms)
        try {
            Thread.sleep(100);
            log.info("Waited for in-flight requests to complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for in-flight requests", e);
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
