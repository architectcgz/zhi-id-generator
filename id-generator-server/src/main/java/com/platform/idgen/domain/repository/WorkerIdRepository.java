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

    /**
     * 检查当前持有的 Worker ID 是否仍然有效。
     * DB 模式下，续期连续失败达到阈值后返回 false，表示租约可能已被回收。
     * ZK 模式下始终返回 true（由 ZooKeeper 会话保证有效性）。
     *
     * @return true 表示 Worker ID 有效，可继续生成 ID；false 表示存在冲突风险，应停止生成
     */
    default boolean isWorkerIdValid() {
        return true;
    }

    /**
     * 获取一个备用 Worker ID 用于时钟回拨切换。
     * 消费后该备用 ID 从备用列表中移除，不可重复使用。
     * ZK 模式不支持备用 ID，默认返回 empty。
     *
     * @return 备用 Worker ID，如果没有可用的备用 ID 则返回 Optional.empty()
     */
    default Optional<WorkerId> consumeBackupWorkerId() {
        return Optional.empty();
    }
}
