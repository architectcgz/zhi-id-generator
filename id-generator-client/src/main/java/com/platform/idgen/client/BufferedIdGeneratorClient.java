package com.platform.idgen.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.idgen.client.config.IdGeneratorClientConfig;
import com.platform.idgen.client.model.SnowflakeIdInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Buffered implementation of IdGeneratorClient.
 * 
 * This implementation pre-fetches IDs into a local buffer to reduce latency
 * and network round-trips. When the buffer runs low, it automatically refills
 * from the server (optionally in a background thread).
 * 
 * Thread-safe: This class is safe for concurrent use.
 */
public class BufferedIdGeneratorClient implements IdGeneratorClient {

    private static final Logger log = LoggerFactory.getLogger(BufferedIdGeneratorClient.class);

    private static final String API_BASE_PATH = "/api/v1/id";

    private final IdGeneratorClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Snowflake buffer
    private final BlockingQueue<Long> snowflakeBuffer;
    private final AtomicBoolean snowflakeRefilling = new AtomicBoolean(false);

    // Segment buffers (one per bizTag)
    private final ConcurrentHashMap<String, BlockingQueue<Long>> segmentBuffers;
    private final ConcurrentHashMap<String, AtomicBoolean> segmentRefilling;

    // Background executor for async refill
    private final ScheduledExecutorService refillExecutor;

    // Client state
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Cached server epoch for ID parsing
    private volatile Long cachedEpoch = null;

    /**
     * Create a new BufferedIdGeneratorClient with default configuration.
     */
    public BufferedIdGeneratorClient() {
        this(IdGeneratorClientConfig.defaults());
    }

    /**
     * Create a new BufferedIdGeneratorClient with the specified server URL.
     * 
     * @param serverUrl the ID Generator server URL
     */
    public BufferedIdGeneratorClient(String serverUrl) {
        this(IdGeneratorClientConfig.builder().serverUrl(serverUrl).build());
    }

    /**
     * Create a new BufferedIdGeneratorClient with the specified configuration.
     * 
     * @param config the client configuration
     */
    public BufferedIdGeneratorClient(IdGeneratorClientConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
            .build();

        if (config.isBufferEnabled()) {
            this.snowflakeBuffer = new LinkedBlockingQueue<>(config.getBufferSize());
            this.segmentBuffers = new ConcurrentHashMap<>();
            this.segmentRefilling = new ConcurrentHashMap<>();

            if (config.isAsyncRefill()) {
                this.refillExecutor = Executors.newScheduledThreadPool(2, r -> {
                    Thread t = new Thread(r, "id-generator-refill");
                    t.setDaemon(true);
                    return t;
                });
            } else {
                this.refillExecutor = null;
            }
        } else {
            this.snowflakeBuffer = null;
            this.segmentBuffers = null;
            this.segmentRefilling = null;
            this.refillExecutor = null;
        }

        log.info("BufferedIdGeneratorClient initialized with config: {}", config);
    }

    @Override
    public long nextSnowflakeId() {
        ensureNotClosed();

        if (!config.isBufferEnabled()) {
            return fetchSnowflakeIdFromServer();
        }

        // Try to get from buffer
        Long id = snowflakeBuffer.poll();
        if (id != null) {
            triggerSnowflakeRefillIfNeeded();
            return id;
        }

        // Buffer empty, fetch synchronously
        log.debug("Snowflake buffer empty, fetching from server");
        refillSnowflakeBuffer();
        
        id = snowflakeBuffer.poll();
        if (id != null) {
            return id;
        }

        throw new IdGeneratorException(
            IdGeneratorException.ErrorCode.BUFFER_EMPTY,
            "Failed to get Snowflake ID: buffer is empty and refill failed"
        );
    }

    @Override
    public List<Long> nextSnowflakeIds(int count) {
        ensureNotClosed();
        validateCount(count);

        if (!config.isBufferEnabled() || count > config.getBufferSize()) {
            return fetchSnowflakeIdsFromServer(count);
        }

        List<Long> ids = new ArrayList<>(count);
        
        // Try to drain from buffer first
        snowflakeBuffer.drainTo(ids, count);
        
        if (ids.size() == count) {
            triggerSnowflakeRefillIfNeeded();
            return ids;
        }

        // Need more IDs
        int remaining = count - ids.size();
        List<Long> moreIds = fetchSnowflakeIdsFromServer(remaining);
        ids.addAll(moreIds);

        triggerSnowflakeRefillIfNeeded();
        return ids;
    }

    @Override
    public long nextSegmentId(String bizTag) {
        ensureNotClosed();
        validateBizTag(bizTag);

        if (!config.isBufferEnabled()) {
            return fetchSegmentIdFromServer(bizTag);
        }

        BlockingQueue<Long> buffer = getOrCreateSegmentBuffer(bizTag);
        
        Long id = buffer.poll();
        if (id != null) {
            triggerSegmentRefillIfNeeded(bizTag, buffer);
            return id;
        }

        // Buffer empty, fetch synchronously
        log.debug("Segment buffer for '{}' empty, fetching from server", bizTag);
        refillSegmentBuffer(bizTag, buffer);
        
        id = buffer.poll();
        if (id != null) {
            return id;
        }

        throw new IdGeneratorException(
            IdGeneratorException.ErrorCode.BUFFER_EMPTY,
            "Failed to get Segment ID for '" + bizTag + "': buffer is empty and refill failed"
        );
    }

    @Override
    public List<Long> nextSegmentIds(String bizTag, int count) {
        ensureNotClosed();
        validateBizTag(bizTag);
        validateCount(count);

        if (!config.isBufferEnabled() || count > config.getBufferSize()) {
            return fetchSegmentIdsFromServer(bizTag, count);
        }

        BlockingQueue<Long> buffer = getOrCreateSegmentBuffer(bizTag);
        List<Long> ids = new ArrayList<>(count);
        
        buffer.drainTo(ids, count);
        
        if (ids.size() == count) {
            triggerSegmentRefillIfNeeded(bizTag, buffer);
            return ids;
        }

        int remaining = count - ids.size();
        List<Long> moreIds = fetchSegmentIdsFromServer(bizTag, remaining);
        ids.addAll(moreIds);

        triggerSegmentRefillIfNeeded(bizTag, buffer);
        return ids;
    }

    @Override
    public SnowflakeIdInfo parseSnowflakeId(long id) {
        ensureNotClosed();

        String url = config.getServerUrl() + API_BASE_PATH + "/snowflake/parse/" + id;
        JsonNode response = executeGetRequest(url);
        
        JsonNode data = response.get("data");
        if (data == null) {
            throw new IdGeneratorException(
                IdGeneratorException.ErrorCode.INVALID_RESPONSE,
                "Invalid response: missing 'data' field"
            );
        }

        return new SnowflakeIdInfo(
            id,
            data.get("timestamp").asLong(),
            data.get("datacenterId").asInt(),
            data.get("workerId").asInt(),
            data.get("sequence").asInt(),
            data.has("epoch") ? data.get("epoch").asLong() : getEpochFromServer()
        );
    }

    @Override
    public boolean isHealthy() {
        if (closed.get()) {
            return false;
        }

        try {
            String url = config.getServerUrl() + API_BASE_PATH + "/health";
            JsonNode response = executeGetRequest(url);
            JsonNode data = response.get("data");
            return data != null
                && data.has("status")
                && "UP".equals(data.get("status").asText());
        } catch (Exception e) {
            log.debug("Health check failed", e);
            return false;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("Closing BufferedIdGeneratorClient");
            
            if (refillExecutor != null) {
                refillExecutor.shutdown();
                try {
                    if (!refillExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        refillExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    refillExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            if (snowflakeBuffer != null) {
                snowflakeBuffer.clear();
            }
            if (segmentBuffers != null) {
                segmentBuffers.values().forEach(BlockingQueue::clear);
                segmentBuffers.clear();
            }
        }
    }

    // ==================== Private Helper Methods ====================

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IdGeneratorException(
                IdGeneratorException.ErrorCode.CLIENT_CLOSED,
                "IdGeneratorClient is closed"
            );
        }
    }

    private void validateBizTag(String bizTag) {
        if (bizTag == null || bizTag.isBlank()) {
            throw new IdGeneratorException(
                IdGeneratorException.ErrorCode.INVALID_ARGUMENT,
                "bizTag cannot be null or empty"
            );
        }
    }

    private void validateCount(int count) {
        if (count < 1 || count > 1000) {
            throw new IdGeneratorException(
                IdGeneratorException.ErrorCode.INVALID_ARGUMENT,
                "count must be between 1 and 1000"
            );
        }
    }

    private BlockingQueue<Long> getOrCreateSegmentBuffer(String bizTag) {
        return segmentBuffers.computeIfAbsent(bizTag, 
            k -> new LinkedBlockingQueue<>(config.getBufferSize()));
    }

    private void triggerSnowflakeRefillIfNeeded() {
        if (snowflakeBuffer.size() < config.getRefillThreshold()) {
            if (config.isAsyncRefill() && refillExecutor != null) {
                if (snowflakeRefilling.compareAndSet(false, true)) {
                    refillExecutor.submit(() -> {
                        try {
                            refillSnowflakeBuffer();
                        } finally {
                            snowflakeRefilling.set(false);
                        }
                    });
                }
            }
        }
    }

    private void triggerSegmentRefillIfNeeded(String bizTag, BlockingQueue<Long> buffer) {
        if (buffer.size() < config.getRefillThreshold()) {
            if (config.isAsyncRefill() && refillExecutor != null) {
                AtomicBoolean refilling = segmentRefilling.computeIfAbsent(bizTag, 
                    k -> new AtomicBoolean(false));
                if (refilling.compareAndSet(false, true)) {
                    refillExecutor.submit(() -> {
                        try {
                            refillSegmentBuffer(bizTag, buffer);
                        } finally {
                            refilling.set(false);
                        }
                    });
                }
            }
        }
    }

    private void refillSnowflakeBuffer() {
        try {
            int toFetch = config.getBufferSize() - snowflakeBuffer.size();
            if (toFetch <= 0) return;

            toFetch = Math.min(toFetch, config.getBatchFetchSize());
            List<Long> ids = fetchSnowflakeIdsFromServer(toFetch);
            
            for (Long id : ids) {
                if (!snowflakeBuffer.offer(id)) {
                    break; // Buffer full
                }
            }
            log.debug("Refilled Snowflake buffer with {} IDs, current size: {}", 
                ids.size(), snowflakeBuffer.size());
        } catch (Exception e) {
            log.warn("Failed to refill Snowflake buffer", e);
        }
    }

    private void refillSegmentBuffer(String bizTag, BlockingQueue<Long> buffer) {
        try {
            int toFetch = config.getBufferSize() - buffer.size();
            if (toFetch <= 0) return;

            toFetch = Math.min(toFetch, config.getBatchFetchSize());
            List<Long> ids = fetchSegmentIdsFromServer(bizTag, toFetch);
            
            for (Long id : ids) {
                if (!buffer.offer(id)) {
                    break; // Buffer full
                }
            }
            log.debug("Refilled Segment buffer for '{}' with {} IDs, current size: {}", 
                bizTag, ids.size(), buffer.size());
        } catch (Exception e) {
            log.warn("Failed to refill Segment buffer for '{}'", bizTag, e);
        }
    }

    private long fetchSnowflakeIdFromServer() {
        String url = config.getServerUrl() + API_BASE_PATH + "/snowflake";
        JsonNode response = executeGetRequest(url);
        return extractId(response);
    }

    private List<Long> fetchSnowflakeIdsFromServer(int count) {
        String url = config.getServerUrl() + API_BASE_PATH + "/snowflake/batch?count=" + count;
        JsonNode response = executeGetRequest(url);
        return extractIds(response);
    }

    private long fetchSegmentIdFromServer(String bizTag) {
        String url = config.getServerUrl() + API_BASE_PATH + "/segment/" + bizTag;
        JsonNode response = executeGetRequest(url);
        return extractId(response);
    }

    private List<Long> fetchSegmentIdsFromServer(String bizTag, int count) {
        String url = config.getServerUrl() + API_BASE_PATH + "/segment/" + bizTag + "/batch?count=" + count;
        JsonNode response = executeGetRequest(url);
        return extractIds(response);
    }

    private long getEpochFromServer() {
        if (cachedEpoch != null) {
            return cachedEpoch;
        }

        try {
            String url = config.getServerUrl() + API_BASE_PATH + "/snowflake/info";
            JsonNode response = executeGetRequest(url);
            JsonNode data = response.get("data");
            if (data != null && data.has("epoch")) {
                cachedEpoch = data.get("epoch").asLong();
                return cachedEpoch;
            }
        } catch (Exception e) {
            log.warn("Failed to get epoch from server", e);
        }

        // Default epoch if not available
        return 1577808000000L; // 2020-01-01 00:00:00 UTC
    }

    private JsonNode executeGetRequest(String url) {
        int retries = 0;
        Exception lastException = null;

        while (retries <= config.getMaxRetries()) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

                HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return objectMapper.readTree(response.body());
                }

                // Handle error response
                if (response.statusCode() == 404) {
                    throw new IdGeneratorException(
                        IdGeneratorException.ErrorCode.BIZ_TAG_NOT_FOUND,
                        "Resource not found: " + url
                    );
                }

                throw new IdGeneratorException(
                    IdGeneratorException.ErrorCode.SERVER_ERROR,
                    "Server returned error: " + response.statusCode() + " - " + response.body()
                );

            } catch (IdGeneratorException e) {
                throw e; // Don't retry business exceptions
            } catch (IOException e) {
                lastException = e;
                retries++;
                if (retries <= config.getMaxRetries()) {
                    log.debug("Request failed, retrying ({}/{}): {}", 
                        retries, config.getMaxRetries(), e.getMessage());
                    try {
                        Thread.sleep((long) Math.pow(2, retries) * 100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IdGeneratorException(
                            IdGeneratorException.ErrorCode.UNKNOWN,
                            "Interrupted during retry", ie
                        );
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IdGeneratorException(
                    IdGeneratorException.ErrorCode.UNKNOWN,
                    "Request interrupted", e
                );
            }
        }

        throw new IdGeneratorException(
            IdGeneratorException.ErrorCode.CONNECTION_FAILED,
            "Failed to connect to ID Generator server after " + config.getMaxRetries() + " retries",
            lastException
        );
    }

    private long extractId(JsonNode response) {
        JsonNode data = response.get("data");
        if (data == null) {
            throw new IdGeneratorException(
                IdGeneratorException.ErrorCode.INVALID_RESPONSE,
                "Invalid response: missing 'data' field"
            );
        }
        return data.asLong();
    }

    private List<Long> extractIds(JsonNode response) {
        JsonNode data = response.get("data");
        if (data == null || !data.isArray()) {
            throw new IdGeneratorException(
                IdGeneratorException.ErrorCode.INVALID_RESPONSE,
                "Invalid response: missing or invalid 'data' array"
            );
        }

        List<Long> ids = new ArrayList<>();
        for (JsonNode node : data) {
            ids.add(node.asLong());
        }
        return ids;
    }
}
