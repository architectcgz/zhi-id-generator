# id-generator 代码 Review（第 2 轮）：第 1 轮 review 问题修复

| 字段 | 说明 |
|------|------|
| 轮次 | 第 2 轮（修复后复审） |
| 审查范围 | 2 commits（73a7d2a..31a033e），8 文件，+82 / -71 行 |
| 变更概述 | 修复第 1 轮 review 的 9 项问题（R1-01 ~ R1-08、R1-10） |
| 审查基准 | `docs/reviews/id-generator-code-review-round1-09efc37.md` |
| 审查日期 | 2026-02-28 |
| 上轮问题数 | 10 项（3 高 / 4 中 / 2 低）→ 9 项已修复，1 项为已知权衡（R1-09） |

---

## 一、逐项复审结果

| 编号 | 级别 | 描述 | 状态 |
|------|------|------|------|
| R1-01 | 高 | SegmentDomainService 领域层依赖基础设施层实体 | ✅ 已修复 |
| R1-02 | 高 | shouldLoadNextSegment() 死代码 | ✅ 已修复 |
| R1-03 | 高 | generateId() 缩进错乱 | ✅ 已修复 |
| R1-04 | 中 | WorkerTimestampCache 适配器未消除 | ✅ 已修复 |
| R1-05 | 中 | 启动等待上限 5000ms 硬编码 | ✅ 已修复 |
| R1-06 | 中 | clockBackwardsWaitThresholdMs 未接入配置 | ✅ 已修复 |
| R1-07 | 中 | AtomicInteger 全限定类名 | ✅ 已修复 |
| R1-08 | 中 | EPHEMERAL 节点 setData 无意义 | ✅ 已修复 |
| R1-09 | 低 | 全写锁性能退化风险 | — 已知权衡，不修 |
| R1-10 | 低 | ClockBackwardsException offset 信息丢失 | ✅ 已修复 |

---

## 二、修复质量确认

### R1-01: SegmentAllocation 值对象引入

- `SegmentAllocation` 放在 `domain.model.valueobject` 包下，位置正确
- `LeafAllocRepository` 接口返回值全部改为 `SegmentAllocation`，领域层零 infrastructure import
- `LeafAllocRepositoryImpl.toSegmentAllocation()` 负责转换，职责清晰

### R1-04: 适配器消除

- `WorkerIdRepository extends WorkerTimestampCache`，接口继承干净
- `SnowflakeDomainService` 直接传 `workerIdRepository` 给 `SnowflakeWorker`，无匿名内部类
- `WorkerTimestampCache` 的 `loadLastUsedTimestamp()` / `saveLastUsedTimestamp()` 方法从 `WorkerIdRepository` 中删除（由父接口提供），无重复定义

### R1-05 + R1-06: 配置项完整传递链

- `SnowflakeProperties.ClockBackwards` 新增 `maxStartupWaitMs`（默认 5000ms），带 `@NotNull` + `@Min(0)` 校验
- 传递链：`SnowflakeProperties` → `SnowflakeDomainServiceConfig` → `SnowflakeDomainService` → `SnowflakeWorker`（6 参数构造函数）
- `clockBackwardsWaitThresholdMs` 同样完整传递，不再使用默认值

### R1-08: EPHEMERAL 节点处理

- `releaseWorkerId()` 简化为日志输出，不再尝试 setData
- 注释说明 EPHEMERAL 节点会话结束自动删除

### R1-10: offset 信息恢复

- `ApiResponse` 新增 `Map<String, Object> extra` 字段（`@JsonInclude(NON_NULL)` 控制序列化）
- `withExtra()` 链式方法设计合理，不影响其他 handler
- `ClockBackwardsException` 的 offset 通过 `extra.offset` 返回，客户端可直接读取

---

## 三、新发现问题

无。本轮修复无新问题。

---

## 四、总结

第 1 轮 10 项问题中 9 项已全部修复，1 项（R1-09 全写锁性能）为已知权衡保留。代码质量优化分支可以合并。
