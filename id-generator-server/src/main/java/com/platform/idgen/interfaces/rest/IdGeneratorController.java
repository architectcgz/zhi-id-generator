package com.platform.idgen.interfaces.rest;

import com.platform.idgen.application.IdGeneratorApplicationService;
import com.platform.idgen.interfaces.rest.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApiResponse<Long>> getSnowflakeId() {
        long id = applicationService.generateSnowflakeId();
        return ResponseEntity.ok(ApiResponse.success(id));
    }
    
    /**
     * Get batch of Snowflake IDs
     * GET /api/v1/id/snowflake/batch?count=10
     */
    @GetMapping("/snowflake/batch")
    public ResponseEntity<ApiResponse<List<Long>>> getBatchSnowflakeIds(
            @RequestParam(defaultValue = "10") int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
        if (count > 1000) {
            throw new IllegalArgumentException("Count must not exceed 1000");
        }
        List<Long> ids = applicationService.generateBatchSnowflakeIds(count);
        return ResponseEntity.ok(ApiResponse.success(ids));
    }
    
    /**
     * Parse Snowflake ID to view its components
     * GET /api/v1/id/snowflake/parse/{id}
     */
    @GetMapping("/snowflake/parse/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> parseSnowflakeId(@PathVariable long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be a positive number");
        }
        Map<String, Object> parsedInfo = applicationService.parseSnowflakeId(id);
        return ResponseEntity.ok(ApiResponse.success(parsedInfo));
    }
    
    /**
     * Get Snowflake service info
     * GET /api/v1/id/snowflake/info
     */
    @GetMapping("/snowflake/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSnowflakeInfo() {
        Map<String, Object> info = applicationService.getSnowflakeInfo();
        return ResponseEntity.ok(ApiResponse.success(info));
    }
    
    // ==================== Segment Mode Endpoints ====================
    
    /**
     * Get a single Segment ID
     * GET /api/v1/id/segment/{bizTag}
     */
    @GetMapping("/segment/{bizTag}")
    public ResponseEntity<ApiResponse<Long>> getSegmentId(@PathVariable String bizTag) {
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        if (bizTag.length() > 128) {
            throw new IllegalArgumentException("BizTag length must not exceed 128 characters");
        }
        long id = applicationService.generateSegmentId(bizTag);
        return ResponseEntity.ok(ApiResponse.success(id));
    }
    
    /**
     * Get batch of Segment IDs
     * GET /api/v1/id/segment/{bizTag}/batch?count=10
     */
    @GetMapping("/segment/{bizTag}/batch")
    public ResponseEntity<ApiResponse<List<Long>>> getBatchSegmentIds(
            @PathVariable String bizTag,
            @RequestParam(defaultValue = "10") int count) {
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        if (bizTag.length() > 128) {
            throw new IllegalArgumentException("BizTag length must not exceed 128 characters");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
        if (count > 1000) {
            throw new IllegalArgumentException("Count must not exceed 1000");
        }
        List<Long> ids = applicationService.generateBatchSegmentIds(bizTag, count);
        return ResponseEntity.ok(ApiResponse.success(ids));
    }
    
    /**
     * Get all business tags
     * GET /api/v1/id/tags
     */
    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<String>>> getAllTags() {
        List<String> tags = applicationService.getAllBizTags();
        return ResponseEntity.ok(ApiResponse.success(tags));
    }
    
    /**
     * Get Segment cache info
     * GET /api/v1/id/cache/{bizTag}
     */
    @GetMapping("/cache/{bizTag}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheInfo(@PathVariable String bizTag) {
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        if (bizTag.length() > 128) {
            throw new IllegalArgumentException("BizTag length must not exceed 128 characters");
        }
        Map<String, Object> cacheInfo = applicationService.getSegmentCacheInfo(bizTag);
        return ResponseEntity.ok(ApiResponse.success(cacheInfo));
    }
    
    /**
     * Health check
     * GET /api/v1/id/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> healthStatus = applicationService.getHealthStatus();
        healthStatus.put("service", "id-generator-service");
        healthStatus.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.success(healthStatus));
    }
}
