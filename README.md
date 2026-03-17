# ID Generator

一个高性能的分布式ID生成服务，支持Snowflake和Segment两种算法。

[English](./README_EN.md) | 简体中文

## ✨ 特性

- 🚀 **高性能**: Snowflake模式QPS 100,000+，Segment模式QPS 50,000+
- 🔄 **双模式**: 支持Snowflake（时间戳）和Segment（数据库序列）两种模式
- 📦 **开箱即用**: 提供Spring Boot Starter，零配置快速集成
- 🎯 **业务隔离**: Segment模式支持按业务标签隔离ID序列
- 💾 **本地缓冲**: 客户端内置缓冲机制，减少网络请求
- 🔧 **易于部署**: 提供Docker Compose一键部署方案

## 📦 模块说明

- **id-generator-server**: ID生成服务，提供REST API
- **id-generator-client**: Java SDK客户端
- **id-generator-spring-boot-starter**: Spring Boot自动配置

## 🚀 快速开始

### 1. 启动服务

#### 使用Docker（推荐）

```bash
# 启动基础设施（PostgreSQL）
cd docker/scripts
./start-infra.sh    # Linux/Mac
start-infra.bat     # Windows

# 启动ID Generator服务
cd ../../id-generator-server
mvn spring-boot:run
```

或者启动完整环境：

```bash
cd docker/scripts
./start-full.sh     # Linux/Mac
start-full.bat      # Windows
```

#### 验证服务

```bash
curl http://localhost:8011/actuator/health
curl http://localhost:8011/api/v1/id/snowflake
```

### 2. 集成到你的项目

#### Spring Boot项目（推荐）

**添加依赖**：

```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>id-generator-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**配置**（application.yml）：

```yaml
id-generator:
  client:
    server-url: http://localhost:8011
    buffer-enabled: true
    buffer-size: 100
```

**使用**：

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final IdGeneratorClient idGeneratorClient;
    
    public Order createOrder() {
        // 生成订单ID（Snowflake模式）
        long orderId = idGeneratorClient.nextSnowflakeId();
        
        Order order = new Order();
        order.setId(orderId);
        // ... 业务逻辑
        
        return order;
    }
    
    public User createUser() {
        // 生成用户ID（Segment模式）
        long userId = idGeneratorClient.nextSegmentId("user");
        
        User user = new User();
        user.setId(userId);
        // ... 业务逻辑
        
        return user;
    }
}
```

#### 普通Java项目

```java
// 创建客户端配置
IdGeneratorClientConfig config = IdGeneratorClientConfig.builder()
    .serverUrl("http://localhost:8011")
    .bufferSize(100)
    .bufferEnabled(true)
    .build();

// 创建客户端实例
IdGeneratorClient client = new BufferedIdGeneratorClient(config);

// 使用
long id = client.nextSnowflakeId();
long seqId = client.nextSegmentId("order");

// 关闭客户端
client.close();
```

## 📖 使用指南

### Snowflake模式

适用于需要时间排序的场景（订单、消息、日志等）：

```java
// 生成单个ID
long id = idGeneratorClient.nextSnowflakeId();

// 批量生成（更高效）
List<Long> ids = idGeneratorClient.nextSnowflakeIds(100);

// 解析ID
SnowflakeIdInfo info = idGeneratorClient.parseSnowflakeId(id);
System.out.println("时间戳: " + info.getTimestamp());
System.out.println("数据中心ID: " + info.getDatacenterId());
System.out.println("Worker ID: " + info.getWorkerId());
```

### Segment模式

适用于需要业务隔离的场景（用户、商品、分类等）：

```java
// 为不同业务生成ID
long userId = idGeneratorClient.nextSegmentId("user");
long orderId = idGeneratorClient.nextSegmentId("order");
long productId = idGeneratorClient.nextSegmentId("product");

// 批量生成
List<Long> orderIds = idGeneratorClient.nextSegmentIds("order", 100);
```

### 添加业务标签

在使用Segment模式前，需要在数据库中添加业务标签：

```sql
INSERT INTO leaf_alloc (biz_tag, max_id, step, description)
VALUES ('your-tag', 1, 1000, 'Your business description');
```

## 🔧 构建项目

```bash
# 构建所有模块
mvn clean install

# 仅构建服务端
cd id-generator-server
mvn clean package

# 仅构建客户端
cd id-generator-client
mvn clean package
```

## 🐳 Docker部署

详细的Docker部署说明请查看 [docker/README.md](docker/README.md)

```bash
# 先在仓库根目录构建可执行服务端 JAR
mvn -q -pl id-generator-server -am -DskipTests package

# 启动基础设施
cd docker
docker-compose up -d id-generator-postgres

# 启动完整环境（包括服务）
docker-compose --profile full up -d

# 停止服务
docker-compose down
```

## 📚 API接口

### Snowflake模式

- `GET /api/v1/id/snowflake` - 生成单个Snowflake ID
- `GET /api/v1/id/snowflake/batch?count=10` - 批量生成Snowflake ID
- `GET /api/v1/id/snowflake/parse/{id}` - 解析Snowflake ID
- `GET /api/v1/id/snowflake/info` - 获取Snowflake Worker信息

### Segment模式

- `GET /api/v1/id/segment/{bizTag}` - 生成单个Segment ID
- `GET /api/v1/id/segment/{bizTag}/batch?count=10` - 批量生成Segment ID
- `GET /api/v1/id/tags` - 获取所有业务标签

### 健康检查

- `GET /api/v1/id/health` - 健康检查
- `GET /actuator/health` - Spring Boot健康检查

### 响应结构

业务接口统一返回 `ApiResponse<T>`，查询类接口的结构化字段位于 `data` 下：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "service": "id-generator-service",
    "segment": {
      "initialized": true,
      "bizTagCount": 2
    },
    "snowflake": {
      "initialized": true,
      "workerId": 7,
      "datacenterId": 3
    }
  }
}
```

查询接口：

- `/api/v1/id/health` -> `data.status`、`data.segment.*`、`data.snowflake.*`
- `/api/v1/id/snowflake/info` -> `data.initialized`、`data.workerId`、`data.datacenterId`、`data.epoch`
- `/api/v1/id/snowflake/parse/{id}` -> `data.id`、`data.timestamp`、`data.sequence` 等解析字段
- `/api/v1/id/cache/{bizTag}` -> `data.currentSegment`、`data.nextSegment` 等缓存快照字段

常见错误响应会返回 `errorCode`，例如 `BIZ_TAG_NOT_EXISTS`、`SEGMENT_UPDATE_FAILED`、`SERVICE_SHUTTING_DOWN`、`SNOWFLAKE_NOT_INITIALIZED`；未预期的内部状态错误统一返回 `ILLEGAL_STATE`（HTTP 500）。

## 📖 文档

- [📗 服务使用文档](docs/USAGE_GUIDE.md) - 服务启动、API 调用、SDK 接入与排障
- [📘 集成指南](docs/INTEGRATION_GUIDE.md) - 详细的集成步骤和示例
- [📝 快速参考](docs/QUICK_REFERENCE.md) - 常用命令和代码片段
- [🐳 Docker部署](docker/README.md) - Docker部署指南
- [💡 示例代码](examples/spring-boot-example/) - Spring Boot完整示例

## ⚙️ 配置说明

### 服务端配置

详细配置请查看 [application.yml](id-generator-server/src/main/resources/application.yml)

主要配置项：

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:postgresql://localhost:5435/id_generator
    username: id_gen_user
    password: id_gen_password

# Snowflake配置
id-generator:
  snowflake:
    datacenter-id: 0
    worker-id: -1
    worker-id-lease-timeout: 10m
    worker-id-renew-interval: 3m
```

### 客户端配置

```yaml
id-generator:
  client:
    server-url: http://localhost:8011
    buffer-enabled: true
    buffer-size: 100
    refill-threshold: 20
    async-refill: true
```

## 🎯 性能指标

| 指标 | Snowflake模式 | Segment模式 |
|------|--------------|------------|
| QPS | 100,000+ | 50,000+ |
| 延迟（P99） | < 1ms | < 5ms |
| 可用性 | 99.99% | 99.9% |

## 🛠️ 技术栈

- **语言**: Java 17
- **框架**: Spring Boot 3.2.1
- **数据库**: PostgreSQL 16
- **Worker ID 分配**: PostgreSQL 租约抢占
- **ORM**: MyBatis 3.0.3
- **构建工具**: Maven 3.9+

## 📊 架构图

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client 1  │     │   Client 2  │     │   Client N  │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                  ┌────────▼────────┐
                  │  ID Generator   │
                  │     Server      │
                  └────────┬────────┘
                           │
              ┌────────────┼────────────┐
              │                         │
       ┌───────────────────────────────▼───────────────────────────────┐
       │                         PostgreSQL                             │
       │     Segment 号段分配 + Snowflake Worker ID 租约抢占/续期        │
       └───────────────────────────────────────────────────────────────┘
```

## 🤝 贡献

欢迎贡献代码、文档或提出建议！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交变更 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📝 许可证

本项目采用 MIT License - 详见 [LICENSE](LICENSE) 文件

## 💬 技术支持

- 📧 Email: support@platform.com
- 💬 Issues: [GitHub Issues](https://github.com/your-org/id-generator/issues)
- 📖 文档: [在线文档](https://docs.platform.com/id-generator)

## 🙏 致谢

本项目参考了以下优秀的开源项目：

- [Leaf - 美团分布式ID生成系统](https://github.com/Meituan-Dianping/Leaf)
- [Twitter Snowflake](https://github.com/twitter-archive/snowflake)

---

**快速链接**: [集成指南](docs/INTEGRATION_GUIDE.md) | [快速参考](docs/QUICK_REFERENCE.md) | [示例代码](examples/spring-boot-example/) | [Docker部署](docker/README.md)
