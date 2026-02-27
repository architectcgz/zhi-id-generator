package com.platform.idgen.domain.port;

import java.util.Optional;

/**
 * 时间戳缓存端口（领域层定义，基础设施层实现）。
 * 用于 SnowflakeWorker 在重启恢复和时钟回拨时持久化/加载最后使用的时间戳。
 */
public interface WorkerTimestampCache {

    /**
     * 加载上次使用的时间戳
     *
     * @return 缓存的时间戳，不存在则返回 empty
     */
    Optional<Long> loadLastUsedTimestamp();

    /**
     * 保存最后使用的时间戳
     *
     * @param timestamp 时间戳
     */
    void saveLastUsedTimestamp(long timestamp);
}
