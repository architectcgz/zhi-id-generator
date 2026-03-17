package com.platform.idgen.interfaces.rest;

import com.platform.idgen.application.IdGeneratorApplicationService;
import com.platform.idgen.application.dto.HealthStatus;
import com.platform.idgen.application.dto.SegmentCacheInfo;
import com.platform.idgen.application.dto.SnowflakeInfo;
import com.platform.idgen.application.dto.SnowflakeParseInfo;
import com.platform.idgen.interfaces.rest.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    private static final int MAX_BATCH_COUNT = 1000;
    private static final int MAX_BIZ_TAG_LENGTH = 128;
    
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
        validateBatchCount(count);
        List<Long> ids = applicationService.generateBatchSnowflakeIds(count);
        return ResponseEntity.ok(ApiResponse.success(ids));
    }
    
    /**
     * Parse Snowflake ID to view its components
     * GET /api/v1/id/snowflake/parse/{id}
     */
    @GetMapping("/snowflake/parse/{id}")
    public ResponseEntity<ApiResponse<SnowflakeParseInfo>> parseSnowflakeId(@PathVariable long id) {
        validatePositiveId(id);
        SnowflakeParseInfo parsedInfo = applicationService.parseSnowflakeId(id);
        return ResponseEntity.ok(ApiResponse.success(parsedInfo));
    }
    
    /**
     * Get Snowflake service info
     * GET /api/v1/id/snowflake/info
     */
    @GetMapping("/snowflake/info")
    public ResponseEntity<ApiResponse<SnowflakeInfo>> getSnowflakeInfo() {
        SnowflakeInfo info = applicationService.getSnowflakeInfo();
        return ResponseEntity.ok(ApiResponse.success(info));
    }
    
    // ==================== Segment Mode Endpoints ====================
    
    /**
     * Get a single Segment ID
     * GET /api/v1/id/segment/{bizTag}
     */
    @GetMapping("/segment/{bizTag}")
    public ResponseEntity<ApiResponse<Long>> getSegmentId(@PathVariable String bizTag) {
        validateBizTag(bizTag);
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
        validateBizTag(bizTag);
        validateBatchCount(count);
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
    public ResponseEntity<ApiResponse<SegmentCacheInfo>> getCacheInfo(@PathVariable String bizTag) {
        validateBizTag(bizTag);
        SegmentCacheInfo cacheInfo = applicationService.getSegmentCacheInfo(bizTag);
        return ResponseEntity.ok(ApiResponse.success(cacheInfo));
    }
    
    /**
     * Health check
     * GET /api/v1/id/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthStatus>> health() {
        HealthStatus healthStatus = applicationService.getHealthStatus();
        return ResponseEntity.ok(ApiResponse.success(healthStatus));
    }

    private void validateBizTag(String bizTag) {
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
        if (bizTag.length() > MAX_BIZ_TAG_LENGTH) {
            throw new IllegalArgumentException(
                    "BizTag length must not exceed " + MAX_BIZ_TAG_LENGTH + " characters");
        }
    }

    private void validateBatchCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
        if (count > MAX_BATCH_COUNT) {
            throw new IllegalArgumentException("Count must not exceed " + MAX_BATCH_COUNT);
        }
    }

    private void validatePositiveId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be a positive number");
        }
    }
}
