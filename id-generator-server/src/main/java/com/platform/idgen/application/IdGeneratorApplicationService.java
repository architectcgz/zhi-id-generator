package com.platform.idgen.application;

import com.platform.idgen.domain.model.valueobject.BizTag;
import com.platform.idgen.domain.model.valueobject.SnowflakeId;
import com.platform.idgen.domain.service.SegmentDomainService;
import com.platform.idgen.domain.service.SnowflakeDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public Map<String, Object> getSegmentCacheInfo(String bizTag) {
        log.debug("Getting cache info for bizTag: {}", bizTag);
        
        Map<String, Object> info = new HashMap<>();
        info.put("bizTag", bizTag);
        info.put("initialized", segmentService.isInitialized());
        
        return info;
    }
    
    // ========== Snowflake Mode Methods ==========
    
    /**
     * Generate a single Snowflake ID.
     * 
     * @return generated Snowflake ID
     * @throws IllegalStateException if service not initialized
     */
    public long generateSnowflakeId() {
        log.debug("Generating snowflake ID");
        
        SnowflakeId id = snowflakeService.generateId();
        long value = id.value();
        
        log.debug("Generated snowflake ID: {}", value);
        return value;
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
    public Map<String, Object> parseSnowflakeId(long id) {
        log.debug("Parsing snowflake ID: {}", id);
        
        SnowflakeId snowflakeId = snowflakeService.parseId(id);
        
        Map<String, Object> info = new HashMap<>();
        info.put("id", id);
        
        // Get epoch from service to correctly parse timestamp
        long epoch = snowflakeService.getEpoch().orElse(0L);
        info.put("timestamp", snowflakeId.getTimestamp(epoch));
        info.put("datacenterId", snowflakeId.getDatacenterId());
        info.put("workerId", snowflakeId.getWorkerId());
        info.put("sequence", snowflakeId.getSequence());
        info.put("epoch", epoch);
        
        log.debug("Parsed snowflake ID: {}", info);
        return info;
    }
    
    /**
     * Get Snowflake Worker information.
     * 
     * @return map containing worker info (workerId, datacenterId, initialized)
     */
    public Map<String, Object> getSnowflakeInfo() {
        log.debug("Getting snowflake info");
        
        Map<String, Object> info = new HashMap<>();
        info.put("initialized", snowflakeService.isInitialized());
        
        snowflakeService.getWorkerId().ifPresent(workerId -> 
            info.put("workerId", workerId.value())
        );
        
        snowflakeService.getDatacenterId().ifPresent(datacenterId -> 
            info.put("datacenterId", datacenterId.value())
        );
        
        snowflakeService.getEpoch().ifPresent(epoch ->
            info.put("epoch", epoch)
        );
        
        return info;
    }
    
    // ========== Health Check ==========
    
    /**
     * Get health status of the ID generation service.
     * 
     * Aggregates health information from Segment and Snowflake domain services.
     * 
     * @return map containing health status information
     */
    public Map<String, Object> getHealthStatus() {
        log.debug("Getting health status");
        
        Map<String, Object> health = new HashMap<>();
        
        // Overall status
        boolean segmentHealthy = segmentService.isInitialized();
        boolean snowflakeHealthy = snowflakeService.isInitialized();
        
        health.put("status", (segmentHealthy && snowflakeHealthy) ? "UP" : "DEGRADED");
        
        // Segment service health
        Map<String, Object> segmentHealth = new HashMap<>();
        segmentHealth.put("initialized", segmentHealthy);
        segmentHealth.put("bizTagCount", segmentService.getAllBizTags().size());
        health.put("segment", segmentHealth);
        
        // Snowflake service health
        Map<String, Object> snowflakeHealth = new HashMap<>();
        snowflakeHealth.put("initialized", snowflakeHealthy);
        snowflakeService.getWorkerId().ifPresent(workerId -> 
            snowflakeHealth.put("workerId", workerId.value())
        );
        snowflakeService.getDatacenterId().ifPresent(datacenterId -> 
            snowflakeHealth.put("datacenterId", datacenterId.value())
        );
        health.put("snowflake", snowflakeHealth);
        
        log.debug("Health status: {}", health);
        return health;
    }
}
