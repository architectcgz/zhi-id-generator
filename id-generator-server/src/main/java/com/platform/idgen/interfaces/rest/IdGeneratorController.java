package com.platform.idgen.interfaces.rest;

import com.platform.idgen.application.IdGeneratorApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ID Generator REST API
 * Supports Segment mode and Snowflake mode.
 * 
 * This controller is lightweight, focusing only on HTTP concerns.
 * All business logic is delegated to IdGeneratorApplicationService.
 */
@RestController
@RequestMapping("/api/v1/id")
public class IdGeneratorController {
    
    private static final Logger log = LoggerFactory.getLogger(IdGeneratorController.class);
    
    private final IdGeneratorApplicationService applicationService;
    
    public IdGeneratorController(IdGeneratorApplicationService applicationService) {
        this.applicationService = applicationService;
    }
    
    // ==================== Snowflake Mode Endpoints ====================
    
    /**
     * Get a single Snowflake ID
     * GET /api/v1/id/snowflake
     */
    @GetMapping("/snowflake")
    public ResponseEntity<Map<String, Object>> getSnowflakeId() {
        Map<String, Object> response = new HashMap<>();
        long id = applicationService.generateSnowflakeId();
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", id);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get batch of Snowflake IDs
     * GET /api/v1/id/snowflake/batch?count=10
     */
    @GetMapping("/snowflake/batch")
    public ResponseEntity<Map<String, Object>> getBatchSnowflakeIds(
            @RequestParam(defaultValue = "10") int count) {
        
        // Validate count parameter
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
        if (count > 1000) {
            throw new IllegalArgumentException("Count must not exceed 1000");
        }
        
        Map<String, Object> response = new HashMap<>();
        List<Long> ids = applicationService.generateBatchSnowflakeIds(count);
        
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", ids);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Parse Snowflake ID to view its components
     * GET /api/v1/id/snowflake/parse/{id}
     */
    @GetMapping("/snowflake/parse/{id}")
    public ResponseEntity<Map<String, Object>> parseSnowflakeId(@PathVariable long id) {
        // Validate ID parameter (must be positive)
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be a positive number");
        }
        
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> parsedInfo = applicationService.parseSnowflakeId(id);
        
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", parsedInfo);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get Snowflake service info
     * GET /api/v1/id/snowflake/info
     */
    @GetMapping("/snowflake/info")
    public ResponseEntity<Map<String, Object>> getSnowflakeInfo() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> info = applicationService.getSnowflakeInfo();
        
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", info);
        return ResponseEntity.ok(response);
    }
    
    // ==================== Segment Mode Endpoints ====================
    
    /**
     * Get a single Segment ID
     * GET /api/v1/id/segment/{bizTag}
     */
    @GetMapping("/segment/{bizTag}")
    public ResponseEntity<Map<String, Object>> getSegmentId(@PathVariable String bizTag) {
        // Validate bizTag parameter
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        if (bizTag.length() > 128) {
            throw new IllegalArgumentException("BizTag length must not exceed 128 characters");
        }
        
        Map<String, Object> response = new HashMap<>();
        long id = applicationService.generateSegmentId(bizTag);
        
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", id);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get batch of Segment IDs
     * GET /api/v1/id/segment/{bizTag}/batch?count=10
     */
    @GetMapping("/segment/{bizTag}/batch")
    public ResponseEntity<Map<String, Object>> getBatchSegmentIds(
            @PathVariable String bizTag,
            @RequestParam(defaultValue = "10") int count) {
        
        // Validate bizTag parameter
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        if (bizTag.length() > 128) {
            throw new IllegalArgumentException("BizTag length must not exceed 128 characters");
        }
        
        // Validate count parameter
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
        if (count > 1000) {
            throw new IllegalArgumentException("Count must not exceed 1000");
        }
        
        Map<String, Object> response = new HashMap<>();
        List<Long> ids = applicationService.generateBatchSegmentIds(bizTag, count);
        
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", ids);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all business tags
     * GET /api/v1/id/tags
     */
    @GetMapping("/tags")
    public ResponseEntity<Map<String, Object>> getAllTags() {
        List<String> tags = applicationService.getAllBizTags();
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", tags);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get Segment cache info
     * GET /api/v1/id/cache/{bizTag}
     */
    @GetMapping("/cache/{bizTag}")
    public ResponseEntity<Map<String, Object>> getCacheInfo(@PathVariable String bizTag) {
        // Validate bizTag parameter
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        if (bizTag.length() > 128) {
            throw new IllegalArgumentException("BizTag length must not exceed 128 characters");
        }
        
        Map<String, Object> cacheInfo = applicationService.getSegmentCacheInfo(bizTag);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", cacheInfo);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check
     * GET /api/v1/id/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthStatus = applicationService.getHealthStatus();
        healthStatus.put("service", "id-generator-service");
        healthStatus.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(healthStatus);
    }
}
