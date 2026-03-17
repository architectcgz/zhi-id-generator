# ID Generator 代码架构文档

> 基于代码实际状态梳理，更新日期：2026-03-10

## 1. 项目概览

`id-generator` 是一个分布式 ID 生成服务，支持两种模式：

- `Snowflake`：趋势递增的 64 位 ID
- `Segment`：按 `bizTag` 隔离的数据库号段 ID

当前项目已经收敛为 **PostgreSQL 单依赖架构**：

- `Segment` 模式使用 `leaf_alloc` 表分配号段
- `Snowflake` 模式使用 `worker_id_alloc` 表抢占 Worker ID，并通过租约续期保证唯一性

| 属性 | 值 |
|------|-----|
| groupId | `com.architectcgz` |
| Java 版本 | 17 |
| Spring Boot | 3.2.1 |
| 数据库 | PostgreSQL 16 |
| ORM | MyBatis 3.0.3 |
| 监控 | Micrometer + Spring Actuator |
| 测试 | JUnit 5 / jqwik |
| 服务端口 | 8011 |

## 2. 模块结构

```text
id-generator/
├── id-generator-server/               # 服务端：ID 生成核心逻辑 + REST API
├── id-generator-client/               # Java SDK：HTTP 客户端 + 本地缓冲
├── id-generator-spring-boot-starter/  # Spring Boot 自动配置
├── examples/spring-boot-example/      # 集成示例
├── docker/                            # 本地 Docker Compose 与脚本
├── deploy/                            # 部署脚本与可扩缩容 compose
├── sql/                               # 数据库初始化脚本
└── docs/                              # 项目文档
```

模块依赖关系：

```text
id-generator-spring-boot-starter
    └── id-generator-client
            └── (HTTP) ──→ id-generator-server
                                └── PostgreSQL
```

## 3. 服务端分层

包路径：`com.platform.idgen`

```text
interfaces/
└── rest/                              # HTTP API 与异常处理

application/
└── IdGeneratorApplicationService      # 用例编排

domain/
├── model/
│   ├── aggregate/
│   │   ├── SnowflakeWorker            # Snowflake 算法聚合根
│   │   └── SegmentBuffer              # 双缓冲号段聚合根
│   └── valueobject/
│       ├── WorkerId / DatacenterId
│       ├── BizTag / SegmentAllocation
│       └── SnowflakeId
├── repository/
│   ├── WorkerIdRepository             # Worker ID 注册 / 续期 / 缓存
│   └── LeafAllocRepository            # 号段分配
└── service/
    ├── SnowflakeDomainService
    └── SegmentDomainService

infrastructure/
├── config/                            # Snowflake / Segment Spring 配置
├── repository/                        # DB 仓储实现
└── persistence/mapper/                # MyBatis Mapper
```

## 4. 核心算法

### 4.1 Snowflake

ID 结构：

```text
1 bit  unused
41 bit timestamp
5 bit  datacenterId
5 bit  workerId
12 bit sequence
```

关键行为：

- `SnowflakeWorker.generateId()` 用 `synchronized` 保证线程安全
- 同毫秒内 sequence 溢出后等待下一毫秒
- 时钟回拨处理：
  - 偏移 `<= max-wait-ms`：短暂等待
  - 偏移 `> max-wait-ms` 且有备用 Worker ID：切换备用 Worker ID 后重试
  - 偏移 `> max-wait-ms` 且无备用 Worker ID：拒绝生成
- 启停时会读写本地缓存中的 `lastTimestamp`，避免重启后重复发号

### 4.2 Segment 双缓冲

每个 `bizTag` 持有一个 `SegmentBuffer`，内部维护两个 segment：

- 当前号段负责发号
- 剩余量低于阈值时异步预加载下一个号段
- 当前号段耗尽后切换到备用号段
- 根据消耗速度动态调整步长

## 5. Worker ID 分配

当前只支持 **数据库模式**。

### 5.1 数据模型

`worker_id_alloc` 表预置 `0-31` 共 32 个 Worker ID。

核心字段：

- `worker_id`：Snowflake worker 位
- `instance_id`：当前持有者，格式 `IP:port`
- `lease_time`：最近一次续期时间
- `status`：`active` / `released`

### 5.2 启动抢占流程

```text
静态配置 worker-id >= 0
    └── 直接使用

worker-id = -1
    └── SELECT ... FOR UPDATE SKIP LOCKED 抢占空闲 Worker ID
        ├── 成功：启动续期任务并预分配备用 Worker ID
        └── 失败：抛出 WorkerIdUnavailableException
```

注意：

- 数据库模式下 **不允许** 回退到本地缓存继续发号
- 无法安全抢占 Worker ID 时，Snowflake 初始化失败并进入降级状态

### 5.3 租约续期与回收

- 定时任务每 `worker-id-renew-interval` 更新一次 `lease_time`
- 若主用 Worker ID 连续续期失败，仓储会标记 `workerIdInvalid=true`
- `SnowflakeDomainService.generateId()` 在发号前检查有效性，失效后拒绝继续生成
- 优雅关闭时释放主用和备用 Worker ID

### 5.4 备用 Worker ID

数据库模式下支持预分配备用 Worker ID：

- 启动时额外抢占 `backup-worker-id-count`
- 时钟大回拨时消费一个备用 Worker ID
- 新 Worker ID 升级为主用，旧主用立即释放

## 6. 生命周期

### 6.1 启动

```text
Spring Boot 启动
├── SegmentDomainService.init()
│   ├── 创建异步更新线程池
│   ├── 加载所有 bizTag 到缓存
│   └── 定时同步新增 bizTag
└── SnowflakeDomainService.autoInitialize()
    ├── 注册 Worker ID
    ├── 恢复 lastTimestamp
    └── 创建 SnowflakeWorker
```

### 6.2 关闭

```text
Spring 容器关闭
├── SnowflakeDomainService.shutdown()
│   ├── 停止接收新请求
│   ├── 等待在途请求完成
│   ├── 持久化 lastTimestamp
│   └── 释放 Worker ID
└── SegmentDomainService.shutdown()
    ├── 停止定时任务
    └── 停止异步线程池
```

## 7. 对外接口

基础路径：`/api/v1/id`

Snowflake：

- `GET /snowflake`
- `GET /snowflake/batch?count=10`
- `GET /snowflake/parse/{id}`
- `GET /snowflake/info`

Segment：

- `GET /segment/{bizTag}`
- `GET /segment/{bizTag}/batch?count=10`
- `GET /tags`

健康检查：

- `GET /health`
- `GET /actuator/health`

统一响应结构：

- 业务接口统一返回 `ApiResponse<T>`
- 成功响应固定包含 `code=200`、`message=success`、`data`
- 查询接口 DTO 已固定为：
  - `/health` -> `HealthStatus`
  - `/snowflake/info` -> `SnowflakeInfo`
  - `/snowflake/parse/{id}` -> `SnowflakeParseInfo`
  - `/cache/{bizTag}` -> `SegmentCacheInfo`
- 错误响应使用 `code` + `errorCode` + `message`，`ClockBackwardsException` 额外带 `extra.offset`

当前关键错误语义：

- `BIZ_TAG_NOT_EXISTS` -> HTTP 404，用于不存在的 Segment 业务标签
- `SEGMENT_UPDATE_FAILED` / `SEGMENTS_NOT_READY` -> HTTP 503，用于 Segment 号段不可用
- `WORKER_ID_UNAVAILABLE` / `WORKER_ID_INVALID` -> HTTP 503，用于 Worker ID 分配或租约失效
- `SERVICE_SHUTTING_DOWN` / `SNOWFLAKE_NOT_INITIALIZED` -> HTTP 503，用于 Snowflake 不可服务
- `ILLEGAL_STATE` -> HTTP 500，仅表示未预期的内部状态错误

## 8. 关键配置

```yaml
id-generator:
  segment:
    cache-update-interval: 60
    segment-duration: 900000
    max-step: 1000000
    update-thread-pool-size: 5

  snowflake:
    datacenter-id: 0
    worker-id: -1
    worker-id-cache-path: /data/leaf/workerID.properties
    worker-id-lease-timeout: 10m
    worker-id-renew-interval: 3m
    backup-worker-id-count: 1
    epoch: 1735689600000
    clock-backwards:
      max-wait-ms: 5
      alert-threshold-ms: 10
      max-startup-wait-ms: 5000
```

## 9. 运维约束

- 单个 `datacenterId` 下最多 32 个活跃 Worker ID
- `worker-id=-1` 时依赖 PostgreSQL 可用
- 若业务需要超过 32 个并发实例，应通过拆分 `datacenterId` 或调整位分配方案扩容

## 10. 当前取舍

这版架构刻意放弃 ZooKeeper：

- 优点：部署更轻，依赖更少，排障面更小
- 代价：Worker ID 分配能力完全依赖 PostgreSQL，可扩展性受 5 bit worker 位宽限制
