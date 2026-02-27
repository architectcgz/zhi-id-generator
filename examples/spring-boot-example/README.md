# ID Generator Spring Boot Example

这是一个演示如何在Spring Boot项目中集成和使用ID Generator的示例项目。

## 前置条件

1. 启动ID Generator服务及其依赖：

```bash
# 方式1：启动基础设施，然后在IDE中运行id-generator-server
cd ../../docker/scripts
./start-infra.sh    # Linux/Mac
start-infra.bat     # Windows

# 方式2：使用Docker启动完整环境
./start-full.sh     # Linux/Mac
start-full.bat      # Windows
```

2. 验证服务可用：

```bash
curl http://localhost:8010/actuator/health
```

## 运行示例

### 1. 构建项目

```bash
cd examples/spring-boot-example
mvn clean package
```

### 2. 运行应用

```bash
mvn spring-boot:run
```

或者：

```bash
java -jar target/id-generator-spring-boot-example-1.0.0.jar
```

### 3. 测试API

应用启动后，访问以下端点：

#### 生成Snowflake ID

```bash
# 生成单个ID
curl http://localhost:8080/api/demo/snowflake

# 批量生成ID
curl http://localhost:8080/api/demo/snowflake/batch?count=10

# 解析Snowflake ID
curl http://localhost:8080/api/demo/snowflake/parse/123456789
```

#### 生成Segment ID

```bash
# 生成用户ID
curl http://localhost:8080/api/demo/segment/user

# 生成订单ID
curl http://localhost:8080/api/demo/segment/order

# 批量生成
curl http://localhost:8080/api/demo/segment/order/batch?count=10
```

#### 健康检查

```bash
curl http://localhost:8080/api/demo/health
```

## 代码说明

### 1. 依赖配置 (pom.xml)

```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>id-generator-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 应用配置 (application.yml)

```yaml
id-generator:
  client:
    enabled: true
    server-url: http://localhost:8010
    buffer-enabled: true
    buffer-size: 100
```

### 3. 使用客户端

Spring Boot Starter会自动配置 `IdGeneratorClient` Bean，直接注入使用：

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final IdGeneratorClient idGeneratorClient;
    
    public Order createOrder() {
        // 生成订单ID
        long orderId = idGeneratorClient.nextSnowflakeId();
        
        // 批量生成订单项ID
        List<Long> itemIds = idGeneratorClient.nextSnowflakeIds(10);
        
        // ... 业务逻辑
    }
}
```

## 示例场景

### 场景1：生成Snowflake ID

适用于需要时间排序的场景，如订单、消息等。

```java
@GetMapping("/snowflake")
public Map<String, Object> generateSnowflakeId() {
    long id = idGeneratorClient.nextSnowflakeId();
    return Map.of("id", id, "mode", "snowflake");
}
```

### 场景2：生成Segment ID

适用于需要业务隔离的场景，如用户、商品等。

```java
@GetMapping("/segment/{bizTag}")
public Map<String, Object> generateSegmentId(@PathVariable String bizTag) {
    long id = idGeneratorClient.nextSegmentId(bizTag);
    return Map.of("id", id, "bizTag", bizTag, "mode", "segment");
}
```

### 场景3：批量生成ID

提高性能，减少网络请求。

```java
public Order createOrder(List<OrderItemRequest> items) {
    // 生成订单ID
    long orderId = idGeneratorClient.nextSnowflakeId();
    
    // 批量生成订单项ID
    List<Long> itemIds = idGeneratorClient.nextSnowflakeIds(items.size());
    
    // 创建订单和订单项
    // ...
}
```

### 场景4：解析Snowflake ID

提取ID中的时间戳、数据中心ID等信息。

```java
@GetMapping("/snowflake/parse/{id}")
public SnowflakeIdInfo parseSnowflakeId(@PathVariable long id) {
    return idGeneratorClient.parseSnowflakeId(id);
}
```

## 性能优化

### 1. 启用本地缓冲

```yaml
id-generator:
  client:
    buffer-enabled: true
    buffer-size: 200
    async-refill: true
```

### 2. 使用批量接口

```java
// 不推荐：多次调用
for (int i = 0; i < 100; i++) {
    long id = idGeneratorClient.nextSnowflakeId();
}

// 推荐：批量调用
List<Long> ids = idGeneratorClient.nextSnowflakeIds(100);
```

## 故障处理

### 1. 服务不可用

```java
try {
    long id = idGeneratorClient.nextSnowflakeId();
} catch (IdGeneratorException e) {
    // 降级方案：使用UUID
    String uuid = UUID.randomUUID().toString();
}
```

### 2. 健康检查

```java
if (!idGeneratorClient.isHealthy()) {
    log.warn("ID Generator service is unavailable");
    // 使用降级方案
}
```

## 相关文档

- [集成指南](../../docs/INTEGRATION_GUIDE.md)
- [API文档](../../docs/API.md)
- [配置说明](../../docs/CONFIGURATION.md)

## 技术支持

如有问题，请查看[故障排查指南](../../docs/TROUBLESHOOTING.md)或提交Issue。
