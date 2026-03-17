# ID Generator 服务使用文档

## 1. 文档目的

这份文档面向接入方和运维方，说明如何启动 `id-generator` 服务、如何准备 `Segment` 业务标签、如何通过 REST API 或 Java SDK 使用发号能力。

当前版本的关键前提：

- `Snowflake` Worker ID 分配已统一为 PostgreSQL 租约模式
- 运行时不再依赖 ZooKeeper
- 业务接口统一返回 `ApiResponse<T>`
- 查询类接口的结构化字段都位于 `data` 下

## 2. 能力概览

服务提供两类 ID：

### 2.1 Snowflake ID

适合订单号、消息号、日志流水号这类需要趋势递增、可按时间大致排序的场景。

特点：

- 64 位 long
- 高吞吐、低延迟
- 不需要提前按业务维度建表
- 单个 `datacenterId` 下最多支持 32 个活跃 Worker ID

### 2.2 Segment ID

适合按业务线隔离号段的场景，例如 `user`、`order`、`message`。

特点：

- 每个 `bizTag` 独立发号
- 依赖 `leaf_alloc` 表预先维护业务标签
- 支持双缓冲与动态步长

## 3. 运行前准备

### 3.1 环境要求

- JDK 17
- Maven 3.9+
- PostgreSQL 16

### 3.2 初始化数据库

创建数据库后执行 [sql/schema.sql](/home/azhi/workspace/projects/id-generator/sql/schema.sql)：

```sql
CREATE DATABASE id_generator;
```

```bash
psql -U postgres -d id_generator -f sql/schema.sql
```

脚本会初始化两张核心表：

- `leaf_alloc`: Segment 号段分配表
- `worker_id_alloc`: Snowflake Worker ID 租约表

同时会预置以下 `bizTag`：

- `default`
- `user`
- `order`
- `message`

### 3.3 服务端核心配置

默认配置位于 [application.yml](/home/azhi/workspace/projects/id-generator/id-generator-server/src/main/resources/application.yml)。

关键项如下：

```yaml
server:
  port: 8011

spring:
  datasource:
    url: jdbc:postgresql://localhost:5435/id_generator
    username: id_gen_user
    password: id_gen_password

id-generator:
  snowflake:
    datacenter-id: 0
    worker-id: -1
    worker-id-lease-timeout: 10m
    worker-id-renew-interval: 3m
    epoch: 1735689600000

  segment:
    cache-update-interval: 60
    segment-duration: 900000
    max-step: 1000000
    update-thread-pool-size: 5
```

说明：

- `worker-id=-1` 表示服务启动时从 `worker_id_alloc` 自动抢占可用 Worker ID
- `datacenter-id` 与 `worker-id` 一起决定 Snowflake ID 中的节点位
- `worker-id-renew-interval` 建议保持为租约超时时间的 1/3 左右

## 4. 启动服务

### 4.1 方式一：源码直接运行

先准备 PostgreSQL，再启动服务端：

```bash
cd id-generator-server
mvn spring-boot:run
```

源码默认监听端口是 `8011`。

验证命令：

```bash
curl http://localhost:8011/api/v1/id/health
curl http://localhost:8011/api/v1/id/snowflake
```

### 4.2 方式二：使用 Docker

项目里当前有两类 Docker 示例：

- [docker/](/home/azhi/workspace/projects/id-generator/docker): 本地开发脚本，常见暴露端口是 `8010`
- [deploy/](/home/azhi/workspace/projects/id-generator/deploy): 部署与扩缩容脚本，常见暴露端口是 `8011` 或 `8011-8020`

先启动数据库：

```bash
cd docker
docker-compose up -d id-generator-postgres
```

如果要启动完整环境：

```bash
docker-compose --profile full up -d
```

注意：

- 由于现有 Docker 示例存在端口映射差异，请以实际 `docker compose ps` 输出为准
- 访问 API 前，先确认容器暴露的宿主机端口

## 5. 准备 Segment 业务标签

如果你要使用 Segment 模式，必须先在 `leaf_alloc` 中准备对应的 `bizTag`。

示例：

```sql
INSERT INTO leaf_alloc (biz_tag, max_id, step, description)
VALUES ('payment', 1, 5000, 'Payment ID sequence')
ON CONFLICT (biz_tag) DO NOTHING;
```

字段建议：

- `biz_tag`: 业务标签，接口路径会直接使用它
- `max_id`: 当前已分配的最大值，初始化通常写 `1`
- `step`: 每次申请的新号段步长
- `description`: 可选说明

如果业务标签不存在，请求 Segment 接口会返回：

- HTTP `404`
- `errorCode=BIZ_TAG_NOT_EXISTS`

## 6. REST API 使用方式

基础路径：`/api/v1/id`

### 6.1 统一响应结构

成功响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": 123456789012345678
}
```

查询接口示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "snowflake": {
      "initialized": true,
      "workerId": 7,
      "datacenterId": 0
    }
  }
}
```

失败响应示例：

```json
{
  "code": 40401,
  "message": "Business tag does not exist: payment",
  "errorCode": "BIZ_TAG_NOT_EXISTS"
}
```

结论很简单：

- 单值发号接口从 `data` 直接取 long
- 查询接口从 `data.xxx` 取结构化字段
- 失败时关注 `errorCode`

### 6.2 Snowflake 接口

生成单个 ID：

```bash
curl http://localhost:8011/api/v1/id/snowflake
```

批量生成：

```bash
curl "http://localhost:8011/api/v1/id/snowflake/batch?count=10"
```

查看 Worker 信息：

```bash
curl http://localhost:8011/api/v1/id/snowflake/info
```

解析 Snowflake ID：

```bash
curl http://localhost:8011/api/v1/id/snowflake/parse/139370018650464256
```

`count` 取值范围是 `1-1000`。

### 6.3 Segment 接口

生成单个 ID：

```bash
curl http://localhost:8011/api/v1/id/segment/order
```

批量生成：

```bash
curl "http://localhost:8011/api/v1/id/segment/order/batch?count=10"
```

查询所有业务标签：

```bash
curl http://localhost:8011/api/v1/id/tags
```

查看某个 `bizTag` 的缓存状态：

```bash
curl http://localhost:8011/api/v1/id/cache/order
```

### 6.4 健康检查

```bash
curl http://localhost:8011/api/v1/id/health
```

重点字段：

- `data.status`
- `data.segment.initialized`
- `data.segment.bizTagCount`
- `data.snowflake.initialized`
- `data.snowflake.workerId`

## 7. Java 客户端接入

### 7.1 直接使用 Client SDK

添加依赖：

```xml
<dependency>
    <groupId>com.architectcgz</groupId>
    <artifactId>id-generator-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

示例代码：

```java
import com.platform.idgen.client.BufferedIdGeneratorClient;
import com.platform.idgen.client.IdGeneratorClient;
import com.platform.idgen.client.config.IdGeneratorClientConfig;

IdGeneratorClientConfig config = IdGeneratorClientConfig.builder()
        .serverUrl("http://localhost:8011")
        .bufferSize(100)
        .refillThreshold(20)
        .batchFetchSize(50)
        .asyncRefill(true)
        .bufferEnabled(true)
        .build();

try (IdGeneratorClient client = new BufferedIdGeneratorClient(config)) {
    long snowflakeId = client.nextSnowflakeId();
    long orderId = client.nextSegmentId("order");
}
```

常用能力：

- `nextSnowflakeId()`
- `nextSnowflakeIds(count)`
- `nextSegmentId(bizTag)`
- `nextSegmentIds(bizTag, count)`
- `parseSnowflakeId(id)`
- `isHealthy()`

客户端行为说明：

- 默认启用本地缓冲
- 默认服务地址是 `http://localhost:8011`
- `isHealthy()` 会读取 `/api/v1/id/health`，并检查 `data.status == UP`

### 7.2 在 Spring Boot 中使用 Starter

添加依赖：

```xml
<dependency>
    <groupId>com.architectcgz</groupId>
    <artifactId>id-generator-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

配置：

```yaml
id-generator:
  client:
    enabled: true
    server-url: http://localhost:8011
    connect-timeout-ms: 5000
    read-timeout-ms: 5000
    max-retries: 3
    buffer-size: 100
    refill-threshold: 20
    batch-fetch-size: 50
    async-refill: true
    buffer-enabled: true
```

在业务代码中注入：

```java
import com.platform.idgen.client.IdGeneratorClient;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final IdGeneratorClient idGeneratorClient;

    public OrderService(IdGeneratorClient idGeneratorClient) {
        this.idGeneratorClient = idGeneratorClient;
    }

    public long nextOrderId() {
        return idGeneratorClient.nextSnowflakeId();
    }
}
```

## 8. 常见错误与处理建议

| HTTP | errorCode | 说明 | 建议 |
|------|-----------|------|------|
| 400 | `INVALID_ARGUMENT` | 参数非法，例如 `count<=0` 或 `bizTag` 为空 | 修正调用参数 |
| 404 | `BIZ_TAG_NOT_EXISTS` | Segment 业务标签不存在 | 先在 `leaf_alloc` 中初始化业务标签 |
| 503 | `SEGMENTS_NOT_READY` | 当前和备用号段都不可用 | 检查数据库连接与 `leaf_alloc` 更新情况 |
| 503 | `SEGMENT_UPDATE_FAILED` | 号段更新失败 | 检查数据库写入、锁竞争、表数据 |
| 503 | `WORKER_ID_UNAVAILABLE` | 没有可分配的 Worker ID | 检查活跃实例数是否超过容量 |
| 503 | `WORKER_ID_INVALID` | Worker ID 租约续期失败 | 检查数据库可用性和租约续期日志 |
| 503 | `SERVICE_SHUTTING_DOWN` | 服务正在优雅关闭 | 重试到其他实例 |
| 503 | `SNOWFLAKE_NOT_INITIALIZED` | Snowflake Worker 尚未初始化成功 | 检查数据库、配置和启动日志 |
| 500 | `CLOCK_BACKWARDS` | 时钟回拨超过可等待阈值 | 检查宿主机时钟同步 |
| 500 | `ILLEGAL_STATE` | 未预期内部状态错误 | 结合日志排查实现缺陷或状态污染 |

## 9. 运维建议

### 9.1 容量边界

- 单个 `datacenterId` 最多 32 个活跃 Worker ID
- 如果实例数会超过 32，需拆分 `datacenterId` 或调整位分配方案

### 9.2 健康检查建议

优先检查：

- `/api/v1/id/health`
- 数据库连接是否正常
- `worker_id_alloc` 中是否存在长期未释放的 `active` 记录

### 9.3 推荐监控项

- Snowflake 初始化成功率
- Worker ID 租约续期失败次数
- Segment 号段更新失败次数
- 接口 4xx / 5xx 比例

## 10. 选型建议

优先使用 `Snowflake` 的场景：

- 没有业务标签隔离需求
- 更关注吞吐和低延迟
- 希望 ID 带时间趋势

优先使用 `Segment` 的场景：

- 不同业务线需要独立号段
- 需要直接按 `bizTag` 管理发号策略
- 能接受提前初始化业务标签

## 11. 相关文档

- [README.md](/home/azhi/workspace/projects/id-generator/README.md)
- [docs/architecture.md](/home/azhi/workspace/projects/id-generator/docs/architecture.md)
- [sql/schema.sql](/home/azhi/workspace/projects/id-generator/sql/schema.sql)
- [deploy/COMMANDS.md](/home/azhi/workspace/projects/id-generator/deploy/COMMANDS.md)
