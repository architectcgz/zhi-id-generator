package com.platform.example.controller;

import com.platform.idgen.client.IdGeneratorClient;
import com.platform.idgen.client.model.SnowflakeIdInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo controller showing ID Generator usage examples.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final IdGeneratorClient idGeneratorClient;

    /**
     * Generate a single Snowflake ID.
     * 
     * Example: GET /api/demo/snowflake
     */
    @GetMapping("/snowflake")
    public Map<String, Object> generateSnowflakeId() {
        long id = idGeneratorClient.nextSnowflakeId();
        log.info("Generated Snowflake ID: {}", id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("mode", "snowflake");
        return response;
    }

    /**
     * Generate multiple Snowflake IDs.
     * 
     * Example: GET /api/demo/snowflake/batch?count=10
     */
    @GetMapping("/snowflake/batch")
    public Map<String, Object> generateSnowflakeIds(@RequestParam(defaultValue = "10") int count) {
        List<Long> ids = idGeneratorClient.nextSnowflakeIds(count);
        log.info("Generated {} Snowflake IDs", ids.size());
        
        Map<String, Object> response = new HashMap<>();
        response.put("ids", ids);
        response.put("count", ids.size());
        response.put("mode", "snowflake");
        return response;
    }

    /**
     * Parse a Snowflake ID to extract its components.
     * 
     * Example: GET /api/demo/snowflake/parse/123456789
     */
    @GetMapping("/snowflake/parse/{id}")
    public Map<String, Object> parseSnowflakeId(@PathVariable long id) {
        SnowflakeIdInfo info = idGeneratorClient.parseSnowflakeId(id);
        log.info("Parsed Snowflake ID: {}", info);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("timestamp", info.getTimestamp());
        response.put("datacenterId", info.getDatacenterId());
        response.put("workerId", info.getWorkerId());
        response.put("sequence", info.getSequence());
        response.put("timestampStr", info.getTimestampStr());
        return response;
    }

    /**
     * Generate a single Segment ID for a business tag.
     * 
     * Example: GET /api/demo/segment/user
     */
    @GetMapping("/segment/{bizTag}")
    public Map<String, Object> generateSegmentId(@PathVariable String bizTag) {
        long id = idGeneratorClient.nextSegmentId(bizTag);
        log.info("Generated Segment ID for {}: {}", bizTag, id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("bizTag", bizTag);
        response.put("mode", "segment");
        return response;
    }

    /**
     * Generate multiple Segment IDs for a business tag.
     * 
     * Example: GET /api/demo/segment/order/batch?count=10
     */
    @GetMapping("/segment/{bizTag}/batch")
    public Map<String, Object> generateSegmentIds(
            @PathVariable String bizTag,
            @RequestParam(defaultValue = "10") int count) {
        List<Long> ids = idGeneratorClient.nextSegmentIds(bizTag, count);
        log.info("Generated {} Segment IDs for {}", ids.size(), bizTag);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ids", ids);
        response.put("count", ids.size());
        response.put("bizTag", bizTag);
        response.put("mode", "segment");
        return response;
    }

    /**
     * Check ID Generator service health.
     * 
     * Example: GET /api/demo/health
     */
    @GetMapping("/health")
    public Map<String, Object> checkHealth() {
        boolean healthy = idGeneratorClient.isHealthy();
        log.info("ID Generator health check: {}", healthy);
        
        Map<String, Object> response = new HashMap<>();
        response.put("healthy", healthy);
        response.put("status", healthy ? "UP" : "DOWN");
        return response;
    }
}
