package com.platform.idgen.application.dto;

/**
 * Snowflake 服务运行信息。
 */
public record SnowflakeInfo(
        boolean initialized,
        Integer workerId,
        Integer datacenterId,
        Long epoch) {}
