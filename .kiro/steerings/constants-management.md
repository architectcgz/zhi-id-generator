---
inclusion: fileMatch
fileMatchPattern: "**/*.java"
---

# 常量管理规范

本项目使用 Java 常量类（而非配置文件）来管理各类常量字符串。

## 常量分类与管理方式

| 常量类型 | 管理方式 | 位置 | 示例 |
|---------|---------|------|------|
| RocketMQ Topics/Tags/Consumer Groups | `TopicConstants.java` | blog-common | `TopicConstants.POST_TOPIC` |
| Redis Keys | 各服务 `*RedisKeys.java` | 各服务 infrastructure/cache | `UserRedisKeys.userInfo(userId)` |
| 协议内部常量（如 STOMP 目的地） | 类内部 private static final | 使用该常量的类 | `MESSAGE_DESTINATION = "/queue/messages"` |

## 具体规范

### 1. RocketMQ 常量
- 位置：`blog-common/src/main/java/com/blog/common/mq/TopicConstants.java`
- 包含：Topic 名称、Tag 名称、Consumer Group 名称
- 格式：`public static final String XXX_TOPIC = "xxx-topic";`

### 2. Redis Key 常量
- 位置：各服务 `infrastructure/cache/*RedisKeys.java`
- 命名：`{服务名}RedisKeys.java`（如 `UserRedisKeys.java`、`PostRedisKeys.java`、`MessageRedisKeys.java`）
- Key 命名规范：`{service}:{id}:{entity}:{field}`
  - 示例：`user:123:detail`、`user:123:stats:following`、`post:456:detail`
  - 排行榜特殊格式：`ranking:{type}:{dimension}:{period}`（如 `ranking:posts:hot`、`ranking:posts:daily:2025-01-15`）
- 格式：使用静态方法返回 key 字符串，支持参数化
```java
public class UserRedisKeys {
    private static final String PREFIX = "user";
    
    // user:{userId}:detail
    public static String userDetail(String userId) {
        return PREFIX + ":" + userId + ":detail";
    }
    
    // user:{userId}:stats:following
    public static String followingCount(String userId) {
        return PREFIX + ":" + userId + ":stats:following";
    }
}
```

### 3. 协议内部常量
- 如 WebSocket STOMP 目的地、HTTP Header 名称等
- 保留在使用该常量的类内部
- 使用 `private static final` 修饰
- 添加注释说明这是内部实现细节

## 不使用配置文件的原因

1. **类型安全**：Java 常量在编译期检查，避免拼写错误
2. **IDE 支持**：支持跳转、重构、查找引用
3. **一致性**：项目已有的模式，保持统一
4. **简单性**：无需额外的配置加载逻辑

## 何时使用配置文件

以下情况应使用 `application.yml` 配置：
- 需要在不同环境（dev/test/prod）使用不同值
- 需要运行时动态修改
- 外部系统连接信息（数据库、Redis、MQ 地址等）
- 业务参数（如限流阈值、超时时间等）
