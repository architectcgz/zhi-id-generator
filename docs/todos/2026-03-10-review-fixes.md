# 2026-03-10 Review Fixes

## 背景

本清单用于跟踪 2026-03-10 针对 `id-generator` 的 review 修复项，范围只覆盖已确认的阻断问题，不做额外重构。
同日已确认项目不再保留 ZooKeeper 运行路径，Snowflake Worker ID 分配统一收敛为 PostgreSQL 租约模式。

## 已确认问题

### 1. 数据库模式错误地允许本地缓存降级

- 现象：DB 抢占 Worker ID 失败后仍会读取本地缓存并继续发号。
- 风险：缓存中的 Worker ID 可能已被其他实例合法持有，导致 Snowflake ID 重复。
- 修复方向：DB 模式禁止使用本地缓存恢复 Worker ID；抢占失败时直接抛出 `WorkerIdUnavailableException`。

### 2. Java SDK 健康检查读取了错误的响应字段

- 现象：SDK 从顶层读取 `status`，但服务端实际返回 `ApiResponse.data.status`。
- 风险：`isHealthy()` 稳定误判为 `false`，影响探活、示例代码和接入方自检。
- 修复方向：SDK 按统一响应结构读取 `data.status`，并补回归测试。

## 执行计划

1. 移除 ZooKeeper 运行路径，统一为 PostgreSQL Worker ID 租约分配。
2. 收紧数据库模式降级策略并补单元测试。
3. 修复 SDK 健康检查并补客户端测试。
4. 同步最小必要文档，确保架构和部署说明不再描述旧实现。

## 验收

- 根模块 `mvn test` 通过。
- `DbWorkerIdRepositoryImplTest` 通过。
- `BufferedIdGeneratorClientTest` 通过。
- 数据库模式在无法获取租约时不会继续使用本地缓存发号。
- SDK `isHealthy()` 对 `ApiResponse.data.status=UP` 返回 `true`。
