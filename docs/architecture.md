# ID Generator 代码架构文档

> 基于代码实际状态梳理，更新日期：2026-02-27

## 1. 项目概览

分布式 ID 生成服务，支持 Snowflake（时间戳趋势递增）和 Segment（数据库号段严格递增）两种模式。

| 属性 | 值 |
|------|-----|
| groupId | `com.architectcgz` |
| Java 版本 | 17 |
| Spring Boot | 3.2.1 |
| 数据库 | PostgreSQL 16 |
| 协调服务 | ZooKeeper 3.8（Curator 5.5.0） |
| ORM | MyBatis 3.0.3 |
| 监控 | Micrometer（Spring Actuator） |
| 测试 | jqwik 1.8.2（属性测试） |
| 服务端口 | 8011 |

## 2. 模块结构

```
id-generator/                          # 聚合 POM（packaging=pom）
├── id-generator-server/               # 服务端：ID 生成核心逻辑 + REST API
├── id-generator-client/               # 客户端 SDK：Java HTTP 客户端 + 本地缓冲
├── id-generator-spring-boot-starter/  # Spring Boot 自动配置
├── examples/spring-boot-example/      # 使用示例
├── docker/                            # Docker Compose + 启动脚本
├── deploy/                            # 生产部署配置
├── sql/                               # 数据库初始化脚本
└── docs/                              # 文档
```

### 模块依赖关系

```
id-generator-spring-boot-starter
    └── id-generator-client
            └── (HTTP) ──→ id-generator-server
                                └── PostgreSQL + ZooKeeper
```

## 3. id-generator-server 分层架构

采用 DDD 分层，包路径 `com.platform.idgen`：

```
├── interfaces/              # 接口层 —— HTTP 入口
│   └── rest/
│       ├── IdGeneratorController.java        # REST API（/api/v1/id/**）
│       └── advice/
│           └── GlobalExceptionHandler.java   # 全局异常处理
│
├── application/             # 应用层 —— 用例编排
│   └── IdGeneratorApplicationService.java    # 统一入口，协调两个领域服务
│
├── domain/                  # 领域层 —— 核心业务逻辑
│   ├── model/
│   │   ├── aggregate/
│   │   │   ├── SnowflakeWorker.java          # 聚合根：Snowflake 算法实现
│   │   │   └── SegmentBuffer.java            # 聚合根：双缓冲号段管理
│   │   └── valueobject/
│   │       ├── WorkerId.java                 # 值对象：Worker ID（0-31）
│   │       ├── DatacenterId.java             # 值对象：数据中心 ID（0-31）
│   │       ├── BizTag.java                   # 值对象：业务标签
│   │       └── SnowflakeId.java              # 值对象：64 位 ID + 解析方法
│   ├── service/
│   │   ├── SnowflakeDomainService.java       # 领域服务：Snowflake 生命周期管理
│   │   └── SegmentDomainService.java         # 领域服务：Segment 缓冲管理
│   ├── repository/
│   │   ├── WorkerIdRepository.java           # 仓储接口：Worker ID 注册/缓存
│   │   └── LeafAllocRepository.java          # 仓储接口：号段分配
│   └── exception/
│       ├── IdGenerationException.java
│       ├── ClockBackwardsException.java
│       └── WorkerIdUnavailableException.java
│
├── infrastructure/          # 基础设施层 —— 外部依赖实现
│   ├── config/
│   │   ├── SnowflakeProperties.java          # Snowflake 配置属性
│   │   └── ZooKeeperProperties.java          # ZooKeeper 配置属性
│   ├── repository/
│   │   ├── WorkerIdRepositoryImpl.java       # ZooKeeper + 本地文件缓存
│   │   └── LeafAllocRepositoryImpl.java      # MyBatis 实现
│   └── zookeeper/
│       └── WorkerIdCache.java                # Worker ID 缓存接口
│
├── model/
│   └── LeafAlloc.java                        # 数据模型（对应 leaf_alloc 表）
│
└── mapper/
    └── LeafAllocMapper.java                  # MyBatis Mapper 接口
```

## 4. 核心算法

### 4.1 Snowflake ID 结构（64 位）

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|0|                    timestamp (41 bits)                       |
+-+                                                             +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  datacenter (5) |  worker (5)  |       sequence (12)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

| 字段 | 位数 | 范围 | 说明 |
|------|------|------|------|
| 未使用 | 1 | 0 | 保证 ID 为正数 |
| timestamp | 41 | 约 69 年 | 毫秒级，相对于自定义 epoch |
| datacenterId | 5 | 0-31 | 数据中心标识 |
| workerId | 5 | 0-31 | 工作节点标识 |
| sequence | 12 | 0-4095 | 毫秒内序列号 |

**关键实现细节（`SnowflakeWorker.java`）：**

- `generateId()` 使用 `synchronized` 保证线程安全
- 同毫秒内序列号溢出时，自旋等待下一毫秒（`waitForNextMillisecond`）
- 时钟回拨处理策略：
  - 偏移 ≤ 5ms：`Thread.sleep(offset)` 等待追上
  - 偏移 > 5ms：使用缓存的 lastTimestamp 保持可用性，记录告警
- 启动时从本地文件恢复 lastTimestamp，避免重启后 ID 重复

### 4.2 Segment 双缓冲机制

```
                    SegmentBuffer
              ┌──────────────────────┐
              │  segments[0]  (当前)  │ ← currentPos=0
              │  value: AtomicLong   │
              │  max: 1000           │
              │  step: 1000          │
              ├──────────────────────┤
              │  segments[1]  (备用)  │
              │  (异步预加载)          │
              └──────────────────────┘
```

**核心流程（`SegmentBuffer.java` + `SegmentDomainService.java`）：**

1. 每个 bizTag 对应一个 `SegmentBuffer`，内含两个 `Segment`（双缓冲）
2. 当前 Segment 剩余量 < 90% step 时，异步触发下一个 Segment 的数据库加载
3. 当前 Segment 耗尽后，切换到已预加载的下一个 Segment
4. 动态步长调整：
   - 消耗过快（< 15 分钟）：步长翻倍（上限 `maxStep=1000000`）
   - 消耗过慢（≥ 30 分钟）：步长减半（下限为数据库配置的初始 step）
5. 线程安全：`ReadWriteLock` + `AtomicLong` + `AtomicBoolean`（CAS 控制异步加载）

## 5. Worker ID 注册与容灾

`WorkerIdRepositoryImpl` 实现了三级降级策略：

```
静态配置（worker-id >= 0）
    │ 失败
    ▼
ZooKeeper 顺序节点注册
    │ 失败
    ▼
本地文件缓存恢复（workerID.properties）
    │ 失败
    ▼
抛出 WorkerIdUnavailableException，服务降级启动
```

- ZooKeeper 路径：`/{basePath}/{serviceName}/snowflake/worker-{seq}`
- 节点类型：`PERSISTENT_SEQUENTIAL`（持久顺序节点）
- WorkerId 计算：`sequenceNumber % 32`（取模映射到 0-31）
- 本地缓存路径：由 `snowflake.worker-id-cache-path` 配置，默认 `/data/leaf/workerID.properties`
- 缓存内容：workerId、datacenterId、zkSequenceNumber、lastTimestamp

## 6. 生命周期管理

### 6.1 启动流程

```
Spring Boot 启动
    │
    ├── SegmentDomainService.init()  [@PostConstruct]
    │   ├── 创建异步更新线程池（ThreadPoolExecutor）
    │   ├── 从数据库加载所有 bizTag → bufferCache
    │   ├── 启动定时任务：每 60s 同步新增 bizTag
    │   └── initOk = true
    │
    └── SnowflakeDomainService.autoInitialize()  [@PostConstruct]
        ├── 读取 SnowflakeProperties + ZooKeeperProperties
        ├── WorkerIdRepository.registerWorkerId()（三级降级）
        ├── 创建 SnowflakeWorker（恢复 lastTimestamp）
        └── 初始化失败不阻塞启动（降级模式）
```

### 6.2 优雅关闭

```
Spring 容器关闭
    │
    ├── SnowflakeDomainService.shutdown()  [@PreDestroy]
    │   ├── accepting = false（拒绝新请求）
    │   ├── Thread.sleep(100)（等待在途请求）
    │   ├── 持久化 lastTimestamp 到本地文件
    │   └── WorkerIdRepository.releaseWorkerId()（ZK 节点标记 offline）
    │
    └── SegmentDomainService.shutdown()  [@PreDestroy]
        ├── scheduledExecutor.shutdown()（停止定时同步）
        └── updateExecutor.shutdown()（停止异步加载）
```

## 7. REST API

基础路径：`/api/v1/id`

### Snowflake 模式

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/snowflake` | 生成单个 Snowflake ID |
| GET | `/snowflake/batch?count=N` | 批量生成（1-1000） |
| GET | `/snowflake/parse/{id}` | 解析 ID 各字段 |
| GET | `/snowflake/info` | 查看 Worker 信息 |

### Segment 模式

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/segment/{bizTag}` | 生成单个 Segment ID |
| GET | `/segment/{bizTag}/batch?count=N` | 批量生成（1-1000） |
| GET | `/tags` | 查看所有业务标签 |
| GET | `/cache/{bizTag}` | 查看缓冲区信息 |

### 通用

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查（含 Segment + Snowflake 状态） |

响应格式统一为：

```json
{
  "code": 200,
  "message": "success",
  "data": <具体数据>
}
```

## 8. id-generator-client 架构

```
id-generator-client/
└── com.platform.idgen.client/
    ├── IdGeneratorClient.java            # 接口定义
    ├── BufferedIdGeneratorClient.java     # 带本地缓冲的实现
    ├── IdGeneratorException.java         # 客户端异常（含 ErrorCode 枚举）
    ├── config/
    │   └── IdGeneratorClientConfig.java  # 客户端配置（Builder 模式）
    └── model/
        └── SnowflakeIdInfo.java          # ID 解析结果
```

### BufferedIdGeneratorClient 缓冲机制

```
应用调用 nextSnowflakeId()
    │
    ├── 缓冲区有 ID → 直接返回（零网络开销）
    │   └── 剩余量 < refillThreshold → 异步触发补充
    │
    └── 缓冲区为空 → 同步批量拉取 → 填充缓冲区 → 返回
```

- 每种模式独立缓冲：Snowflake 一个 `BlockingQueue`，Segment 按 bizTag 各一个
- 异步补充：`ScheduledExecutorService` + `AtomicBoolean`（CAS 防重入）
- HTTP 客户端：JDK 11 `HttpClient`，支持重试（指数退避）
- 线程安全：所有操作基于 `BlockingQueue` + `ConcurrentHashMap`

## 9. Spring Boot Starter 自动配置

```
id-generator-spring-boot-starter/
└── com.platform.idgen.autoconfigure/
    ├── IdGeneratorAutoConfiguration.java   # @AutoConfiguration
    └── IdGeneratorProperties.java          # @ConfigurationProperties(prefix = "id-generator.client")
```

生效条件：
- classpath 存在 `IdGeneratorClient`
- 未手动定义 `IdGeneratorClient` Bean（`@ConditionalOnMissingBean`）
- `id-generator.client.enabled` 未设为 false（默认 true）

客户端配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server-url` | `http://localhost:8010` | 服务端地址 |
| `connect-timeout-ms` | 5000 | 连接超时 |
| `read-timeout-ms` | 5000 | 读取超时 |
| `max-retries` | 3 | 最大重试次数 |
| `buffer-enabled` | true | 启用本地缓冲 |
| `buffer-size` | 100 | 缓冲区大小 |
| `refill-threshold` | 20 | 触发补充的阈值 |
| `batch-fetch-size` | 50 | 单次批量拉取数量 |
| `async-refill` | true | 异步补充 |

## 10. 数据库设计

### leaf_alloc 表（Segment 模式）

```sql
CREATE TABLE leaf_alloc (
    biz_tag      VARCHAR(128) PRIMARY KEY,   -- 业务标签
    max_id       BIGINT NOT NULL DEFAULT 1,  -- 当前已分配的最大 ID
    step         INT NOT NULL DEFAULT 1000,  -- 分配步长
    description  VARCHAR(256),               -- 描述
    update_time  TIMESTAMP,                  -- 最后更新时间
    version      BIGINT NOT NULL DEFAULT 0   -- 乐观锁版本号
);
```

- 索引：`idx_leaf_alloc_update_time`（update_time）
- 预置标签：default(1000)、user(2000)、order(5000)、message(10000)
- 并发控制：乐观锁（version 字段）

## 11. 可观测性

两个领域服务均通过 Micrometer 暴露指标：

### Snowflake 指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `snowflake.workerid.registration.success` | Counter | WorkerId 注册成功次数 |
| `snowflake.workerid.registration.failure` | Counter | WorkerId 注册失败次数 |
| `snowflake.clock.drift.ms` | Gauge | 当前时钟偏移量（ms） |
| `snowflake.clock.backwards.count` | Counter | 时钟回拨检测次数 |
| `snowflake.sequence.overflow.count` | Counter | 序列号溢出次数 |
| `snowflake.id.generation.latency` | Timer | ID 生成延迟 |

### Segment 指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `segment.buffer.switch.count` | Counter | 缓冲区切换次数 |
| `segment.db.update.latency` | Timer | 数据库更新延迟 |
| `segment.db.update.failure` | Counter | 数据库更新失败次数 |
| `segment.cache.hit.rate` | Gauge | 缓存命中率 |

## 12. 服务端关键配置项

```yaml
id-generator:
  segment:
    cache-update-interval: 60       # bizTag 同步间隔（秒）
    segment-duration: 900000        # 号段目标消耗时长（ms，用于动态步长）
    max-step: 1000000               # 步长上限
    update-thread-pool-size: 5      # 异步加载线程池大小

  snowflake:
    datacenter-id: 0                # 数据中心 ID（0-31）
    worker-id: -1                   # -1 表示使用 ZooKeeper 自动分配
    enable-zookeeper: true
    worker-id-cache-path: /data/leaf/workerID.properties
    epoch: 1735689600000            # 自定义纪元（2025-01-01）
    clock-backwards:
      max-wait-ms: 5               # 时钟回拨最大等待
      startup-check-enabled: true
      zk-time-sync-interval: 3000
      alert-threshold-ms: 10       # 告警阈值

  zookeeper:
    connection-string: localhost:2181
    session-timeout-ms: 60000
    connection-timeout-ms: 15000
    base-path: /leaf
    service-name: id-generator
    retry:
      max-retries: 3
      base-sleep-time-ms: 1000
      max-sleep-time-ms: 10000
```
