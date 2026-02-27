# Q2: 一定要用 ZooKeeper 吗？

> 基于代码实际实现分析，2026-02-27

## 结论

不一定。ZK 在这个项目里只做了一件事：**给 Snowflake 模式分配不重复的 Worker ID**。而且代码已经内置了不用 ZK 的路径。

---

## 1. ZK 在项目中到底做了什么？

翻遍所有引用 ZK 的代码，ZK 的职责只有一个：

**在 `WorkerIdRepositoryImpl.registerWithZooKeeper()` 中创建 `PERSISTENT_SEQUENTIAL` 节点，用序列号 % 32 得到 Worker ID（0-31）。**

就这些。没有用 ZK 做分布式锁、没有做服务发现、没有做配置中心。

## 2. 代码已经支持不用 ZK

`WorkerIdRepositoryImpl.registerWorkerId()` 的第一个分支：

```java
// 静态配置 worker-id >= 0 时，直接用配置值，完全跳过 ZK
if (snowflakeProperties.getWorkerId() >= 0) {
    registeredWorkerId = new WorkerId(snowflakeProperties.getWorkerId());
    return registeredWorkerId;
}
```

只需要这样配置就能完全绕过 ZK：

```yaml
id-generator:
  snowflake:
    enable-zookeeper: false
    worker-id: 0          # 手动指定 0-31
```

**所以现在就可以不用 ZK，前提是你能手动保证每个实例的 worker-id 不重复。**

## 3. ZK 解决的核心问题：Worker ID 自动分配

问题本质很简单：Snowflake 要求同一集群内每个实例的 Worker ID（0-31）不能重复，否则会生成重复 ID。

| 部署方式 | 是否需要自动分配 |
|---------|----------------|
| 单实例 | 不需要，写死 `worker-id: 0` 就行 |
| 固定数量多实例 | 不需要，每个实例配不同值即可 |
| 动态扩缩容 | 需要，手动管理容易出错 |

## 4. 如果要替换 ZK，有哪些方案？

核心需求就是「分配一个 0-31 范围内不重复的整数」，实现方式很多：

### 方案 A：数据库自增（最简单，推荐）

用已有的 PostgreSQL 就能做，新建一张表：

```sql
CREATE TABLE worker_id_alloc (
    id SERIAL PRIMARY KEY,
    instance_ip VARCHAR(64),
    registered_at TIMESTAMP DEFAULT now()
);
```

启动时 INSERT 一行，拿 `id % 32` 作为 Worker ID。零额外依赖。

### 方案 B：Redis INCR

如果项目已经用了 Redis：

```
INCR id-generator:worker-id-seq
```

拿返回值 % 32。比数据库更快，但多了 Redis 依赖。

### 方案 C：环境变量 / 容器编排

K8s StatefulSet 的 Pod 名自带序号（`pod-0`, `pod-1`, ...），直接用序号作为 Worker ID。最适合容器化部署，零外部依赖。

### 方案 D：IP/MAC 哈希

用实例 IP 或 MAC 地址哈希取模。简单但有碰撞风险，实例少时可用。

## 5. 各方案对比

| 方案 | 额外依赖 | 实现复杂度 | 碰撞风险 | 适用场景 |
|------|---------|-----------|---------|---------|
| ZK（当前） | ZooKeeper 集群 | 中 | 无 | 已有 ZK 基础设施 |
| 静态配置 | 无 | 低 | 人为配错 | 单实例 / 固定实例数 |
| 数据库自增 | 无（复用 PG） | 低 | 无 | 通用，推荐替代方案 |
| Redis INCR | Redis | 低 | 无 | 已有 Redis |
| K8s 序号 | 无 | 低 | 无 | 容器化部署 |
| IP/MAC 哈希 | 无 | 低 | 有 | 实例数远小于 32 |

## 6. 替换 ZK 的改动量

代码架构上已经做了很好的抽象。ZK 的使用被封装在 `WorkerIdRepository` 接口背后，只有 `WorkerIdRepositoryImpl` 一个实现类直接依赖 Curator。

替换只需要：
1. 新写一个 `WorkerIdRepository` 实现（如 `DbWorkerIdRepositoryImpl`）
2. 配置条件注入（`@ConditionalOnProperty`）
3. 不需要改领域层任何代码

改动范围：1 个新文件 + 1 处配置，核心逻辑零侵入。

## 7. 总结

ZK 不是必须的。它在这个项目里只做了 Worker ID 自动分配这一件事，代码已经预留了静态配置的绕过路径。如果你的部署场景是单实例或固定实例数，直接配 `worker-id: 0` 就行；如果需要动态分配但不想引入 ZK，用数据库自增是最简单的替代方案，零额外依赖。
