package com.platform.idgen.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Snowflake ID Generator Configuration Properties
 * 
 * Configures Snowflake algorithm parameters including:
 * - Worker ID and Datacenter ID
 * - Database lease settings for Worker ID allocation
 * - Clock backwards handling configuration
 * - Worker ID cache settings
 */
@Component
@ConfigurationProperties(prefix = "id-generator.snowflake")
@Validated
public class SnowflakeProperties {
    
    /**
     * Datacenter ID (0-31)
     * Used for Snowflake ID generation to identify the datacenter.
     */
    @NotNull(message = "Datacenter ID cannot be null")
    @Min(value = 0, message = "Datacenter ID must be between 0 and 31")
    @Max(value = 31, message = "Datacenter ID must be between 0 and 31")
    private Integer datacenterId;
    
    /**
     * Worker ID (0-31)。
     * 设置为 -1 时由数据库自动分配。
     */
    @Min(value = -1, message = "Worker ID must be between -1 and 31 (-1 means auto-assign)")
    @Max(value = 31, message = "Worker ID must be between -1 and 31")
    private Integer workerId = -1;
    
    /**
     * Local cache file path for Worker ID persistence.
     * 用于持久化最后一次使用的时间戳和当前 Worker ID 元数据。
     */
    @NotBlank(message = "Worker ID cache path cannot be empty")
    private String workerIdCachePath = "/data/leaf/workerID.properties";
    
    /**
     * Epoch timestamp (milliseconds since 1970-01-01)
     * Custom epoch for Snowflake algorithm.
     * Default: 2020-01-01 00:00:00 UTC (1577808000000L)
     */
    @NotNull(message = "Epoch cannot be null")
    @Min(value = 0, message = "Epoch must be a positive timestamp")
    private Long epoch = 1577808000000L;
    
    /**
     * Worker ID 租约超时时间。
     * 超过此时间未续期的 active 记录视为过期，可被其他实例回收。
     */
    @NotNull(message = "Worker ID lease timeout cannot be null")
    private Duration workerIdLeaseTimeout = Duration.ofMinutes(10);

    /**
     * Worker ID 租约续期间隔。
     * 定时更新 lease_time 防止被其他实例回收。
     * 建议设置为 lease-timeout 的 1/3 左右，确保续期频率足够。
     */
    @NotNull(message = "Worker ID renew interval cannot be null")
    private Duration workerIdRenewInterval = Duration.ofMinutes(3);

    /**
     * 预分配的备用 Worker ID 数量（用于时钟回拨切换），默认 1。
     * 启动时会额外抢占此数量的 Worker ID 作为备用，
     * 当发生大回拨时切换到备用 ID 继续生成，避免服务中断。
     * 配置路径：id-generator.snowflake.backup-worker-id-count
     */
    @Min(value = 0, message = "Backup worker ID count must be non-negative")
    private int backupWorkerIdCount = 1;

    /**
     * Clock backwards configuration
     */
    private ClockBackwards clockBackwards = new ClockBackwards();
    
    public static class ClockBackwards {
        /**
         * Maximum wait time for clock to catch up (milliseconds).
         * When clock drift <= maxWaitMs, wait for clock to catch up.
         * When clock drift > maxWaitMs, use cached timestamp.
         */
        @NotNull(message = "Max wait time cannot be null")
        @Min(value = 0, message = "Max wait time must be non-negative")
        private Long maxWaitMs = 5L;
        
        /**
         * Alert threshold for clock drift (milliseconds).
         * Trigger alert when clock drift exceeds this threshold.
         */
        @NotNull(message = "Alert threshold cannot be null")
        @Min(value = 0, message = "Alert threshold must be non-negative")
        private Long alertThresholdMs = 10L;

        /**
         * 启动时等待缓存时间戳追赶的最大毫秒数。
         * 超过此值认为缓存时间戳异常，直接使用当前时间。
         */
        @NotNull(message = "Max startup wait time cannot be null")
        @Min(value = 0, message = "Max startup wait time must be non-negative")
        private Long maxStartupWaitMs = 5000L;
        
        // Getters and Setters
        public Long getMaxWaitMs() {
            return maxWaitMs;
        }
        
        public void setMaxWaitMs(Long maxWaitMs) {
            this.maxWaitMs = maxWaitMs;
        }
        
        public Long getAlertThresholdMs() {
            return alertThresholdMs;
        }
        
        public void setAlertThresholdMs(Long alertThresholdMs) {
            this.alertThresholdMs = alertThresholdMs;
        }

        public Long getMaxStartupWaitMs() {
            return maxStartupWaitMs;
        }

        public void setMaxStartupWaitMs(Long maxStartupWaitMs) {
            this.maxStartupWaitMs = maxStartupWaitMs;
        }
    }
    
    // Getters and Setters
    public Integer getDatacenterId() {
        return datacenterId;
    }
    
    public void setDatacenterId(Integer datacenterId) {
        this.datacenterId = datacenterId;
    }
    
    public Integer getWorkerId() {
        return workerId;
    }
    
    public void setWorkerId(Integer workerId) {
        this.workerId = workerId;
    }
    
    public String getWorkerIdCachePath() {
        return workerIdCachePath;
    }
    
    public void setWorkerIdCachePath(String workerIdCachePath) {
        this.workerIdCachePath = workerIdCachePath;
    }
    
    public Long getEpoch() {
        return epoch;
    }
    
    public void setEpoch(Long epoch) {
        this.epoch = epoch;
    }
    
    public Duration getWorkerIdLeaseTimeout() {
        return workerIdLeaseTimeout;
    }

    public void setWorkerIdLeaseTimeout(Duration workerIdLeaseTimeout) {
        this.workerIdLeaseTimeout = workerIdLeaseTimeout;
    }

    public Duration getWorkerIdRenewInterval() {
        return workerIdRenewInterval;
    }

    public void setWorkerIdRenewInterval(Duration workerIdRenewInterval) {
        this.workerIdRenewInterval = workerIdRenewInterval;
    }

    public ClockBackwards getClockBackwards() {
        return clockBackwards;
    }

    public void setClockBackwards(ClockBackwards clockBackwards) {
        this.clockBackwards = clockBackwards;
    }

    public int getBackupWorkerIdCount() {
        return backupWorkerIdCount;
    }

    public void setBackupWorkerIdCount(int backupWorkerIdCount) {
        this.backupWorkerIdCount = backupWorkerIdCount;
    }
}
