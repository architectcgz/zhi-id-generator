# id-generator 代码 Review（db-worker-id 第 4 轮）：数据库分配 Worker ID 实现

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | db-worker-id |
| 轮次 | 第 4 轮 |
| 审查范围 | master..feat/db-worker-id，5 个 commit（4bec84f, cce5cd4, 9abf110, 7637672, b0639d7），8 个文件，+505 / -18 行 |
| 变更概述 | 实现基于数据库（SELECT FOR UPDATE SKIP LOCKED）的 Worker ID 抢占/续期/释放机制，通过 @ConditionalOnProperty 切换 DB/ZK 两种实现，新增租约配置项 |
| 审查基准 | `docs/architecture.md`、`docs/todo.md`（任务 1.1-1.4） |
| 审查日期 | 2026-03-01 |
| 上轮问题数 | 第 3 轮 5 项（0 高 / 3 中 / 2 低） |

## 第 3 轮问题修复确认

| 编号 | 问题 | 状态 | 说明 |
|------|------|------|------|
| M1 | worker_id_alloc.status 缺少 CHECK 约束 | 已修复 | commit 9abf110 补充了 `CHECK (status IN ('active', 'released'))` |
| M2 | worker_id_alloc 缺少 lease_time 索引 | 未修复 | 32 行数据全表扫描无实际性能问题，第 3 轮报告也建议可放低优先级，但仍建议补上 |
| M3 | worker_id_alloc.worker_id 缺少 CHECK 约束 | 已修复 | commit 9abf110 补充了 `CHECK (worker_id >= 0 AND worker_id <= 31)` |
| L1 | leaf_alloc 预填数据 INSERT 缺少 update_time | 未修复 | 风格一致性问题，非本批次重点 |
| L2 | schema.sql 中英文注释混用 | 未修复 | 风格一致性问题，非本批次重点 |

## 任务完成度对照

| TODO 编号 | 描述 | 状态 | 说明 |
|-----------|------|------|------|
| 0.5 | MyBatis XML 映射文件缺失 | 已修复 | LeafAllocMapper.xml 已补充（commit cce5cd4） |
| 1.1 | 新建 worker_id_alloc 表 | 已完成 | DDL + CHECK 约束 + 预填数据（commit 4bec84f + 9abf110） |
| 1.2 | 实现 DbWorkerIdRepository | 已完成 | DbWorkerIdRepositoryImpl 实现抢占/续期/释放/缓存降级（commit 7637672） |
| 1.3 | 条件注入切换 | 已完成 | DB/ZK 两个实现均加 @ConditionalOnProperty，互斥正确（commit b0639d7） |
| 1.4 | 配置项调整 | 已完成 | 新增 lease-timeout/renew-interval，enable-zookeeper 默认改为 false（commit b0639d7） |

## 问题清单

### 🔴 高优先级

#### [H1] acquireWorkerId UPDATE 缺少防御性 WHERE 条件（第 1 轮 review 遗留）

- **文件**：`id-generator-server/src/main/resources/mapper/WorkerIdAllocMapper.xml:20-25`
- **问题描述**：`acquireWorkerId` 的 UPDATE 语句只有 `WHERE worker_id = #{workerId}`，没有校验 `status` 和 `lease_time`。虽然 SELECT FOR UPDATE SKIP LOCKED 在同一事务内持有行锁可以防止并发问题，但 UPDATE 缺少防御性条件意味着：如果未来事务边界重构、或有其他代码路径（如手动运维 SQL）在 SELECT 和 UPDATE 之间修改了该行状态，UPDATE 仍会无条件成功，导致两个实例持有同一个 Worker ID
- **影响范围/风险**：Worker ID 冲突 → Snowflake ID 重复（核心正确性风险）
- **修正建议**：

WorkerIdAllocMapper.xml：
```xml
<update id="acquireWorkerId">
    UPDATE worker_id_alloc
    SET status      = 'active',
        instance_id = #{instanceId},
        lease_time  = NOW()
    WHERE worker_id = #{workerId}
      AND (status = 'released'
           OR (status = 'active'
               AND lease_time < NOW() - CAST(#{leaseTimeoutMinutes} || ' minutes' AS INTERVAL)))
</update>
```

WorkerIdAllocMapper.java 接口同步增加参数：
```java
int acquireWorkerId(@Param("workerId") int workerId,
                    @Param("instanceId") String instanceId,
                    @Param("leaseTimeoutMinutes") long leaseTimeoutMinutes);
```

DbWorkerIdRepositoryImpl.acquireFromDatabase() 调用处传入 leaseTimeoutMinutes。

#### [H2] 租约续期失败后未采取任何保护措施

- **文件**：`DbWorkerIdRepositoryImpl.java:155-161`（renewLease 方法）
- **问题描述**：当 `renewLease()` 返回 `updated == 0`（续期失败，Worker ID 可能已被其他实例回收）时，仅打了一条 WARN 日志，但当前实例仍然继续使用该 Worker ID 生成 Snowflake ID。此时另一个实例可能已经抢占了同一个 Worker ID，两个实例同时用相同 Worker ID 生成 ID 会导致重复
- **影响范围/风险**：Worker ID 冲突 → Snowflake ID 重复（核心正确性风险）
- **修正建议**：续期失败时应触发告警并尝试重新抢占，或通知 SnowflakeDomainService 停止生成 ID。建议增加连续失败计数器，连续 N 次失败后主动停止 ID 生成：

```java
private final AtomicInteger renewFailCount = new AtomicInteger(0);
private static final int MAX_RENEW_FAIL_COUNT = 2;

private void renewLease() {
    if (registeredWorkerId == null || instanceId == null) {
        return;
    }
    try {
        int updated = workerIdAllocMapper.renewLease(registeredWorkerId.value(), instanceId);
        if (updated > 0) {
            renewFailCount.set(0);
            log.debug("WorkerId {} 租约续期成功", registeredWorkerId.value());
        } else {
            int failCount = renewFailCount.incrementAndGet();
            log.warn("WorkerId {} 租约续期失败（连续第 {} 次），可能已被回收",
                    registeredWorkerId.value(), failCount);
            if (failCount >= MAX_RENEW_FAIL_COUNT) {
                log.error("WorkerId {} 连续 {} 次续期失败，停止使用该 Worker ID",
                        registeredWorkerId.value(), failCount);
                // 通知领域服务停止生成 ID 或触发重新注册
            }
        }
    } catch (Exception e) {
        log.error("WorkerId {} 租约续期异常", registeredWorkerId.value(), e);
    }
}
```

### 🟡 中优先级

#### [M1] buildInstanceId() 通过 System.getProperty 获取端口不可靠

- **文件**：`DbWorkerIdRepositoryImpl.java:278-286`
- **问题描述**：`System.getProperty("server.port", "8011")` 获取的是 JVM 系统属性，而非 Spring Boot 的 `server.port` 配置。Spring Boot 的端口配置在 `application.yml` 中，通过 `Environment` 注入，不会自动设置为系统属性。因此在大多数部署场景下，这里始终返回硬编码的 `"8011"`
- **影响范围/风险**：多实例部署在同一台机器上使用不同端口时，所有实例的 instanceId 相同（如 `192.168.1.1:8011`），续期时 WHERE instance_id 校验形同虚设
- **修正建议**：通过构造函数注入 Spring `Environment`，使用 `environment.getProperty("server.port", "8011")` 获取实际端口：

```java
private final Environment environment;

public DbWorkerIdRepositoryImpl(SnowflakeProperties snowflakeProperties,
                                 WorkerIdAllocMapper workerIdAllocMapper,
                                 Environment environment) {
    this.snowflakeProperties = snowflakeProperties;
    this.workerIdAllocMapper = workerIdAllocMapper;
    this.environment = environment;
}

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
```

#### [M2] registerWorkerId() 的 @Transactional 事务范围过大

- **文件**：`DbWorkerIdRepositoryImpl.java:72`
- **问题描述**：`@Transactional` 标注在 `registerWorkerId()` 上，但该方法在进入数据库操作前包含静态配置检查（`getWorkerId() >= 0`）、`buildInstanceId()`（涉及 DNS 解析）等非数据库操作，且在数据库抢占之后还有本地文件缓存写入（`cacheWorkerId`）和定时任务启动（`startLeaseRenewal`）。这些操作都在事务内执行，不必要地延长了事务持有时间和行锁持有时间
- **影响范围/风险**：多实例同时启动时，DNS 解析慢或文件 IO 慢会导致 FOR UPDATE 行锁持有时间过长，阻塞其他实例的抢占（虽然 SKIP LOCKED 可以跳过，但被锁定的行对其他实例不可见，减少了可用 Worker ID 池）
- **修正建议**：将事务范围缩小到仅包含数据库操作。把 `acquireFromDatabase()` 提取为独立的 `@Transactional` 方法，但由于 Spring AOP 自调用不生效，需要通过以下方式之一解决：
  1. 将数据库操作提取到一个独立的 Spring Bean（如 `WorkerIdAllocService`）
  2. 或从 `registerWorkerId()` 上移除 `@Transactional`，在 Mapper 层面保证原子性（将 SELECT + UPDATE 合并为一条 SQL）

#### [M3] cacheWorkerId() 第三个参数语义错误

- **文件**：`DbWorkerIdRepositoryImpl.java:86`
- **问题描述**：调用 `cacheWorkerId(workerId, snowflakeProperties.getDatacenterId(), workerId.value())` 时，第三个参数 `zkSequenceNumber` 传入的是 `workerId.value()`。在 DB 模式下不存在 ZK 序列号的概念，这里用 Worker ID 值充当 zkSequenceNumber 是语义错误。虽然该值仅用于本地缓存文件，不影响运行时逻辑，但会误导后续维护者
- **影响范围/风险**：缓存文件中 `zkSequenceNumber` 字段值无意义，排查问题时可能造成混淆
- **修正建议**：`WorkerIdRepository.cacheWorkerId()` 接口的参数设计耦合了 ZK 实现细节。短期可传 `-1` 或 `0` 并加注释说明 DB 模式下该字段无意义；长期建议重构接口，将缓存内容抽象为一个 `WorkerIdCacheData` 值对象，不同实现填充不同字段

#### [M4] 本地缓存降级恢复的 Worker ID 可能与数据库中其他实例冲突

- **文件**：`DbWorkerIdRepositoryImpl.java:93-97`
- **问题描述**：当数据库抢占失败后，代码降级到本地缓存恢复 Worker ID。但此时该 Worker ID 在数据库中可能已被其他实例占用（状态为 active），两个实例同时使用相同 Worker ID 会导致 Snowflake ID 重复。与 ZK 模式不同，DB 模式下有明确的租约机制，降级恢复绕过了这个机制
- **影响范围/风险**：数据库短暂不可用后恢复时，可能出现 Worker ID 冲突
- **修正建议**：降级恢复时应记录明确的 WARN 日志说明风险，并在数据库恢复后尝试重新注册。或者更保守地：DB 模式下不支持本地缓存降级，数据库不可用时直接抛异常拒绝启动，因为 DB 模式的核心假设就是数据库可用

### 🟢 低优先级

#### [L1] releaseWorkerId() 未校验 instance_id，可能误释放他人持有的 Worker ID

- **文件**：`WorkerIdAllocMapper.xml:38-42`
- **问题描述**：`releaseWorkerId` 的 SQL 只有 `WHERE worker_id = #{workerId}`，没有校验 `instance_id`。如果当前实例的 Worker ID 已因租约过期被其他实例回收并重新占用，此时原实例执行 `@PreDestroy` 释放操作会把新持有者的 Worker ID 错误释放
- **影响范围/风险**：极端场景下（实例长时间 GC 后恢复 → 触发 shutdown），新持有者的 Worker ID 被意外释放，导致该 Worker ID 被第三个实例抢占
- **修正建议**：

```xml
<update id="releaseWorkerId">
    UPDATE worker_id_alloc
    SET status      = 'released',
        instance_id = ''
    WHERE worker_id = #{workerId}
      AND instance_id = #{instanceId}
</update>
```

Mapper 接口同步增加 `instanceId` 参数，DbWorkerIdRepositoryImpl 调用处传入 `this.instanceId`。

#### [L2] ScheduledExecutorService 在构造时即创建，静态配置模式下浪费资源

- **文件**：`DbWorkerIdRepositoryImpl.java:49-53`
- **问题描述**：`scheduler` 在字段声明时直接 `Executors.newSingleThreadScheduledExecutor()` 创建线程。当使用静态配置的 Worker ID（`workerId >= 0`）时，不需要租约续期，但线程已经创建且不会被关闭（`startLeaseRenewal()` 不会被调用，`releaseWorkerId()` 中的 `scheduler.shutdown()` 会关闭它，但线程已经白白存在了整个生命周期）
- **影响范围/风险**：资源浪费，非功能性问题
- **修正建议**：延迟创建 scheduler，仅在需要续期时才初始化：

```java
private volatile ScheduledExecutorService scheduler;

private ScheduledExecutorService getOrCreateScheduler() {
    if (scheduler == null) {
        synchronized (this) {
            if (scheduler == null) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "worker-id-lease-renew");
                    thread.setDaemon(true);
                    return thread;
                });
            }
        }
    }
    return scheduler;
}
```

#### [L3] saveLastUsedTimestamp() 的文件读写非原子操作

- **文件**：`DbWorkerIdRepositoryImpl.java:237-262`
- **问题描述**：`saveLastUsedTimestamp()` 先读取已有 Properties 文件，修改后再写回。读和写之间没有文件锁保护，如果续期线程和 shutdown 线程同时调用（虽然当前代码路径不会），可能导致文件内容损坏。此外，写入过程中如果 JVM 崩溃，文件可能只写了一半
- **影响范围/风险**：极端场景下缓存文件损坏，重启后无法恢复 lastTimestamp，但有其他降级手段兜底
- **修正建议**：使用"写临时文件 + rename"的原子写入模式：

```java
Path tmpPath = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
try (OutputStream os = Files.newOutputStream(tmpPath)) {
    props.store(os, "WorkerId Cache");
}
Files.move(tmpPath, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
```

#### [L4] LeafAllocMapper.xml resultMap 的 type 使用短名依赖全局 type-aliases-package 配置

- **文件**：`id-generator-server/src/main/resources/mapper/LeafAllocMapper.xml:8`
- **问题描述**：`type="LeafAlloc"` 依赖 `application.yml` 中 `mybatis.type-aliases-package` 配置。而新增的 `WorkerIdAllocMapper.xml` 没有使用 resultMap（直接 `resultType="java.lang.Integer"`），两个 XML 风格不一致。如果未来 type-aliases-package 配置变更或 LeafAlloc 类移动包路径，LeafAllocMapper.xml 会静默失败
- **影响范围/风险**：无当前功能影响，仅可维护性风险
- **修正建议**：建议统一使用全限定类名 `type="com.platform.idgen.infrastructure.persistence.entity.LeafAlloc"`，或确认团队约定后保持一致即可

## 风险前置检查清单

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 幂等性 | 部分通过 | schema.sql 的 `ON CONFLICT DO NOTHING` 保证 DDL 幂等；`registerWorkerId()` 重复调用会重复抢占，但启动流程只调用一次，可接受 |
| 并发竞争 | 通过 | SELECT FOR UPDATE SKIP LOCKED 正确避免多实例抢占同一行；renewLease 的 WHERE 条件包含 instance_id 校验 |
| 超时与重试 | 部分通过 | 租约超时/续期机制设计合理；但续期失败后缺少重试或保护措施（见 H2） |
| 补偿/回滚 | 部分通过 | @PreDestroy 释放 Worker ID；但释放失败时无补偿，依赖租约过期自动回收 |
| 可观测性 | 需改进 | 关键操作有日志覆盖；但缺少 Micrometer 指标（如 `worker.id.lease.renew.failure`、`worker.id.acquire.latency`），与架构文档中 Snowflake 指标风格不一致 |

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 2 |
| 🟡 中 | 4 |
| 🟢 低 | 4 |
| 合计 | 10 |

## 总体评价

本轮审查覆盖 feat/db-worker-id 分支全部 5 个 commit，任务 1.1-1.4 均已完成，第 3 轮的 M1/M3（CHECK 约束）已修复。整体架构设计合理：SELECT FOR UPDATE SKIP LOCKED 抢占方案正确、条件注入互斥无误、配置项通过 Duration 类型注入符合规范、LeafAllocMapper.xml 与接口完全一致。

主要改进方向集中在两个高优先级问题：

1. acquireWorkerId 的 UPDATE 缺少防御性 WHERE 条件（H1，第 1 轮 review 遗留至今未修复），虽然当前事务内行锁可以保护，但属于必须补上的防御性编程。
2. 租约续期失败后无保护措施（H2），续期失败意味着 Worker ID 可能已被回收，继续使用会导致 ID 重复，这是核心正确性风险。

中优先级问题主要涉及 instanceId 获取方式不可靠（M1）、事务范围过大（M2）、接口参数语义错误（M3）、本地缓存降级在 DB 模式下的安全性（M4）。低优先级问题为释放时缺少 instance_id 校验（L1）、scheduler 提前创建（L2）、文件写入非原子（L3）、XML 风格不一致（L4）。

建议优先修复 H1 和 H2，这两个问题直接关系到 Worker ID 唯一性保证，是 Snowflake 算法正确性的基础。
