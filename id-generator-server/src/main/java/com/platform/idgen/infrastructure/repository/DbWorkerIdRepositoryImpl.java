package com.platform.idgen.infrastructure.repository;

import com.platform.idgen.domain.exception.WorkerIdUnavailableException;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.repository.WorkerIdRepository;
import com.platform.idgen.infrastructure.config.SnowflakeProperties;
import com.platform.idgen.infrastructure.persistence.mapper.WorkerIdAllocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * 续期连续失败阈值：超过此次数后标记 Worker ID 为无效，
     * 防止租约已被回收的情况下继续生成可能重复的 ID。
     */
    private static final int MAX_RENEW_FAIL_COUNT = 2;

    private final SnowflakeProperties snowflakeProperties;
    private final WorkerIdAllocMapper workerIdAllocMapper;
    /** Spring Environment，用于可靠获取 server.port 配置 */
    private final Environment environment;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "worker-id-lease-renew");
        thread.setDaemon(true);
        return thread;
    });

    /** 当前持有的主用 Worker ID，未分配时为 null */
    private volatile WorkerId registeredWorkerId;
    /** 备用 Worker ID 队列，用于时钟回拨时切换，线程安全 */
    private final Queue<WorkerId> backupWorkerIds = new ConcurrentLinkedQueue<>();
    /** 当前实例标识（IP:port），用于续期校验 */
    private volatile String instanceId;
    /** 续期定时任务句柄 */
    private volatile ScheduledFuture<?> renewFuture;
    /** 续期连续失败计数器 */
    private final AtomicInteger renewFailCount = new AtomicInteger(0);
    /**
     * Worker ID 有效性标志。
     * 续期连续失败达到阈值后置为 true，表示租约可能已被回收，
     * 此时继续生成 ID 存在与其他实例冲突的风险。
     */
    private volatile boolean workerIdInvalid = false;

    public DbWorkerIdRepositoryImpl(SnowflakeProperties snowflakeProperties,
                                     WorkerIdAllocMapper workerIdAllocMapper,
                                     Environment environment) {
        this.snowflakeProperties = snowflakeProperties;
        this.workerIdAllocMapper = workerIdAllocMapper;
        this.environment = environment;
    }

    @Override
    public WorkerId registerWorkerId(String serviceName) throws WorkerIdUnavailableException {
        // 优先使用静态配置的 Worker ID
        if (snowflakeProperties.getWorkerId() >= 0) {
            registeredWorkerId = new WorkerId(snowflakeProperties.getWorkerId());
            log.info("使用静态配置的 WorkerId: {}", registeredWorkerId.value());
            return registeredWorkerId;
        }

        // 构建实例标识
        instanceId = buildInstanceId();

        // 尝试从数据库抢占（SELECT FOR UPDATE SKIP LOCKED + UPDATE 在 Mapper 层保证原子性）
        try {
            WorkerId workerId = acquireFromDatabase();
            if (workerId != null) {
                registeredWorkerId = workerId;
                // DB 模式下无 ZK 序列号，传 -1 标识
                cacheWorkerId(workerId, snowflakeProperties.getDatacenterId(), -1);

                // 预分配备用 Worker ID，用于时钟回拨时切换
                acquireBackupWorkerIds();

                startLeaseRenewal();
                log.info("从数据库抢占 WorkerId: {}, 实例: {}, 备用 ID 数量: {}",
                        workerId.value(), instanceId, backupWorkerIds.size());
                return workerId;
            }
        } catch (Exception e) {
            log.warn("数据库抢占 WorkerId 失败，尝试本地缓存降级", e);
        }

        // 降级到本地缓存
        Optional<WorkerId> cached = loadCachedWorkerId();
        if (cached.isPresent()) {
            registeredWorkerId = cached.get();
            // DB 模式下使用本地缓存降级，该 ID 可能已被其他实例占用，存在 ID 冲突风险
            log.warn("DB 模式下使用本地缓存降级恢复 Worker ID {}，该 ID 可能已被其他实例占用，存在 ID 冲突风险",
                    registeredWorkerId.value());
            return registeredWorkerId;
        }

        throw new WorkerIdUnavailableException(
                "无法从数据库获取 WorkerId，且本地缓存不可用");
    }

    /**
     * 从数据库抢占一个空闲的 Worker ID。
     * SELECT FOR UPDATE SKIP LOCKED 与 UPDATE 的防御性 WHERE 条件在 Mapper 层保证原子性，
     * 无需在此处加事务（避免事务范围过大）。
     *
     * @return 抢占到的 WorkerId，无可用时返回 null
     */
    @Transactional
    public WorkerId acquireFromDatabase() {
        long leaseTimeoutMinutes = snowflakeProperties.getWorkerIdLeaseTimeout().toMinutes();
        Integer availableId = workerIdAllocMapper.selectAvailableWorkerId(leaseTimeoutMinutes);
        if (availableId == null) {
            log.warn("没有可用的 Worker ID（全部被占用且未过期）");
            return null;
        }

        int updated = workerIdAllocMapper.acquireWorkerId(availableId, instanceId, leaseTimeoutMinutes);
        if (updated > 0) {
            return new WorkerId(availableId);
        }

        log.warn("抢占 WorkerId {} 失败（可能被其他实例抢先占用）", availableId);
        return null;
    }

    /**
     * 预分配备用 Worker ID，用于时钟回拨时切换。
     * 按配置数量尝试抢占，抢占失败不影响主 Worker ID 的正常使用。
     * 备用 ID 用完后不再补充，避免频繁抢占数据库。
     */
    private void acquireBackupWorkerIds() {
        int backupCount = snowflakeProperties.getBackupWorkerIdCount();
        if (backupCount <= 0) {
            log.info("备用 Worker ID 数量配置为 {}，跳过预分配", backupCount);
            return;
        }

        int acquired = 0;
        for (int i = 0; i < backupCount; i++) {
            try {
                WorkerId backupId = acquireFromDatabase();
                if (backupId != null) {
                    backupWorkerIds.offer(backupId);
                    acquired++;
                    log.info("预分配备用 WorkerId: {}", backupId.value());
                } else {
                    log.warn("预分配备用 WorkerId 失败（第 {} 个），无可用 ID", i + 1);
                    break;
                }
            } catch (Exception e) {
                log.warn("预分配备用 WorkerId 异常（第 {} 个）", i + 1, e);
                break;
            }
        }

        if (acquired < backupCount) {
            log.warn("备用 Worker ID 预分配不足：期望 {}，实际 {}", backupCount, acquired);
        }
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

    /**
     * 执行一次租约续期，同时续期主用 Worker ID 和所有备用 Worker ID。
     * 主用 ID 续期连续失败达到 MAX_RENEW_FAIL_COUNT 后，将 workerIdInvalid 置为 true，
     * 上层服务应通过 isWorkerIdValid() 检查后拒绝继续生成 ID，防止 ID 冲突。
     * 备用 ID 续期失败仅记录警告，不影响主用 ID 的有效性判断。
     */
    private void renewLease() {
        if (registeredWorkerId == null || instanceId == null) {
            return;
        }
        // 续期主用 Worker ID
        try {
            int updated = workerIdAllocMapper.renewLease(registeredWorkerId.value(), instanceId);
            if (updated > 0) {
                // 续期成功，重置失败计数器
                renewFailCount.set(0);
                log.debug("WorkerId {} 租约续期成功", registeredWorkerId.value());
            } else {
                int failCount = renewFailCount.incrementAndGet();
                log.warn("WorkerId {} 租约续期失败（第 {} 次），可能已被回收", registeredWorkerId.value(), failCount);
                if (failCount >= MAX_RENEW_FAIL_COUNT) {
                    workerIdInvalid = true;
                    log.error("WorkerId {} 续期连续失败 {} 次，已标记为无效，停止 ID 生成以防止冲突",
                            registeredWorkerId.value(), failCount);
                }
            }
        } catch (Exception e) {
            int failCount = renewFailCount.incrementAndGet();
            log.error("WorkerId {} 租约续期异常（第 {} 次）", registeredWorkerId.value(), failCount, e);
            if (failCount >= MAX_RENEW_FAIL_COUNT) {
                workerIdInvalid = true;
                log.error("WorkerId {} 续期连续异常 {} 次，已标记为无效，停止 ID 生成以防止冲突",
                        registeredWorkerId.value(), failCount);
            }
        }

        // 续期所有备用 Worker ID，续期失败时从队列移除，避免后续切换到已失效的备用 ID
        Iterator<WorkerId> iterator = backupWorkerIds.iterator();
        while (iterator.hasNext()) {
            WorkerId backupId = iterator.next();
            try {
                int updated = workerIdAllocMapper.renewLease(backupId.value(), instanceId);
                if (updated > 0) {
                    log.debug("备用 WorkerId {} 租约续期成功", backupId.value());
                } else {
                    log.warn("备用 WorkerId {} 租约续期失败，已从备用队列移除", backupId.value());
                    iterator.remove();
                }
            } catch (Exception e) {
                log.warn("备用 WorkerId {} 租约续期异常，已从备用队列移除", backupId.value(), e);
                iterator.remove();
            }
        }
    }

    @Override
    public boolean isWorkerIdValid() {
        return !workerIdInvalid;
    }

    @Override
    @PreDestroy
    public void releaseWorkerId() {
        stopLeaseRenewal();
        // 释放主用 Worker ID
        if (registeredWorkerId != null && instanceId != null) {
            try {
                int updated = workerIdAllocMapper.releaseWorkerId(registeredWorkerId.value(), instanceId);
                if (updated > 0) {
                    log.info("释放主用 WorkerId: {}", registeredWorkerId.value());
                } else {
                    log.warn("释放主用 WorkerId {} 失败，可能已被其他实例占用", registeredWorkerId.value());
                }
            } catch (Exception e) {
                log.warn("释放主用 WorkerId 失败: {}", registeredWorkerId.value(), e);
            }
        }
        // 释放所有备用 Worker ID
        WorkerId backupId;
        while ((backupId = backupWorkerIds.poll()) != null) {
            try {
                int updated = workerIdAllocMapper.releaseWorkerId(backupId.value(), instanceId);
                if (updated > 0) {
                    log.info("释放备用 WorkerId: {}", backupId.value());
                } else {
                    log.warn("释放备用 WorkerId {} 失败，可能已被其他实例占用", backupId.value());
                }
            } catch (Exception e) {
                log.warn("释放备用 WorkerId 失败: {}", backupId.value(), e);
            }
        }
        scheduler.shutdown();
    }

    @Override
    public Optional<WorkerId> consumeBackupWorkerId() {
        WorkerId backupId = backupWorkerIds.poll();
        if (backupId != null) {
            log.info("消费备用 WorkerId: {}，剩余备用数量: {}", backupId.value(), backupWorkerIds.size());
        } else {
            log.warn("备用 Worker ID 已耗尽，无法切换");
        }
        return Optional.ofNullable(backupId);
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
     * 通过 Spring Environment 获取 server.port，比 System.getProperty 更可靠，
     * 能正确读取 Spring Boot 配置文件中的端口设置。
     *
     * @return 实例标识字符串
     */
    private String buildInstanceId() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String port = environment.getProperty("server.port", "8011");
            return ip + ":" + port;
        } catch (Exception e) {
            log.warn("获取本机 IP 失败，使用 unknown 作为实例标识", e);
            return "unknown:" + System.currentTimeMillis();
        }
    }
}
