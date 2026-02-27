package com.platform.idgen.infrastructure.zookeeper;

import java.util.Optional;

/**
 * Interface for caching WorkerId and timestamp information to local storage.
 * Used for fallback when ZooKeeper is unavailable and for clock backwards detection.
 */
public interface WorkerIdCache {
    
    /**
     * Load last used timestamp from cache.
     * 
     * @return Optional containing last timestamp, empty if unavailable
     */
    Optional<Long> loadLastUsedTimestamp();
    
    /**
     * Save last used timestamp to cache.
     * 
     * @param timestamp the timestamp to save
     */
    void saveLastUsedTimestamp(long timestamp);
}
