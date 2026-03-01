package com.platform.idgen.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Worker ID 分配表 MyBatis Mapper。
 * 提供基于数据库的 Worker ID 抢占、续期、释放操作。
 */
@Mapper
public interface WorkerIdAllocMapper {

    /**
     * 抢占一个空闲的 Worker ID。
     * 查找状态为 released 或租约已过期的记录，使用 FOR UPDATE SKIP LOCKED 避免竞争阻塞。
     *
     * @param leaseTimeoutMinutes 租约超时时间（分钟），超过此时间的 active 记录视为可回收
     * @return 可用的 Worker ID，无可用时返回 null
     */
    Integer selectAvailableWorkerId(@Param("leaseTimeoutMinutes") long leaseTimeoutMinutes);

    /**
     * 占用指定的 Worker ID，将状态更新为 active。
     * 增加防御性 WHERE 条件，只允许抢占 released 或租约已过期的记录，
     * 防止并发场景下误覆盖其他实例正在持有的 Worker ID。
     *
     * @param workerId            要占用的 Worker ID
     * @param instanceId          实例标识（IP:port）
     * @param leaseTimeoutMinutes 租约超时时间（分钟），用于判断 active 记录是否已过期
     * @return 更新行数，1 表示成功，0 表示已被其他实例抢占
     */
    int acquireWorkerId(@Param("workerId") int workerId,
                        @Param("instanceId") String instanceId,
                        @Param("leaseTimeoutMinutes") long leaseTimeoutMinutes);

    /**
     * 续期：更新 lease_time 为当前时间。
     *
     * @param workerId   当前持有的 Worker ID
     * @param instanceId 实例标识，用于校验持有者身份
     * @return 更新行数，1 表示成功
     */
    int renewLease(@Param("workerId") int workerId,
                   @Param("instanceId") String instanceId);

    /**
     * 释放 Worker ID，将状态更新为 released 并清空实例标识。
     * 增加 instance_id 校验，防止误释放其他实例持有的 Worker ID。
     *
     * @param workerId   要释放的 Worker ID
     * @param instanceId 实例标识，用于校验持有者身份
     * @return 更新行数
     */
    int releaseWorkerId(@Param("workerId") int workerId,
                        @Param("instanceId") String instanceId);
}
