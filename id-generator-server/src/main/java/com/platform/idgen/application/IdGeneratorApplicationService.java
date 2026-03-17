package com.platform.idgen.application;

import com.platform.idgen.application.dto.HealthStatus;
import com.platform.idgen.application.dto.SegmentCacheInfo;
import com.platform.idgen.application.dto.SegmentCacheInfo.SegmentState;
import com.platform.idgen.application.dto.SnowflakeInfo;
import com.platform.idgen.application.dto.SnowflakeParseInfo;
import com.platform.idgen.domain.model.valueobject.BizTag;
import com.platform.idgen.domain.model.valueobject.SnowflakeId;
import com.platform.idgen.domain.service.SegmentDomainService.SegmentStateSnapshot;
import com.platform.idgen.domain.service.SegmentDomainService;
import com.platform.idgen.domain.service.SnowflakeDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ID Generation Application Service
 * 
 * This service orchestrates use cases and coordinates interactions between domain services.
 * Provides unified interface for both Segment and Snowflake ID generation modes.
 */
@Service
public class IdGeneratorApplicationService {
    
    private static final Logger log = LoggerFactory.getLogger(IdGeneratorApplicationService.class);
    
    private final SegmentDomainService segmentService;
    private final SnowflakeDomainService snowflakeService;
    
    public IdGeneratorApplicationService(
            SegmentDomainService segmentService,
            SnowflakeDomainService snowflakeService) {
        this.segmentService = segmentService;
        this.snowflakeService = snowflakeService;
    }
    
    // ========== Segment Mode Methods ==========
    
    /**
     * Generate a single Segment ID for the specified business tag.
     * 
     * @param bizTag business tag identifier
     * @return generated ID
     * @throws IllegalArgumentException if bizTag is null or invalid
     * @throws IllegalStateException if service not initialized
     */
    public long generateSegmentId(String bizTag) {
        log.debug("Generating segment ID for bizTag: {}", bizTag);
        
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        
        BizTag tag = new BizTag(bizTag);
        long id = segmentService.generateId(tag);
        
        log.debug("Generated segment ID: {} for bizTag: {}", id, bizTag);
        return id;
    }
    
    /**
     * Generate batch of Segment IDs for the specified business tag.
     * 
     * @param bizTag business tag identifier
     * @param count number of IDs to generate
     * @return list of generated IDs
     * @throws IllegalArgumentException if bizTag invalid or count <= 0
     * @throws IllegalStateException if service not initialized
     */
    public List<Long> generateBatchSegmentIds(String bizTag, int count) {
        log.debug("Generating batch of {} segment IDs for bizTag: {}", count, bizTag);
        
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
        
        BizTag tag = new BizTag(bizTag);
        List<Long> ids = new ArrayList<>(count);
        
        for (int i = 0; i < count; i++) {
            ids.add(segmentService.generateId(tag));
        }
        
        log.debug("Generated {} segment IDs for bizTag: {}", count, bizTag);
        return ids;
    }
    
    /**
     * Get all available business tags.
     * 
     * @return list of business tag strings
     */
    public List<String> getAllBizTags() {
        log.debug("Getting all business tags");
        return segmentService.getAllBizTags();
    }
    
    /**
     * Get cache info for the specified business tag.
     * 
     * @param bizTag business tag identifier
     * @return map containing cache information
     */
    public SegmentCacheInfo getSegmentCacheInfo(String bizTag) {
        log.debug("Getting cache info for bizTag: {}", bizTag);

        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }

        return segmentService.getCacheSnapshot(new BizTag(bizTag))
                .map(snapshot -> new SegmentCacheInfo(
                        snapshot.bizTag(),
                        segmentService.isInitialized(),
                        true,
                        snapshot.initialized(),
                        snapshot.currentPos(),
                        snapshot.nextReady(),
                        snapshot.loadingNextSegment(),
                        snapshot.minStep(),
                        snapshot.updateTimestamp(),
                        toSegmentState(snapshot.currentSegment()),
                        toSegmentState(snapshot.nextSegment())
                ))
                .orElseGet(() -> new SegmentCacheInfo(
                        bizTag,
                        segmentService.isInitialized(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
    }

    private SegmentState toSegmentState(SegmentStateSnapshot snapshot) {
        return new SegmentState(
                snapshot.value(),
                snapshot.max(),
                snapshot.step(),
                snapshot.idle()
        );
    }
    
    // ========== Snowflake Mode Methods ==========
    
    /**
     * Generate a single Snowflake ID.
     * 
     * @return generated Snowflake ID
     * @throws IllegalStateException if service not initialized
     */
    public long generateSnowflakeId() {
        SnowflakeId id = snowflakeService.generateId();
        return id.value();
    }
    
    /**
     * Generate batch of Snowflake IDs.
     * 
     * @param count number of IDs to generate
     * @return list of generated IDs
     * @throws IllegalArgumentException if count <= 0
     * @throws IllegalStateException if service not initialized
     */
    public List<Long> generateBatchSnowflakeIds(int count) {
        log.debug("Generating batch of {} snowflake IDs", count);
        
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
        
        List<Long> ids = new ArrayList<>(count);
        
        for (int i = 0; i < count; i++) {
            SnowflakeId id = snowflakeService.generateId();
            ids.add(id.value());
        }
        
        log.debug("Generated {} snowflake IDs", count);
        return ids;
    }
    
    /**
     * Parse a Snowflake ID into its components.
     * 
     * @param id the Snowflake ID to parse
     * @return map containing parsed components (timestamp, datacenterId, workerId, sequence)
     */
    public SnowflakeParseInfo parseSnowflakeId(long id) {
        log.debug("Parsing snowflake ID: {}", id);
        
        SnowflakeId snowflakeId = snowflakeService.parseId(id);
        long epoch = snowflakeService.getEpoch().orElse(0L);
        SnowflakeParseInfo info = new SnowflakeParseInfo(
                id,
                snowflakeId.getTimestamp(epoch),
                snowflakeId.getDatacenterId(),
                snowflakeId.getWorkerId(),
                snowflakeId.getSequence(),
                epoch
        );

        log.debug("Parsed snowflake ID: {}", info);
        return info;
    }
    
    /**
     * Get Snowflake Worker information.
     * 
     * @return map containing worker info (workerId, datacenterId, initialized)
     */
    public SnowflakeInfo getSnowflakeInfo() {
        log.debug("Getting snowflake info");

        return new SnowflakeInfo(
                snowflakeService.isInitialized(),
                snowflakeService.getWorkerId().map(workerId -> (int) workerId.value()).orElse(null),
                snowflakeService.getDatacenterId().map(datacenterId -> (int) datacenterId.value()).orElse(null),
                snowflakeService.getEpoch().orElse(null)
        );
    }
    
    // ========== Health Check ==========
    
    /**
     * Get health status of the ID generation service.
     * 
     * Aggregates health information from Segment and Snowflake domain services.
     * 
     * @return map containing health status information
     */
    public HealthStatus getHealthStatus() {
        log.debug("Getting health status");

        boolean segmentHealthy = segmentService.isInitialized();
        boolean snowflakeHealthy = snowflakeService.isInitialized();

        HealthStatus health = new HealthStatus(
                (segmentHealthy && snowflakeHealthy) ? "UP" : "DEGRADED",
                "id-generator-service",
                System.currentTimeMillis(),
                new HealthStatus.SegmentHealth(segmentHealthy, segmentService.getAllBizTags().size()),
                new HealthStatus.SnowflakeHealth(
                        snowflakeHealthy,
                        snowflakeService.getWorkerId().map(workerId -> (int) workerId.value()).orElse(null),
                        snowflakeService.getDatacenterId().map(datacenterId -> (int) datacenterId.value()).orElse(null)
                )
        );

        log.debug("Health status: {}", health);
        return health;
    }
}
