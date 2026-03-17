# ID Generator 优化 TODO

> 基于代码审查 + Q&A 讨论整理，2026-02-28
>
> 注：本文是 2026-02-28 的历史 backlog，其中部分条目仍基于早期 ZooKeeper/DB 双轨方案。自 2026-03-10 起，项目已收敛为 PostgreSQL-only Worker ID 分配，当前运行架构请以 [docs/architecture.md](./architecture.md) 和 `docs/todos/2026-03-10-review-fixes.md` 为准。

---

## 零、代码质量问题（建议在新功能开发前先修复）

> 以下问题按严重程度排序。标注 `[高]` 的问题有正确性或可用性风险，应优先处理。

### 0.1 `[高]` SnowflakeWorker 大回拨分支有 ID 重复风险

- 位置：`SnowflakeWorker.java:222-226`
- 问题：`handleClockBackwards()` 大回拨分支只保存了 `lastTimestamp` 到缓存，但没有修正 `currentTimestamp`。回到 `generateId()` 后，`lastTimestamp` 被更新为回拨后的值，导致回拨前后的时间重叠区间内可能生成重复 ID
- 影响：ID 唯一性被破坏（核心功能缺陷）
- 修复：这个问题会在后续「时钟回拨切换 Worker ID」功能中一并解决。如果不做该功能，至少应改为 `throw new ClockBackwardsException(offset)` 拒绝生成，而非静默继续

### 0.2 `[高]` SegmentBuffer.nextId() 读写锁升级存在竞态风险

- 位置：`SegmentBuffer.java:284-306`
- 问题：`readLock.unlock()` 后再 `writeLock.lock()` 之间存在窗口期，其他线程可能已经完成了 segment 切换。虽然代码在获取写锁后做了 re-check，但递归调用 `nextId()` 时又会重新获取读锁，而此时外层 finally 还会再 `readLock.unlock()`，可能导致 unlock 次数不匹配
- 影响：高并发下可能出现 `IllegalMonitorStateException` 或死锁
- 修复：简化为只用写锁（性能影响极小，因为 segment 切换是低频操作），或改用 `StampedLock` 的乐观读模式

### 0.3 `[高]` SegmentDomainService 的 shouldLoadNextSegment 与 nextId 之间存在竞态

- 位置：`SegmentDomainService.java:273-281`
- 问题：`shouldLoadNextSegment()` 和 `nextId()` 之间没有原子性保证。在高并发下，可能出现：检查时 segment 还有余量 → 触发异步加载 → 但在加载完成前 segment 已耗尽 → nextId 抛异常
- 更深层问题：`shouldLoadNextSegment()` 在 `nextId()` 之前调用，但 `nextId()` 内部消费 ID 后才是真正应该检查的时机
- 修复：将 `shouldLoadNextSegment` 检查移到 `nextId()` 内部（消费 ID 之后），或在 `SegmentBuffer.nextId()` 中直接触发

### 0.4 `[高]` SnowflakeDomainService 的 sequenceOverflowCount 指标不准确

- 位置：`SnowflakeDomainService.java:186-189`
- 问题：用 `endTime - startTime > 1` 来推测 sequence overflow，这是一个不可靠的启发式判断。GC 暂停、线程调度延迟都会导致误报。而且 `Timer.record()` 本身已经在计时了，内部再 `System.currentTimeMillis()` 是重复计时
- 修复：让 `SnowflakeWorker.generateId()` 返回一个包含元信息的结果（是否发生了 overflow/clock-wait），或通过回调通知

### 0.5 `[高]` MyBatis XML 映射文件缺失

- 问题：`application.yml` 配置了 `mybatis.mapper-locations: classpath:mapper/*.xml`，但项目中没有任何 XML 映射文件。`LeafAllocMapper` 的方法（`findAll`、`findByBizTag`、`updateMaxIdWithLock`、`insert`）没有对应的 SQL 定义
- 影响：Segment 模式完全无法工作，启动时 MyBatis 会报错
- 修复：补充 `src/main/resources/mapper/LeafAllocMapper.xml`，或改用 MyBatis 注解方式（`@Select`、`@Update`）

### 0.6 `[高]` 测试完全缺失

- 问题：整个项目没有任何单元测试或集成测试（`src/test/` 目录为空）
- 影响：无法验证核心逻辑的正确性，重构和新功能开发没有安全网
- 修复：至少补充以下测试：
  - `SnowflakeWorker` 单元测试：ID 唯一性、sequence 溢出、时钟回拨处理
  - `SegmentBuffer` 单元测试：双缓冲切换、并发 nextId、segment 耗尽
  - `WorkerId` / `SnowflakeId` 值对象测试：边界值、fromSequenceNumber
  - `LeafAllocRepositoryImpl` 测试：乐观锁重试

### 0.7 `[中]` SnowflakeDomainService 领域层依赖基础设施层

- 位置：`SnowflakeDomainService.java:12-13`
- 问题：领域服务直接 import 了 `infrastructure.config.SnowflakeProperties`、`infrastructure.config.ZooKeeperProperties`、`infrastructure.zookeeper.WorkerIdCache`，违反了 DDD 分层原则（领域层不应依赖基础设施层）
- 影响：领域逻辑与基础设施耦合，难以独立测试
- 修复：
  - `SnowflakeProperties` / `ZooKeeperProperties` 的值应通过构造函数注入原始值，而非注入 Properties 对象
  - `WorkerIdCache` 接口应移到 domain 层（它本质上是领域需要的端口）

### 0.8 `[中]` WorkerIdCache 接口放在了 infrastructure 包下

- 位置：`infrastructure/zookeeper/WorkerIdCache.java`
- 问题：`WorkerIdCache` 是一个抽象接口，被 `SnowflakeWorker`（领域聚合根）依赖。按 DDD 六边形架构，它应该是领域层定义的端口（port），实现放在基础设施层
- 修复：移到 `domain/repository/` 或 `domain/port/` 包下

### 0.9 `[中]` LeafAlloc 实体放在了错误的包下

- 位置：`com.platform.idgen.model.LeafAlloc` 和 `com.platform.idgen.mapper.LeafAllocMapper`
- 问题：`model` 和 `mapper` 包直接放在根包下，不属于任何分层。按项目的 DDD 分层结构，`LeafAlloc` 应在 `infrastructure` 层（它是数据库映射对象），`LeafAllocMapper` 也应在 `infrastructure` 层
- 修复：
  - `LeafAlloc` → `infrastructure/persistence/entity/LeafAlloc.java`（或 `infrastructure/repository/entity/`）
  - `LeafAllocMapper` → `infrastructure/persistence/mapper/LeafAllocMapper.java`

### 0.10 `[中]` Controller 返回裸 Map 而非统一响应 DTO

- 位置：`IdGeneratorController.java` 所有方法
- 问题：所有接口返回 `Map<String, Object>`，每个方法都手动构造 `response.put("code", 200)` 等。缺乏类型安全，容易遗漏字段，且与 `GlobalExceptionHandler` 的错误响应格式不一致（错误响应有 `errorCode` 字段，成功响应没有）
- 修复：定义统一的 `ApiResponse<T>` 泛型 DTO，成功和失败都用同一结构

### 0.11 `[中]` SnowflakeDomainService.shutdown() 用 Thread.sleep 等待飞行中请求

- 位置：`SnowflakeDomainService.java:280-281`
- 问题：用固定 100ms sleep 来等待飞行中请求完成，这是不可靠的。如果请求处理时间超过 100ms（比如 sequence overflow 等待下一毫秒），可能在请求还在处理时就开始释放资源
- 修复：使用 `CountDownLatch` 或 `AtomicInteger` 跟踪飞行中请求数，等待归零后再继续

### 0.12 `[中]` SnowflakeDomainService.initialize() 中的 WorkerIdCache 适配器

- 位置：`SnowflakeDomainService.java:137-147`
- 问题：在 `initialize()` 方法中用匿名内部类创建 `WorkerIdCache` 适配器，将 `WorkerIdRepository` 的方法委托给 `WorkerIdCache`。这说明 `WorkerIdCache` 和 `WorkerIdRepository` 的 timestamp 相关方法是重复的
- 修复：合并接口，或让 `WorkerIdRepository` 直接实现 `WorkerIdCache`

### 0.13 `[中]` 客户端默认端口与服务端不一致

- 位置：`IdGeneratorClientConfig.java:17`、`IdGeneratorProperties.java:37`
- 问题：客户端默认 `serverUrl = "http://localhost:8010"`，但服务端实际端口是 `8011`（`application.yml:2`）
- 影响：使用默认配置的客户端无法连接服务端
- 修复：统一为 `8011`

### 0.14 `[中]` ZooKeeperProperties 的 @NotBlank 校验在 ZK 禁用时仍会触发

- 位置：`ZooKeeperProperties.java:25`
- 问题：`connectionString` 标注了 `@NotBlank`，但当 `enable-zookeeper=false` 时，用户不应该被强制配置 ZK 连接信息。当前 `@Validated` 会在 Bean 创建时校验所有字段
- 影响：禁用 ZK 时仍需提供 ZK 配置，否则启动失败
- 修复：去掉 `@Validated`，改为在 `WorkerIdRepositoryImpl.init()` 中按需校验；或将 ZK 配置类的创建也加上 `@ConditionalOnProperty`

### 0.15 `[中]` WorkerIdRepositoryImpl 使用 PERSISTENT_SEQUENTIAL 节点但不清理

- 位置：`WorkerIdRepositoryImpl.java:133-134`
- 问题：每次启动都创建新的 `PERSISTENT_SEQUENTIAL` 节点，但从不删除旧节点。长期运行后 ZK 上会积累大量废弃节点，且 `fromSequenceNumber()` 用 `% 32` 取模，序号增长后不同实例可能分到相同的 Worker ID
- 影响：Worker ID 冲突风险随重启次数增加而增大
- 修复：改用 `EPHEMERAL_SEQUENTIAL`（会话结束自动删除），或在启动时清理过期节点

### 0.16 `[低]` SegmentDomainService 配置项用 @Value 而非 @ConfigurationProperties

- 位置：`SegmentDomainService.java:63-73`
- 问题：Segment 相关配置用 `@Value` 注入，而 Snowflake 配置用 `@ConfigurationProperties`。风格不统一，且 `@Value` 不支持校验、不支持 IDE 自动补全
- 修复：新建 `SegmentProperties` 类，用 `@ConfigurationProperties(prefix = "id-generator.segment")` 统一管理

### 0.17 `[低]` SnowflakeWorker 的 CLOCK_BACKWARDS_WAIT_THRESHOLD_MS 硬编码

- 位置：`SnowflakeWorker.java:46`
- 问题：阈值 `5L` 硬编码在聚合根中，但 `SnowflakeProperties.ClockBackwards.maxWaitMs` 已经有对应的配置项。两者没有关联，配置项改了不生效
- 修复：通过构造函数将阈值传入 `SnowflakeWorker`

### 0.18 `[低]` SegmentDomainService.generateId() 的 segment 切换检测不可靠

- 位置：`SegmentDomainService.java:278-288`
- 问题：通过比较 `nextId()` 前后的 `getCurrentSegment()` 引用来判断是否发生了 segment 切换。但在高并发下，其他线程可能在两次 `getCurrentSegment()` 之间完成了切换又切回来，导致漏计
- 修复：让 `SegmentBuffer.nextId()` 返回是否发生了切换，或维护一个切换计数器

### 0.19 `[低]` SnowflakeWorker 构造函数中的 Thread.sleep 阻塞

- 位置：`SnowflakeWorker.java:91-93`
- 问题：构造函数中如果检测到当前时间 ≤ 缓存时间戳，会 `Thread.sleep(waitTime)` 阻塞。如果缓存时间戳异常（比如未来很远的时间），会阻塞很长时间
- 修复：设置最大等待上限（比如 5 秒），超过则抛异常或清除缓存

### 0.20 `[低]` releaseWorkerId 中保存的是系统时间而非 Snowflake 时间戳

- 位置：`WorkerIdRepositoryImpl.java:214-216`
- 问题：`releaseWorkerId()` 调用 `saveLastUsedTimestamp(System.currentTimeMillis())`，但 `SnowflakeWorker` 内部的 `lastTimestamp` 是 `System.currentTimeMillis() - epoch`。保存的值和恢复时期望的值不在同一个时间基准上
- 影响：重启后恢复的 `lastTimestamp` 偏大，会导致不必要的等待
- 修复：这个方法不应该自行保存时间戳，`SnowflakeDomainService.shutdown()` 已经在调用 `saveLastUsedTimestamp(worker.getLastTimestamp())` 了，这里的保存是多余且错误的

---

## 优化目标

1. 用 PostgreSQL 替代 ZooKeeper 做 Worker ID 分配，消除 ZK 依赖
2. 时钟回拨时切换 Worker ID，解决大回拨场景下的 ID 重复风险

---

## 任务拆分

### 一、数据库分配 Worker ID

#### 1.1 新建 worker_id_alloc 表

- 预填 0-31 共 32 行
- 字段：worker_id、instance_id（IP:port）、lease_time（租约时间）、status（active/released）
- 位置：`sql/schema.sql` 追加

#### 1.2 实现 DbWorkerIdRepository

- 新建 `infrastructure/repository/DbWorkerIdRepositoryImpl.java`，实现 `WorkerIdRepository` 接口
- 启动时通过 `SELECT ... FOR UPDATE SKIP LOCKED` 抢占一个空闲的 worker_id
- 支持租约续期（定时更新 lease_time）
- 关闭时释放（status 置为 released）
- 租约过期自动回收（超过阈值的视为可用）

#### 1.3 条件注入切换

- `DbWorkerIdRepositoryImpl` 加 `@ConditionalOnProperty(name = "id-generator.snowflake.enable-zookeeper", havingValue = "false")`
- `WorkerIdRepositoryImpl`（ZK 版）加 `@ConditionalOnProperty(name = "id-generator.snowflake.enable-zookeeper", havingValue = "true")`
- 默认值改为 `false`（默认走数据库）

#### 1.4 配置项调整

- 新增配置：`id-generator.snowflake.worker-id-lease-timeout`（租约超时，默认 10 分钟）
- 新增配置：`id-generator.snowflake.worker-id-renew-interval`（续期间隔，默认 3 分钟）
- `enable-zookeeper` 默认值从 `true` 改为 `false`

#### 1.5 移除 ZK 强依赖

- ZK 相关依赖（curator-framework、curator-recipes）改为 `<optional>true</optional>`
- 不用 ZK 时不需要配置 zookeeper 连接信息

---

### 二、时钟回拨时切换 Worker ID

#### 2.1 启动时预分配多个 Worker ID

- `DbWorkerIdRepository` 启动时抢占 2-3 个 worker_id（主用 + 备用）
- 备用 ID 同样标记为 active，绑定同一个 instance_id
- 新增字段或标记区分主用/备用

#### 2.2 修改 SnowflakeWorker 支持运行时切换 Worker ID

- 新增方法 `switchWorkerId(WorkerId newWorkerId)`
- 切换时重置 sequence = 0、更新 lastTimestamp
- 保持 `synchronized` 保证线程安全

#### 2.3 修改 SnowflakeDomainService 的回拨处理逻辑

- 大回拨（> 5ms）时，调用 `switchWorkerId()` 切换到备用 Worker ID
- 切换后记录指标：`snowflake.workerid.switch.count`
- 如果备用 ID 也用完了，降级为当前的「容忍继续生成」策略

#### 2.4 修改 handleClockBackwards 方法

- 当前逻辑：大回拨 → 打日志 + 保存 lastTimestamp（有重复风险）
- 改为：大回拨 → 切换 Worker ID → 用新 Worker ID 继续生成（无重复风险）

---

### 三、测试

#### 3.1 DbWorkerIdRepository 单元测试

- 正常抢占、租约续期、释放、过期回收
- 并发抢占（多线程模拟多实例同时启动）
- 32 个 ID 全部占满时的行为

#### 3.2 Worker ID 切换集成测试

- 模拟时钟回拨 > 5ms，验证自动切换 Worker ID
- 切换前后生成的 ID 无重复
- 备用 ID 耗尽后的降级行为

#### 3.3 回归测试

- 原有 Snowflake / Segment 功能不受影响
- `enable-zookeeper: true` 时行为不变

---

### 四、文档更新

- 更新 `docs/architecture.md` 中 Worker ID 注册章节
- 更新配置项说明
- Q&A 中补充实际实现后的说明

---

## 建议 commit 拆分

| 顺序 | commit | 内容 |
|------|--------|------|
| 1 | `feat(snowflake): 新增 worker_id_alloc 表结构` | DDL + schema.sql |
| 2 | `feat(snowflake): 实现数据库分配 Worker ID` | DbWorkerIdRepositoryImpl + 条件注入 + 配置项 |
| 3 | `refactor(snowflake): ZK 依赖改为 optional` | pom.xml 调整 |
| 4 | `feat(snowflake): 支持时钟回拨时切换 Worker ID` | 预分配备用 ID + switchWorkerId + 回拨逻辑修改 |
| 5 | `test(snowflake): 补充 DB 分配和回拨切换测试` | 单元测试 + 集成测试 |
| 6 | `docs: 更新架构文档` | architecture.md + 配置说明 |
