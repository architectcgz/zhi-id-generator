package com.platform.idgen.application.dto;

/**
 * Snowflake ID 解析结果。
 */
public record SnowflakeParseInfo(
        long id,
        long timestamp,
        long datacenterId,
        long workerId,
        long sequence,
        long epoch) {}
