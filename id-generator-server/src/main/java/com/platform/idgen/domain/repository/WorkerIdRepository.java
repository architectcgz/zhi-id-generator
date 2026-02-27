package com.platform.idgen.domain.repository;

import com.platform.idgen.domain.exception.WorkerIdUnavailableException;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.port.WorkerTimestampCache;

import java.util.Optional;

/**
 * WorkerId Repository Interface
 *
 * Abstracts WorkerId registration, caching, and persistence operations.
 * 继承 WorkerTimestampCache 以统一时间戳持久化接口，消除适配器。
 */
public interface WorkerIdRepository extends WorkerTimestampCache {
    
    /**
     * Register a WorkerId for service instance.
     * Tries ZooKeeper first, falls back to cache if ZooKeeper fails.
     * 
     * @param serviceName service name for building ZooKeeper path
     * @return registered WorkerId
     * @throws WorkerIdUnavailableException when both ZooKeeper and cache fail
     */
    WorkerId registerWorkerId(String serviceName) throws WorkerIdUnavailableException;
    
    /**
     * Load WorkerId from local cache file.
     * Used as fallback when ZooKeeper is unavailable.
     * 
     * @return Optional containing cached WorkerId, empty if not found
     */
    Optional<WorkerId> loadCachedWorkerId();
    
    /**
     * Cache WorkerId and its metadata to local file.
     * Stores workerId, datacenterId, zkSequenceNumber for recovery.
     * 
     * @param workerId the WorkerId to cache
     * @param datacenterId the datacenter ID
     * @param zkSequenceNumber the original ZooKeeper sequence number
     */
    void cacheWorkerId(WorkerId workerId, long datacenterId, long zkSequenceNumber);
    
    /**
     * Release WorkerId by marking ZooKeeper node as offline.
     * Called during graceful shutdown. Does not delete node to preserve WorkerId for restart.
     */
    void releaseWorkerId();
}
