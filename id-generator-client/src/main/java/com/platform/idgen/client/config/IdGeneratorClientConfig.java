package com.platform.idgen.client.config;

/**
 * Configuration for ID Generator Client.
 * 
 * Use the builder pattern for easy configuration:
 * <pre>
 * IdGeneratorClientConfig config = IdGeneratorClientConfig.builder()
 *     .serverUrl("http://localhost:8010")
 *     .bufferSize(200)
 *     .build();
 * </pre>
 */
public class IdGeneratorClientConfig {

    /** Default server URL */
    public static final String DEFAULT_SERVER_URL = "http://localhost:8011";

    /** Default connection timeout in milliseconds */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    /** Default read timeout in milliseconds */
    public static final int DEFAULT_READ_TIMEOUT_MS = 5000;

    /** Default maximum retry attempts */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Default buffer size for pre-fetched IDs */
    public static final int DEFAULT_BUFFER_SIZE = 100;

    /** Default threshold to trigger buffer refill (percentage of buffer size) */
    public static final int DEFAULT_REFILL_THRESHOLD = 20;

    /** Default batch size for fetching IDs from server */
    public static final int DEFAULT_BATCH_FETCH_SIZE = 50;

    private final String serverUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxRetries;
    private final int bufferSize;
    private final int refillThreshold;
    private final int batchFetchSize;
    private final boolean asyncRefill;
    private final boolean bufferEnabled;

    private IdGeneratorClientConfig(Builder builder) {
        this.serverUrl = builder.serverUrl;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.maxRetries = builder.maxRetries;
        this.bufferSize = builder.bufferSize;
        this.refillThreshold = builder.refillThreshold;
        this.batchFetchSize = builder.batchFetchSize;
        this.asyncRefill = builder.asyncRefill;
        this.bufferEnabled = builder.bufferEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static IdGeneratorClientConfig defaults() {
        return builder().build();
    }

    // Getters

    public String getServerUrl() {
        return serverUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getRefillThreshold() {
        return refillThreshold;
    }

    public int getBatchFetchSize() {
        return batchFetchSize;
    }

    public boolean isAsyncRefill() {
        return asyncRefill;
    }

    public boolean isBufferEnabled() {
        return bufferEnabled;
    }

    /**
     * Builder for IdGeneratorClientConfig.
     */
    public static class Builder {
        private String serverUrl = DEFAULT_SERVER_URL;
        private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private int bufferSize = DEFAULT_BUFFER_SIZE;
        private int refillThreshold = DEFAULT_REFILL_THRESHOLD;
        private int batchFetchSize = DEFAULT_BATCH_FETCH_SIZE;
        private boolean asyncRefill = true;
        private boolean bufferEnabled = true;

        /**
         * Set the ID Generator server URL.
         * 
         * @param serverUrl server URL (e.g., "http://localhost:8010")
         * @return this builder
         */
        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        /**
         * Set the connection timeout in milliseconds.
         * 
         * @param connectTimeoutMs connection timeout (default: 5000)
         * @return this builder
         */
        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        /**
         * Set the read timeout in milliseconds.
         * 
         * @param readTimeoutMs read timeout (default: 5000)
         * @return this builder
         */
        public Builder readTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        /**
         * Set the maximum number of retry attempts.
         * 
         * @param maxRetries max retries (default: 3)
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Set the buffer size for pre-fetched IDs.
         * 
         * A larger buffer reduces network round-trips but uses more memory.
         * 
         * @param bufferSize buffer size (default: 100)
         * @return this builder
         */
        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * Set the refill threshold.
         * 
         * When the buffer size drops below this threshold, a refill is triggered.
         * 
         * @param refillThreshold refill threshold (default: 20)
         * @return this builder
         */
        public Builder refillThreshold(int refillThreshold) {
            this.refillThreshold = refillThreshold;
            return this;
        }

        /**
         * Set the batch size for fetching IDs from server.
         * 
         * @param batchFetchSize batch fetch size (default: 50)
         * @return this builder
         */
        public Builder batchFetchSize(int batchFetchSize) {
            this.batchFetchSize = batchFetchSize;
            return this;
        }

        /**
         * Enable or disable asynchronous buffer refill.
         * 
         * When enabled, buffer refill happens in background thread.
         * When disabled, refill blocks the current thread if buffer is low.
         * 
         * @param asyncRefill true for async refill (default: true)
         * @return this builder
         */
        public Builder asyncRefill(boolean asyncRefill) {
            this.asyncRefill = asyncRefill;
            return this;
        }

        /**
         * Enable or disable the ID buffer.
         * 
         * When disabled, each ID request goes directly to the server.
         * This is useful for testing or low-volume scenarios.
         * 
         * @param bufferEnabled true to enable buffer (default: true)
         * @return this builder
         */
        public Builder bufferEnabled(boolean bufferEnabled) {
            this.bufferEnabled = bufferEnabled;
            return this;
        }

        /**
         * Build the configuration.
         * 
         * @return the configuration
         * @throws IllegalArgumentException if configuration is invalid
         */
        public IdGeneratorClientConfig build() {
            validate();
            return new IdGeneratorClientConfig(this);
        }

        private void validate() {
            if (serverUrl == null || serverUrl.isBlank()) {
                throw new IllegalArgumentException("serverUrl cannot be null or empty");
            }
            if (connectTimeoutMs <= 0) {
                throw new IllegalArgumentException("connectTimeoutMs must be positive");
            }
            if (readTimeoutMs <= 0) {
                throw new IllegalArgumentException("readTimeoutMs must be positive");
            }
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries cannot be negative");
            }
            if (bufferSize < 1) {
                throw new IllegalArgumentException("bufferSize must be at least 1");
            }
            if (refillThreshold < 0 || refillThreshold >= bufferSize) {
                throw new IllegalArgumentException("refillThreshold must be between 0 and bufferSize");
            }
            if (batchFetchSize < 1) {
                throw new IllegalArgumentException("batchFetchSize must be at least 1");
            }
        }
    }

    @Override
    public String toString() {
        return "IdGeneratorClientConfig{" +
            "serverUrl='" + serverUrl + '\'' +
            ", connectTimeoutMs=" + connectTimeoutMs +
            ", readTimeoutMs=" + readTimeoutMs +
            ", maxRetries=" + maxRetries +
            ", bufferSize=" + bufferSize +
            ", refillThreshold=" + refillThreshold +
            ", batchFetchSize=" + batchFetchSize +
            ", asyncRefill=" + asyncRefill +
            ", bufferEnabled=" + bufferEnabled +
            '}';
    }
}
