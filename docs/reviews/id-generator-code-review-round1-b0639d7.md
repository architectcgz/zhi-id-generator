# id-generator 代码 Review（第 1 轮）：数据库分配 Worker ID + 条件注入切换

## Review 信息

| 字段 | 说明 |
|------|------|
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | 9abf110..b0639d7（2 个 commit），6 个文件，+436/-6 行 |
| 变更概述 | 实现基于数据库的 Worker ID 抢占/续期/释放机制，通过条件注解切换 DB/ZK 两种实现 |
| 审查基准 | `docs/architecture.md`、`docs/todo.md`（任务 1.2-1.4） |
| 审查日期 | 2026-02-28 |

## 问题清单

### 🔴 高优先级

#### [H1] `registerWorkerId()` 的 `@Transactional` 无法覆盖 SELECT FOR UPDATE

- **文件**：`id-generator-server/src/main/java/com/platform/idgen/infrastructure/repository/DbWorkerIdRepositoryImpl.java:72`
- **问题描述**：`registerWorkerId()` 标注了 `@Transactional`，但实际的 SELECT FOR UPDATE 和 UPDATE 操作在私有方法 `acquireFromDatabase()` 中执行。由于 Spring AOP 代理机制，`@Transactional` 只在外部调用时生效。然而更关键的问题是：`selectAvailableWorkerId()`（SELECT FOR UPDATE SKIP LOCKED）和 `acquireWorkerId()`（UPDATE）是两次独立的 Mapper 调用，它们必须在同一个事务内才能保证行锁的有效性。当前 `@Transactional` 加在 `registerWorkerId()` 上，而该方法在 SELECT FOR UPDATE 之前还有静态配置检查和 `buildInstanceId()` 等非数据库操作，事务持有时间偏长，但功能上是正确的。
- **真正的风险**：`acquireWorkerId()` 的 UPDATE 语句没有加 WHERE 条件校验 `status`，意味着即使该行在 SELECT 和 UPDATE 之间被其他事务（通过不同路径）修改了状态，UPDATE 仍会成功。虽然 FOR UPDATE SKIP LOCKED 在同一事务内持有行锁可以防止这种情况，但 UPDATE 缺少防御性条件是一个隐患。
- **影响范围/风险**：如果未来代码重构导致事务边界变化，可能出现两个实例抢到同一个 Worker ID
- **修正建议**：`acquireWorkerId` 的 UPDATE 增加防御性 WHERE 条件：

```xml
<update id="acquireWorkerId">
    UPDATE worker_id_alloc
    SET status      = 'active',
        instance_id = #{instanceId},
        lease_time  = NOW()
    WHERE worker_id = #{workerId}
      AND (status = 'released'
           OR (status = 'active' AND lease_time &lt; NOW() - CAST(#{leaseTimeoutMinutes} || ' minutes' AS INTERVAL)))
</update>
```

同时 Mapper 接口增加 `leaseTimeoutMinutes` 参数。
