package com.platform.idgen.infrastructure.repository;

import com.platform.idgen.domain.exception.WorkerIdUnavailableException;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.repository.WorkerIdRepository;
import com.platform.idgen.infrastructure.config.SnowflakeProperties;
import com.platform.idgen.infrastructure.config.ZooKeeperProperties;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

/**
 * WorkerId Repository Implementation
 * 
 * Manages WorkerId registration via ZooKeeper with local file cache fallback.
 */
@Repository
public class WorkerIdRepositoryImpl implements WorkerIdRepository {
    
    private static final Logger log = LoggerFactory.getLogger(WorkerIdRepositoryImpl.class);
    
    private static final String WORKER_ID_KEY = "workerId";
    private static final String DATACENTER_ID_KEY = "datacenterId";
    private static final String SEQUENCE_NUMBER_KEY = "zkSequenceNumber";
    private static final String LAST_TIMESTAMP_KEY = "lastTimestamp";
    
    private final SnowflakeProperties snowflakeProperties;
    private final ZooKeeperProperties zkProperties;
    
    private CuratorFramework curatorClient;
    private String zkNodePath;
    private WorkerId registeredWorkerId;
    
    public WorkerIdRepositoryImpl(SnowflakeProperties snowflakeProperties, 
                                   ZooKeeperProperties zkProperties) {
        this.snowflakeProperties = snowflakeProperties;
        this.zkProperties = zkProperties;
    }
    
    @PostConstruct
    public void init() {
        if (snowflakeProperties.getEnableZookeeper()) {
            try {
                initZooKeeperClient();
            } catch (Exception e) {
                log.warn("Failed to initialize ZooKeeper client, will use local cache fallback", e);
            }
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (curatorClient != null) {
            curatorClient.close();
        }
    }
    
    private void initZooKeeperClient() {
        curatorClient = CuratorFrameworkFactory.builder()
                .connectString(zkProperties.getConnectionString())
                .sessionTimeoutMs(zkProperties.getSessionTimeoutMs())
                .connectionTimeoutMs(zkProperties.getConnectionTimeoutMs())
                .retryPolicy(new ExponentialBackoffRetry(
                        zkProperties.getRetry().getBaseSleepTimeMs(),
                        zkProperties.getRetry().getMaxRetries(),
                        zkProperties.getRetry().getMaxSleepTimeMs()))
                .build();
        
        curatorClient.start();
        log.info("ZooKeeper client initialized: {}", zkProperties.getConnectionString());
    }
    
    @Override
    public WorkerId registerWorkerId(String serviceName) throws WorkerIdUnavailableException {
        // Try configured static WorkerId first
        if (snowflakeProperties.getWorkerId() >= 0) {
            registeredWorkerId = new WorkerId(snowflakeProperties.getWorkerId());
            log.info("Using configured static WorkerId: {}", registeredWorkerId.value());
            return registeredWorkerId;
        }
        
        // Try ZooKeeper registration
        if (snowflakeProperties.getEnableZookeeper() && curatorClient != null) {
            try {
                registeredWorkerId = registerWithZooKeeper(serviceName);
                // Cache for fallback
                cacheWorkerId(registeredWorkerId, 
                             snowflakeProperties.getDatacenterId(), 
                             registeredWorkerId.value());
                return registeredWorkerId;
            } catch (Exception e) {
                log.warn("ZooKeeper registration failed, trying cache fallback", e);
            }
        }
        
        // Fallback to cached WorkerId
        Optional<WorkerId> cached = loadCachedWorkerId();
        if (cached.isPresent()) {
            registeredWorkerId = cached.get();
            log.info("Using cached WorkerId: {}", registeredWorkerId.value());
            return registeredWorkerId;
        }
        
        throw new WorkerIdUnavailableException(
                "Failed to obtain WorkerId from ZooKeeper and no valid cache available");
    }
    
    private WorkerId registerWithZooKeeper(String serviceName) throws Exception {
        String basePath = zkProperties.getBasePath() + "/" + serviceName + "/snowflake";
        
        // Ensure parent path exists
        if (curatorClient.checkExists().forPath(basePath) == null) {
            curatorClient.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(basePath);
        }
        
        // 使用 EPHEMERAL_SEQUENTIAL 节点，会话结束自动删除，避免废弃节点堆积
        zkNodePath = curatorClient.create()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(basePath + "/worker-", buildNodeData());
        
        // Extract sequence number from path
        String seqStr = zkNodePath.substring(zkNodePath.lastIndexOf('-') + 1);
        long sequenceNumber = Long.parseLong(seqStr);
        
        WorkerId workerId = WorkerId.fromSequenceNumber(sequenceNumber);
        log.info("Registered WorkerId {} from ZooKeeper path: {}", workerId.value(), zkNodePath);
        
        return workerId;
    }
    
    private byte[] buildNodeData() {
        String data = String.format("{\"timestamp\":%d,\"status\":\"online\"}", 
                                   System.currentTimeMillis());
        return data.getBytes();
    }
    
    @Override
    public Optional<WorkerId> loadCachedWorkerId() {
        Path cachePath = Paths.get(snowflakeProperties.getWorkerIdCachePath());
        if (!Files.exists(cachePath)) {
            return Optional.empty();
        }
        
        try (InputStream is = Files.newInputStream(cachePath)) {
            Properties props = new Properties();
            props.load(is);
            
            if (props.containsKey(WORKER_ID_KEY)) {
                int workerId = Integer.parseInt(props.getProperty(WORKER_ID_KEY));
                return Optional.of(new WorkerId(workerId));
            }
        } catch (Exception e) {
            log.warn("Failed to load cached WorkerId", e);
        }
        
        return Optional.empty();
    }
    
    @Override
    public void cacheWorkerId(WorkerId workerId, long datacenterId, long zkSequenceNumber) {
        Path cachePath = Paths.get(snowflakeProperties.getWorkerIdCachePath());
        
        try {
            // Ensure parent directory exists
            Files.createDirectories(cachePath.getParent());
            
            Properties props = new Properties();
            props.setProperty(WORKER_ID_KEY, String.valueOf(workerId.value()));
            props.setProperty(DATACENTER_ID_KEY, String.valueOf(datacenterId));
            props.setProperty(SEQUENCE_NUMBER_KEY, String.valueOf(zkSequenceNumber));
            
            try (OutputStream os = Files.newOutputStream(cachePath)) {
                props.store(os, "WorkerId Cache");
            }
            
            log.info("Cached WorkerId {} to {}", workerId.value(), cachePath);
            
        } catch (Exception e) {
            log.warn("Failed to cache WorkerId", e);
        }
    }
    
    @Override
    public void releaseWorkerId() {
        if (curatorClient != null && zkNodePath != null) {
            try {
                String data = String.format("{\"timestamp\":%d,\"status\":\"offline\"}",
                                           System.currentTimeMillis());
                curatorClient.setData().forPath(zkNodePath, data.getBytes());
                log.info("Marked ZooKeeper node as offline: {}", zkNodePath);
            } catch (Exception e) {
                log.warn("Failed to mark ZooKeeper node as offline", e);
            }
        }
        // 不在此处保存 lastTimestamp，由 SnowflakeDomainService.shutdown() 用精确值保存
    }
    
    @Override
    public Optional<Long> loadLastUsedTimestamp() {
        Path cachePath = Paths.get(snowflakeProperties.getWorkerIdCachePath());
        if (!Files.exists(cachePath)) {
            return Optional.empty();
        }
        
        try (InputStream is = Files.newInputStream(cachePath)) {
            Properties props = new Properties();
            props.load(is);
            
            if (props.containsKey(LAST_TIMESTAMP_KEY)) {
                long timestamp = Long.parseLong(props.getProperty(LAST_TIMESTAMP_KEY));
                return Optional.of(timestamp);
            }
        } catch (Exception e) {
            log.warn("Failed to load last used timestamp", e);
        }
        
        return Optional.empty();
    }
    
    @Override
    public void saveLastUsedTimestamp(long timestamp) {
        Path cachePath = Paths.get(snowflakeProperties.getWorkerIdCachePath());
        
        try {
            Files.createDirectories(cachePath.getParent());
            
            Properties props = new Properties();
            
            // Load existing properties
            if (Files.exists(cachePath)) {
                try (InputStream is = Files.newInputStream(cachePath)) {
                    props.load(is);
                }
            }
            
            // Update timestamp
            props.setProperty(LAST_TIMESTAMP_KEY, String.valueOf(timestamp));
            
            try (OutputStream os = Files.newOutputStream(cachePath)) {
                props.store(os, "WorkerId Cache");
            }
            
            log.debug("Saved last used timestamp: {}", timestamp);
            
        } catch (Exception e) {
            log.warn("Failed to save last used timestamp", e);
        }
    }
}
