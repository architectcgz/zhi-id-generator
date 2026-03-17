# 2026-03-11 Remaining Todos

## 已完成

- Snowflake Worker ID 分配已统一为 PostgreSQL 租约模式，ZooKeeper 运行路径已移除。
- Segment 与 Snowflake 的查询接口已统一为明确 DTO，并通过 `ApiResponse.data` 暴露。
- Segment / Snowflake 主要失败路径已补齐明确错误码，`IllegalStateException` 已收敛为内部错误语义。
- REST 查询接口返回结构测试已补齐，根模块 `mvn test` 已通过。

## 剩余收尾项

### 1. 文档与运维脚本同步收口

- 检查 README、部署文档、脚本示例是否全部按 `ApiResponse.data` 读取查询接口字段。
- 特别关注健康检查、`/api/v1/id/snowflake/info`、`/api/v1/id/snowflake/parse/{id}`、`/api/v1/id/cache/{bizTag}` 的示例是否仍引用旧结构。

### 2. 多实例脚本做一次人工冒烟

- `deploy/scale.ps1`、`deploy/test-api.ps1` 已改为读取 `data.*` 字段。
- 后续在 Windows / PowerShell 环境下实际跑一遍，确认输出与当前接口一致。

## 验收建议

- 文档中的接口示例与当前测试覆盖的响应结构一致。
- 运维脚本不再依赖顶层 `status` / `snowflake` / `segment` 字段。
