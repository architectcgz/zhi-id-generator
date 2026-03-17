package com.platform.idgen.application.dto;

/**
 * 服务健康状态查询结果。
 */
public record HealthStatus(
        String status,
        String service,
        long timestamp,
        SegmentHealth segment,
        SnowflakeHealth snowflake) {

    public record SegmentHealth(boolean initialized, int bizTagCount) {}

    public record SnowflakeHealth(boolean initialized, Integer workerId, Integer datacenterId) {}
}
