# 避免硬编码规范

## 原则

**所有可配置的常量必须提取到配置文件中，禁止在代码中硬编码。**

---

## 需要配置化的常量类型

### 1. 时间相关常量
- Token 过期时间
- 缓存过期时间
- 连接超时时间
- 重试间隔时间

**❌ 错误示例：**
```java
private static final long ACCESS_TOKEN_EXPIRE = 7200;  // 硬编码
private static final long CACHE_EXPIRE = 3600;
```

**✅ 正确示例：**
```java
@Value("${im.auth.token.access-expire}")
private long accessTokenExpire;

@Value("${im.cache.user.expire}")
private long cacheExpire;
```

---

### 2. Redis Key 前缀
- 缓存 Key 前缀
- 锁 Key 前缀
- 序列号 Key 前缀

**❌ 错误示例：**
```java
private static final String TOKEN_KEY_PREFIX = "token:";
private static final String USER_CACHE_KEY = "user:info:";
```

**✅ 正确示例：**
```java
@Value("${im.auth.token.key-prefix}")
private String tokenKeyPrefix;

@Value("${im.cache.user.key-prefix}")
private String userCacheKeyPrefix;
```

---

### 3. 消息队列配置
- Topic 名称
- Tag 名称
- Consumer Group 名称

**❌ 错误示例：**
```java
private static final String SINGLE_MESSAGE_TOPIC = "im-message-persist:single";
```

**✅ 正确示例：**
```java
@Value("${rocketmq.topic.message-persist}")
private String messagePersistTopic;

@Value("${rocketmq.tag.single-message}")
private String singleMessageTag;
```

---

### 4. 业务规则常量
- 群组最大成员数
- 分表数量
- 批量操作大小限制

**❌ 错误示例：**
```java
group.setMaxMemberCount(groupType == 1 ? 200 : 2000);  // 硬编码
private static final int TABLE_COUNT = 8;
```

**✅ 正确示例：**
```java
@Value("${im.group.normal.max-members}")
private int normalGroupMaxMembers;

@Value("${im.group.super.max-members}")
private int superGroupMaxMembers;

@Value("${im.sharding.message-history.table-count}")
private int tableCount;
```

---

### 5. Snowflake 算法配置
- Worker ID
- Datacenter ID
- Epoch（起始时间戳）

**❌ 错误示例：**
```java
private final long twepoch = 1577808000000L;
private long workerId = 1;
private long datacenterId = 1;
```

**✅ 正确示例：**
```java
@Value("${im.snowflake.worker-id}")
private long workerId;

@Value("${im.snowflake.datacenter-id}")
private long datacenterId;

@Value("${im.snowflake.epoch}")
private long twepoch;
```

---

## 可以保留的硬编码

以下情况的硬编码是合理的，**不需要**配置化：

### 1. 业务状态码
```java
// ✅ 合理：业务逻辑判断
if (user.getStatus() == 0) {  // 0 表示正常状态
    // ...
}

if (conversationType == 1) {  // 1 表示单聊
    // ...
}
```

### 2. 枚举值
```java
// ✅ 合理：使用枚举类
public enum GroupMemberRole {
    MEMBER(1, "普通成员"),
    ADMIN(2, "管理员"),
    OWNER(3, "群主");
}
```

### 3. 算法常量
```java
// ✅ 合理：算法固定常量
private final long workerIdBits = 5L;
private final long sequenceBits = 12L;
private final long sequenceMask = -1L ^ (-1L << sequenceBits);
```

---

## 配置文件组织

### application.yml 结构

```yaml
# IM系统配置
im:
  # 认证配置
  auth:
    token:
      key-prefix: "im:token:"
      lock-prefix: "im:lock:token:"
      access-expire: 7200      # 2小时（秒）
      refresh-expire: 604800   # 7天（秒）
  
  # 缓存配置
  cache:
    user:
      key-prefix: "im:user:info:"
      expire: 3600  # 1小时（秒）
  
  # 序列号配置
  sequence:
    key-prefix: "im:seq:"
  
  # Snowflake配置
  snowflake:
    worker-id: 1
    datacenter-id: 1
    epoch: 1577808000000  # 2020-01-01 00:00:00
  
  # 群组配置
  group:
    normal:
      max-members: 200
    super:
      max-members: 2000
  
  # 分表配置
  sharding:
    message-history:
      table-count: 8
    group-message-history:
      table-count: 8

# RocketMQ配置
rocketmq:
  name-server: localhost:9876
  producer:
    group: im-message-producer
  consumer:
    group:
      single-message: im-persist-single-consumer
      group-message: im-persist-group-consumer
  topic:
    message-persist: im-message-persist
  tag:
    single-message: single
    group-message: group
```

---

## 配置注入方式

### 1. 使用 @Value 注解（推荐用于简单配置）

```java
@Service
public class UserService {
    
    @Value("${im.cache.user.key-prefix}")
    private String userCacheKeyPrefix;
    
    @Value("${im.cache.user.expire}")
    private long cacheExpire;
}
```

### 2. 使用 @ConfigurationProperties（推荐用于复杂配置）

```java
@Component
@ConfigurationProperties(prefix = "im.auth.token")
@Data
public class TokenProperties {
    private String keyPrefix;
    private String lockPrefix;
    private long accessExpire;
    private long refreshExpire;
}

@Service
public class AuthService {
    
    @Autowired
    private TokenProperties tokenProperties;
    
    public void generateToken() {
        String key = tokenProperties.getKeyPrefix() + userId;
        // ...
    }
}
```

---

## 检查清单

在编写代码时，检查以下内容：

- [ ] 是否有 `private static final` 声明的业务常量？
- [ ] 是否有硬编码的数字（时间、数量、大小限制）？
- [ ] 是否有硬编码的字符串（Key前缀、Topic名称）？
- [ ] 是否有环境相关的配置（端口、地址、ID）？
- [ ] 配置是否已添加到 `application.yml`？
- [ ] 配置是否有清晰的注释说明？

---

## 好处

1. **灵活性**：不同环境（开发、测试、生产）可使用不同配置
2. **可维护性**：修改配置无需重新编译代码
3. **可扩展性**：新增配置项无需修改代码逻辑
4. **可测试性**：测试时可轻松覆盖配置值
5. **可观测性**：配置集中管理，便于审计和监控

---

## 反例警示

### 案例1：硬编码导致的生产事故

```java
// ❌ 开发环境硬编码
private static final int MAX_RETRY = 3;
private static final int TIMEOUT = 1000;  // 1秒

// 生产环境网络延迟高，1秒超时导致大量失败
// 需要重新编译、打包、部署才能修改
```

### 案例2：硬编码导致的扩展困难

```java
// ❌ 硬编码分表数量
private static final int TABLE_COUNT = 8;

// 数据量增长需要扩展到16张表
// 需要修改代码、重新部署所有节点
```

---

## 总结

**记住：如果一个值将来可能需要调整，就应该放到配置文件中！**
