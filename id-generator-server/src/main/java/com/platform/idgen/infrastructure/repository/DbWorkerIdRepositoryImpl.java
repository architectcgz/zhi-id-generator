package com.platform.idgen.infrastructure.repository;

import com.platform.idgen.domain.exception.WorkerIdUnavailableException;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.repository.WorkerIdRepository;
import com.platform.idgen.infrastructure.config.SnowflakeProperties;
import com.platform.idgen.infrastructure.persistence.mapper.WorkerIdAllocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于数据库的 Worker ID 分配实现。
 * 通过 SELECT ... FOR UPDATE SKIP LOCKED 抢占空闲 Worker ID，
 * 并使用定时任务续期租约，替代 ZooKeeper 方案。
 */
@Repository
@ConditionalOnProperty(name = "id-generator.snowflake.enable-zookeeper", havingValue = "false")
public class DbWorkerIdRepositoryImpl implements WorkerIdRepository {

    private static final Logger log = LoggerFactory.getLogger(DbWorkerIdRepositoryImpl.class);

    private static final String WORKER_ID_KEY = "workerId";
    private static final String DATACENTER_ID_KEY = "datacenterId";
    private static final String SEQUENCE_NUMBER_KEY = "zkSequenceNumber";
    private static final String LAST_TIMESTAMP_KEY = "lastTimestamp";

    private final SnowflakeProperties snowflakeProperties;
    private final WorkerIdAllocMapper workerIdAllocMapper;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "worker-id-lease-renew");
        thread.setDaemon(true);
        return thread;
    });

    /** 当前持有的 Worker ID，未分配时为 null */
    private volatile WorkerId registeredWorkerId;
    /** 当前实例标识（IP:port），用于续期校验 */
    private volatile String instanceId;
    /** 续期定时任务句柄 */
    private volatile ScheduledFuture<?> renewFuture;

    public DbWorkerIdRepositoryImpl(SnowflakeProperties snowflakeProperties,
                                     WorkerIdAllocMapper workerIdAllocMapper) {
        this.snowflakeProperties = snowflakeProperties;
        this.workerIdAllocMapper = workerIdAllocMapper;
    }

    @Override
    @Transactional
    public WorkerId registerWorkerId(String serviceName) throws WorkerIdUnavailableException {
        // 优先使用静态配置的 Worker ID
        if (snowflakeProperties.getWorkerId() >= 0) {
            registeredWorkerId = new WorkerId(snowflakeProperties.getWorkerId());
            log.info("使用静态配置的 WorkerId: {}", registeredWorkerId.value());
            return registeredWorkerId;
        }

        // 构建实例标识
        instanceId = buildInstanceId();

        // 尝试从数据库抢占
        try {
            WorkerId workerId = acquireFromDatabase();
            if (workerId != null) {
                registeredWorkerId = workerId;
                cacheWorkerId(workerId, snowflakeProperties.getDatacenterId(), workerId.value());
                startLeaseRenewal();
                log.info("从数据库抢占 WorkerId: {}, 实例: {}", workerId.value(), instanceId);
                return workerId;
            }
        } catch (Exception e) {
            log.warn("数据库抢占 WorkerId 失败，尝试本地缓存降级", e);
        }

        // 降级到本地缓存
        Optional<WorkerId> cached = loadCachedWorkerId();
        if (cached.isPresent()) {
            registeredWorkerId = cached.get();
            log.info("使用本地缓存的 WorkerId: {}", registeredWorkerId.value());
            return registeredWorkerId;
        }

        throw new WorkerIdUnavailableException(
                "无法从数据库获取 WorkerId，且本地缓存不可用");
    }

    /**
     * 从数据库抢占一个空闲的 Worker ID。
     * 在事务内执行 SELECT FOR UPDATE SKIP LOCKED + UPDATE。
     *
     * @return 抢占到的 WorkerId，无可用时返回 null
     */
    private WorkerId acquireFromDatabase() {
        long leaseTimeoutMinutes = snowflakeProperties.getWorkerIdLeaseTimeout().toMinutes();
        Integer availableId = workerIdAllocMapper.selectAvailableWorkerId(leaseTimeoutMinutes);
        if (availableId == null) {
            log.warn("没有可用的 Worker ID（全部被占用且未过期）");
            return null;
        }

        int updated = workerIdAllocMapper.acquireWorkerId(availableId, instanceId);
        if (updated > 0) {
            return new WorkerId(availableId);
        }

        log.warn("抢占 WorkerId {} 失败（可能被其他实例抢先占用）", availableId);
        return null;
    }

    /**
     * 启动租约续期定时任务。
     * 按配置的间隔定时更新 lease_time，防止被其他实例回收。
     */
    private void startLeaseRenewal() {
        stopLeaseRenewal();
        long intervalSeconds = snowflakeProperties.getWorkerIdRenewInterval().getSeconds();
        renewFuture = scheduler.scheduleAtFixedRate(this::renewLease,
                intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("租约续期任务已启动，间隔: {}s", intervalSeconds);
    }

    /** 停止租约续期定时任务 */
    private void stopLeaseRenewal() {
        if (renewFuture != null && !renewFuture.isCancelled()) {
            renewFuture.cancel(false);
            renewFuture = null;
            log.info("租约续期任务已停止");
        }
    }

    /** 执行一次租约续期 */
    private void renewLease() {
        if (registeredWorkerId == null || instanceId == null) {
            return;
        }
        try {
            int updated = workerIdAllocMapper.renewLease(registeredWorkerId.value(), instanceId);
            if (updated > 0) {
                log.debug("WorkerId {} 租约续期成功", registeredWorkerId.value());
            } else {
                log.warn("WorkerId {} 租约续期失败，可能已被回收", registeredWorkerId.value());
            }
        } catch (Exception e) {
            log.error("WorkerId {} 租约续期异常", registeredWorkerId.value(), e);
        }
    }

    @Override
    @PreDestroy
    public void releaseWorkerId() {
        stopLeaseRenewal();
        if (registeredWorkerId != null) {
            try {
                int updated = workerIdAllocMapper.releaseWorkerId(registeredWorkerId.value());
                if (updated > 0) {
                    log.info("释放 WorkerId: {}", registeredWorkerId.value());
                }
            } catch (Exception e) {
                log.warn("释放 WorkerId 失败: {}", registeredWorkerId.value(), e);
            }
        }
        scheduler.shutdown();
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
            log.warn("加载本地缓存 WorkerId 失败", e);
        }

        return Optional.empty();
    }

    @Override
    public void cacheWorkerId(WorkerId workerId, long datacenterId, long zkSequenceNumber) {
        Path cachePath = Paths.get(snowflakeProperties.getWorkerIdCachePath());

        try {
            Files.createDirectories(cachePath.getParent());

            Properties props = new Properties();
            props.setProperty(WORKER_ID_KEY, String.valueOf(workerId.value()));
            props.setProperty(DATACENTER_ID_KEY, String.valueOf(datacenterId));
            props.setProperty(SEQUENCE_NUMBER_KEY, String.valueOf(zkSequenceNumber));

            try (OutputStream os = Files.newOutputStream(cachePath)) {
                props.store(os, "WorkerId Cache");
            }

            log.info("缓存 WorkerId {} 到 {}", workerId.value(), cachePath);
        } catch (Exception e) {
            log.warn("缓存 WorkerId 失败", e);
        }
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
            log.warn("加载上次使用的时间戳失败", e);
        }

        return Optional.empty();
    }

    @Override
    public void saveLastUsedTimestamp(long timestamp) {
        Path cachePath = Paths.get(snowflakeProperties.getWorkerIdCachePath());

        try {
            Files.createDirectories(cachePath.getParent());

            Properties props = new Properties();

            // 加载已有属性，避免覆盖其他缓存数据
            if (Files.exists(cachePath)) {
                try (InputStream is = Files.newInputStream(cachePath)) {
                    props.load(is);
                }
            }

            props.setProperty(LAST_TIMESTAMP_KEY, String.valueOf(timestamp));

            try (OutputStream os = Files.newOutputStream(cachePath)) {
                props.store(os, "WorkerId Cache");
            }

            log.debug("保存最后使用的时间戳: {}", timestamp);
        } catch (Exception e) {
            log.warn("保存最后使用的时间戳失败", e);
        }
    }

    /**
     * 构建实例标识，格式为 IP:port。
     * 用于标识当前持有 Worker ID 的实例，续期时校验身份。
     *
     * @return 实例标识字符串
     */
    private String buildInstanceId() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String port = System.getProperty("server.port", "8011");
            return ip + ":" + port;
        } catch (Exception e) {
            log.warn("获取本机 IP 失败，使用 unknown 作为实例标识", e);
            return "unknown:" + System.currentTimeMillis();
        }
    }
}
