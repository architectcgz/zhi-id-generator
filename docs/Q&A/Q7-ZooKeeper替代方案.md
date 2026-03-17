# Q7: 除了 ZooKeeper，还能用什么做协调？

> 2026-02-27
>
> 注：本文是历史性方案比较。当前项目已经移除 ZooKeeper 运行路径，默认且唯一的实现是 PostgreSQL Worker ID 租约分配。

## 结论

ZK 在这个项目里只做了 Worker ID 分配（Q2 已分析），协调需求很轻。替代方案很多，按「是否引入新依赖」分两类。

---

## 1. 先明确：这个项目需要「协调」什么？

翻遍代码，需要协调的事情只有一件：

**确保多个服务实例的 Worker ID（0-31）不重复。**

不需要分布式锁、不需要 Leader 选举、不需要配置推送。这是一个非常轻量的协调需求。

## 2. 零额外依赖的方案

### 方案 A：数据库（复用已有的 PostgreSQL）

```sql
CREATE TABLE worker_id_alloc (
    worker_id   INT PRIMARY KEY,          -- 0-31
    instance_id VARCHAR(128),             -- 实例标识（IP:port）
    lease_time  TIMESTAMP DEFAULT now(),  -- 租约时间
    status      VARCHAR(16) DEFAULT 'active'
);

-- 预填 0-31
INSERT INTO worker_id_alloc (worker_id)
SELECT generate_series(0, 31);
```

启动时抢占一个空闲的：

```sql
UPDATE worker_id_alloc
SET instance_id = 'ip:port', lease_time = now(), status = 'active'
WHERE worker_id = (
    SELECT worker_id FROM worker_id_alloc
    WHERE status = 'released' OR lease_time < now() - INTERVAL '10 minutes'
    ORDER BY worker_id LIMIT 1 FOR UPDATE SKIP LOCKED
)
RETURNING worker_id;
```

优点：零额外依赖，复用现有 PG，支持租约过期自动回收。

### 方案 B：K8s StatefulSet 序号

如果部署在 K8s 上，StatefulSet 的 Pod 名自带序号：

```
id-generator-0  → workerId = 0
id-generator-1  → workerId = 1
id-generator-2  → workerId = 2
```

启动时读环境变量或 hostname 解析序号即可：

```java
String hostname = System.getenv("HOSTNAME"); // "id-generator-2"
int workerId = Integer.parseInt(hostname.substring(hostname.lastIndexOf('-') + 1));
```

优点：零依赖、天然不重复、扩缩容自动管理。
限制：只适用于 K8s StatefulSet 部署。

### 方案 C：静态配置 + 环境变量

最简单的方式，每个实例通过环境变量或启动参数指定：

```bash
# 实例1
java -jar id-generator.jar --id-generator.snowflake.worker-id=0

# 实例2
java -jar id-generator.jar --id-generator.snowflake.worker-id=1
```

当前代码已经支持（`worker-id >= 0` 时直接使用，跳过 ZK）。
限制：人工管理，实例数固定时可用，动态扩缩容不适合。

## 3. 需要引入新依赖的方案

### 方案 D：Redis

```
-- 启动时原子分配
INCR id-generator:worker-id-seq
-- 拿返回值 % 32
```

或者更精确的租约模式：

```
-- 尝试抢占 workerId=3，租约 60 秒
SET id-gen:worker:3 "instance-ip" NX EX 60
-- 成功 → 用 3，失败 → 试下一个
```

优点：性能高、支持租约过期。
适用：项目已经用了 Redis 的场景。

### 方案 E：etcd

和 ZK 定位类似的分布式协调服务，但更轻量：

```bash
# 创建租约（TTL 60秒）
etcdctl lease grant 60

# 用租约绑定 key
etcdctl put /id-gen/worker/3 "instance-ip" --lease=<lease-id>
```

优点：比 ZK 运维更简单、HTTP API 友好、Go 生态原生支持。
适用：已有 etcd 基础设施（如 K8s 集群自带 etcd）。

### 方案 F：Nacos / Consul

如果项目用了微服务注册中心，可以复用它的 KV 存储能力做 Worker ID 分配，原理和 Redis 租约类似。

## 4. 对比总结

| 方案 | 额外依赖 | 动态扩缩容 | 租约/自动回收 | 适用场景 |
|------|---------|-----------|-------------|---------|
| 数据库 | 无（复用 PG） | 支持 | 支持 | 通用，推荐 |
| K8s 序号 | 无 | 自动 | 自动 | K8s StatefulSet |
| 静态配置 | 无 | 不支持 | 不支持 | 单实例 / 固定实例数 |
| Redis | Redis | 支持 | 支持 | 已有 Redis |
| etcd | etcd | 支持 | 支持 | 已有 etcd / K8s |
| ZK（当前） | ZooKeeper | 支持 | 需自行实现 | 已有 ZK |
| Nacos/Consul | 注册中心 | 支持 | 支持 | 微服务架构 |

## 5. 推荐选择

选择原则很简单：**不要为了 Worker ID 分配这一个需求引入新的基础设施。**

- 已有 PG，没有其他中间件 → 用数据库（方案 A）
- 部署在 K8s 上 → 用 StatefulSet 序号（方案 B）
- 已有 Redis → 用 Redis 租约（方案 D）
- 已有 ZK / etcd / Nacos → 继续用，不用换
- 单实例或固定实例数 → 静态配置最省事（方案 C）
