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
 * - ZooKeeper integration settings
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
     * Worker ID (0-31)
     * Used when ZooKeeper is disabled or as fallback.
     * Set to -1 to force ZooKeeper registration.
     */
    @Min(value = -1, message = "Worker ID must be between -1 and 31 (-1 means auto-assign)")
    @Max(value = 31, message = "Worker ID must be between -1 and 31")
    private Integer workerId = -1;
    
    /**
     * Enable ZooKeeper for distributed Worker ID management.
     * When true, Worker ID is obtained from ZooKeeper.
     * When false, uses configured workerId value.
     */
    @NotNull(message = "Enable ZooKeeper flag cannot be null")
    private Boolean enableZookeeper = false;
    
    /**
     * Local cache file path for Worker ID persistence.
     * Used for fallback when ZooKeeper is unavailable.
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
     * 仅在 enable-zookeeper=false（数据库模式）时生效。
     */
    @NotNull(message = "Worker ID lease timeout cannot be null")
    private Duration workerIdLeaseTimeout = Duration.ofMinutes(10);

    /**
     * Worker ID 租约续期间隔。
     * 定时更新 lease_time 防止被其他实例回收。
     * 建议设置为 lease-timeout 的 1/3 左右，确保续期频率足够。
     * 仅在 enable-zookeeper=false（数据库模式）时生效。
     */
    @NotNull(message = "Worker ID renew interval cannot be null")
    private Duration workerIdRenewInterval = Duration.ofMinutes(3);

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
         * Enable startup clock validation.
         * When true, validates clock on startup against ZooKeeper recorded time.
         */
        @NotNull(message = "Startup check enabled flag cannot be null")
        private Boolean startupCheckEnabled = true;
        
        /**
         * ZooKeeper time synchronization interval (milliseconds).
         * How often to report current timestamp to ZooKeeper.
         */
        @NotNull(message = "ZK time sync interval cannot be null")
        @Min(value = 1000, message = "ZK time sync interval must be at least 1000ms")
        private Long zkTimeSyncInterval = 3000L;
        
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
        
        public Boolean getStartupCheckEnabled() {
            return startupCheckEnabled;
        }
        
        public void setStartupCheckEnabled(Boolean startupCheckEnabled) {
            this.startupCheckEnabled = startupCheckEnabled;
        }
        
        public Long getZkTimeSyncInterval() {
            return zkTimeSyncInterval;
        }
        
        public void setZkTimeSyncInterval(Long zkTimeSyncInterval) {
            this.zkTimeSyncInterval = zkTimeSyncInterval;
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
    
    public Boolean getEnableZookeeper() {
        return enableZookeeper;
    }
    
    public void setEnableZookeeper(Boolean enableZookeeper) {
        this.enableZookeeper = enableZookeeper;
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
}
