package com.platform.idgen.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for ID Generator Client.
 * 
 * All properties are prefixed with "id-generator.client".
 * 
 * Example configuration in application.yml:
 * <pre>
 * id-generator:
 *   client:
 *     enabled: true
 *     server-url: http://localhost:8010
 *     connect-timeout-ms: 5000
 *     read-timeout-ms: 5000
 *     max-retries: 3
 *     buffer-size: 100
 *     refill-threshold: 20
 *     batch-fetch-size: 50
 *     async-refill: true
 *     buffer-enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "id-generator.client")
public class IdGeneratorProperties {

    /**
     * Whether to enable the ID Generator client auto-configuration.
     */
    private boolean enabled = true;

    /**
     * The ID Generator server URL.
     */
    private String serverUrl = "http://localhost:8010";

    /**
     * Connection timeout in milliseconds.
     */
    private int connectTimeoutMs = 5000;

    /**
     * Read timeout in milliseconds.
     */
    private int readTimeoutMs = 5000;

    /**
     * Maximum number of retry attempts for failed requests.
     */
    private int maxRetries = 3;

    /**
     * Size of the local ID buffer.
     * Larger buffer reduces network round-trips but uses more memory.
     */
    private int bufferSize = 100;

    /**
     * Threshold to trigger buffer refill.
     * When buffer size drops below this value, a refill is triggered.
     */
    private int refillThreshold = 20;

    /**
     * Number of IDs to fetch in a single batch request.
     */
    private int batchFetchSize = 50;

    /**
     * Whether to refill buffer asynchronously in background thread.
     */
    private boolean asyncRefill = true;

    /**
     * Whether to enable the local ID buffer.
     * When disabled, each ID request goes directly to the server.
     */
    private boolean bufferEnabled = true;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getRefillThreshold() {
        return refillThreshold;
    }

    public void setRefillThreshold(int refillThreshold) {
        this.refillThreshold = refillThreshold;
    }

    public int getBatchFetchSize() {
        return batchFetchSize;
    }

    public void setBatchFetchSize(int batchFetchSize) {
        this.batchFetchSize = batchFetchSize;
    }

    public boolean isAsyncRefill() {
        return asyncRefill;
    }

    public void setAsyncRefill(boolean asyncRefill) {
        this.asyncRefill = asyncRefill;
    }

    public boolean isBufferEnabled() {
        return bufferEnabled;
    }

    public void setBufferEnabled(boolean bufferEnabled) {
        this.bufferEnabled = bufferEnabled;
    }
}
