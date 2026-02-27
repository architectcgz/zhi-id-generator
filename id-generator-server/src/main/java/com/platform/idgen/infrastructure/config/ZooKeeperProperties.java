package com.platform.idgen.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * ZooKeeper Configuration Properties
 * 
 * Configures ZooKeeper connection and retry settings for distributed Worker ID management.
 */
@Component
@ConfigurationProperties(prefix = "id-generator.zookeeper")
@Validated
public class ZooKeeperProperties {
    
    /**
     * ZooKeeper connection string.
     * Format: host1:port1,host2:port2,host3:port3
     * Example: localhost:2181 or zk1:2181,zk2:2181,zk3:2181
     */
    @NotBlank(message = "ZooKeeper connection string cannot be empty")
    private String connectionString = "localhost:2181";
    
    /**
     * Session timeout (milliseconds).
     * ZooKeeper session timeout - if no heartbeat within this time, session expires.
     */
    @NotNull(message = "Session timeout cannot be null")
    @Min(value = 1000, message = "Session timeout must be at least 1000ms")
    private Integer sessionTimeoutMs = 60000;
    
    /**
     * Connection timeout (milliseconds).
     * Maximum time to wait for initial connection to ZooKeeper.
     */
    @NotNull(message = "Connection timeout cannot be null")
    @Min(value = 1000, message = "Connection timeout must be at least 1000ms")
    private Integer connectionTimeoutMs = 15000;
    
    /**
     * Base path for ZooKeeper nodes.
     * All Worker ID nodes will be created under this path.
     */
    @NotBlank(message = "Base path cannot be empty")
    private String basePath = "/leaf";
    
    /**
     * Service name for ZooKeeper node path.
     * Used to create service-specific path: {basePath}/{serviceName}/snowflake
     */
    @NotBlank(message = "Service name cannot be empty")
    private String serviceName = "id-generator";
    
    /**
     * Retry policy configuration
     */
    private Retry retry = new Retry();
    
    public static class Retry {
        /**
         * Maximum number of retry attempts.
         * Number of times to retry failed ZooKeeper operations.
         */
        @NotNull(message = "Max retries cannot be null")
        @Min(value = 0, message = "Max retries must be non-negative")
        private Integer maxRetries = 3;
        
        /**
         * Base sleep time between retries (milliseconds).
         * Actual sleep time grows exponentially: baseSleepTimeMs * (2 ^ retryCount)
         */
        @NotNull(message = "Base sleep time cannot be null")
        @Min(value = 100, message = "Base sleep time must be at least 100ms")
        private Integer baseSleepTimeMs = 1000;
        
        /**
         * Maximum sleep time between retries (milliseconds).
         * Caps exponential backoff to prevent excessive wait times.
         */
        @NotNull(message = "Max sleep time cannot be null")
        @Min(value = 1000, message = "Max sleep time must be at least 1000ms")
        private Integer maxSleepTimeMs = 10000;
        
        // Getters and Setters
        public Integer getMaxRetries() {
            return maxRetries;
        }
        
        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }
        
        public Integer getBaseSleepTimeMs() {
            return baseSleepTimeMs;
        }
        
        public void setBaseSleepTimeMs(Integer baseSleepTimeMs) {
            this.baseSleepTimeMs = baseSleepTimeMs;
        }
        
        public Integer getMaxSleepTimeMs() {
            return maxSleepTimeMs;
        }
        
        public void setMaxSleepTimeMs(Integer maxSleepTimeMs) {
            this.maxSleepTimeMs = maxSleepTimeMs;
        }
    }
    
    // Getters and Setters
    public String getConnectionString() {
        return connectionString;
    }
    
    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }
    
    public Integer getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }
    
    public void setSessionTimeoutMs(Integer sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }
    
    public Integer getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
    
    public void setConnectionTimeoutMs(Integer connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
    
    public String getBasePath() {
        return basePath;
    }
    
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public Retry getRetry() {
        return retry;
    }
    
    public void setRetry(Retry retry) {
        this.retry = retry;
    }
}
