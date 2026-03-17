# Q2: 一定要用 ZooKeeper 吗？

> 更新：2026-03-10。当前项目已经移除 ZooKeeper 运行路径，本文只保留结论性说明。

## 结论

不需要。项目当前只支持 **PostgreSQL Worker ID 租约分配**：

- `worker-id >= 0`：使用静态 Worker ID
- `worker-id = -1`：从 `worker_id_alloc` 表自动抢占

也就是说，ZooKeeper 已经不再是这个项目的运行依赖。

## 当前实现

Snowflake Worker ID 的来源只有两种：

1. 静态配置 `worker-id`
2. PostgreSQL `worker_id_alloc` 表自动抢占

示例配置：

```yaml
id-generator:
  snowflake:
    datacenter-id: 0
    worker-id: -1
    worker-id-lease-timeout: 10m
    worker-id-renew-interval: 3m
```

## 为什么收敛到 PostgreSQL

- 减少部署依赖，开发和测试更轻
- Worker ID 分配与业务数据库统一运维
- 对当前 5 bit worker 位宽的容量需求已经足够

## 当前限制

- 单个 `datacenter-id` 下最多 32 个活跃 Worker ID
- 自动分配依赖 PostgreSQL 可用

如果未来需要更大规模扩容，再评估拆分 `datacenter-id`、调整位宽，或重新引入独立协调组件。
