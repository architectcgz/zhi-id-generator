# id-generator 代码 Review（第 3 轮）：新增 worker_id_alloc 表结构 + 补充 LeafAllocMapper.xml

## Review 信息

| 字段 | 说明 |
|------|------|
| 轮次 | 第 3 轮（新功能开发审查） |
| 审查范围 | 31a033e..cce5cd4，2 个 commit，2 个文件，+69 / -12 行 |
| 变更概述 | 1) 在 schema.sql 中追加 worker_id_alloc 表 DDL 及预填数据；2) 补充 LeafAllocMapper.xml 映射文件（修复 todo 0.5） |
| 审查基准 | docs/architecture.md、docs/todo.md（任务 0.5 + 1.1） |
| 审查日期 | 2026-02-28 |

## 任务完成度对照

| TODO 编号 | 描述 | 状态 | 说明 |
|-----------|------|------|------|
| 0.5 | MyBatis XML 映射文件缺失 | 已修复 | LeafAllocMapper.xml 已补充，namespace、方法签名、resultMap 均与 Mapper 接口一致 |
| 1.1 | 新建 worker_id_alloc 表 | 已完成 | DDL + 预填数据已追加到 schema.sql，字段与 todo 描述一致 |

## 问题清单

### 🟡 中优先级

#### [M1] worker_id_alloc.status 应使用 CHECK 约束限制合法值

- **文件**：`sql/schema.sql:39`
- **问题描述**：`status` 字段定义为 `VARCHAR(16) NOT NULL DEFAULT 'released'`，但没有 CHECK 约束。任何字符串都可以写入，无法在数据库层面保证只有 `active` / `released` 两种合法状态
- **影响范围/风险**：应用层 bug 或手动操作可能写入非法状态值（如拼写错误 `actve`），导致 `SELECT ... WHERE status = 'released'` 查不到该行，Worker ID 泄漏无法回收
- **修正建议**：
```sql
status VARCHAR(16) NOT NULL DEFAULT 'released'
    CHECK (status IN ('active', 'released'))
```

#### [M2] worker_id_alloc 缺少 lease_time 索引，过期回收查询将全表扫描

- **文件**：`sql/schema.sql:33-40`
- **问题描述**：todo 1.2 描述的过期回收逻辑需要查询 `WHERE status = 'active' AND lease_time < NOW() - interval '10 minutes'`。当前表没有针对 `(status, lease_time)` 的索引
- **影响范围/风险**：虽然表只有 32 行，全表扫描性能影响可忽略，但作为基础设施表，加索引是良好实践，且能避免后续扩展 Worker ID 范围时的性能隐患
- **修正建议**：
```sql
CREATE INDEX IF NOT EXISTS idx_worker_id_alloc_status_lease
    ON worker_id_alloc(status, lease_time);
```

#### [M3] worker_id_alloc.worker_id 缺少 CHECK 约束，无法防止越界插入

- **文件**：`sql/schema.sql:34`
- **问题描述**：`worker_id INTEGER PRIMARY KEY` 没有范围约束。虽然预填数据是 0-31，但手动 INSERT 可以写入 32、-1 等越界值，而 Snowflake 算法的 worker 位只有 5 bit（0-31）
- **影响范围/风险**：越界的 worker_id 被分配后，生成的 Snowflake ID 会因位溢出导致 datacenter 位被污染，产生不可预期的 ID 冲突
- **修正建议**：
```sql
worker_id INTEGER PRIMARY KEY CHECK (worker_id >= 0 AND worker_id <= 31)
```

### 🟢 低优先级

#### [L1] leaf_alloc 预填数据的 INSERT 缺少 update_time 字段

- **文件**：`sql/schema.sql:18-23`（已有代码，非本次变更，但与本次 XML 修复相关）
- **问题描述**：`leaf_alloc` 的预填 INSERT 语句没有显式指定 `update_time` 列，依赖 DDL 中的 `DEFAULT CURRENT_TIMESTAMP`。而 `worker_id_alloc` 的预填数据则显式传了 `CURRENT_TIMESTAMP`。两处风格不一致
- **影响范围/风险**：功能无影响，仅风格一致性问题
- **修正建议**：统一风格即可，建议 `leaf_alloc` 的 INSERT 也显式传 `CURRENT_TIMESTAMP`，或两处都依赖 DEFAULT

#### [L2] schema.sql 中英文注释混用

- **文件**：`sql/schema.sql` 全文
- **问题描述**：`leaf_alloc` 部分的 COMMENT ON 使用英文（如 `'Business tag identifier'`），而 `worker_id_alloc` 部分使用中文（如 `'Worker ID，取值 0-31，对应 Snowflake 算法的 worker 位'`）。行内注释也是一半英文一半中文
- **影响范围/风险**：无功能影响，仅可读性和一致性
- **修正建议**：统一为中文（与项目整体注释规范一致），可在后续 commit 中一并调整

## LeafAllocMapper.xml 审查确认

本次 commit cce5cd4 补充的 XML 映射文件经逐项核对，确认以下方面均无问题：

| 检查项 | 结果 |
|--------|------|
| namespace 与 Mapper 接口全限定名一致 | 通过 |
| resultMap 字段与 LeafAlloc 实体属性完全匹配 | 通过 |
| type="LeafAlloc" 依赖 type-aliases-package 配置（application.yml:22），路径正确 | 通过 |
| findAll / findByBizTag / updateMaxIdWithLock / insert 四个方法签名与接口一致 | 通过 |
| updateMaxIdWithLock 乐观锁实现正确：`SET version = version + 1 WHERE version = #{version}` | 通过 |
| insert 语句 version 初始值硬编码为 0，与 DDL DEFAULT 一致 | 通过 |
| SQL 语法兼容 PostgreSQL 16（CURRENT_TIMESTAMP、标准 DML） | 通过 |

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 0 |
| 🟡 中 | 3 |
| 🟢 低 | 2 |
| 合计 | 5 |

## 总体评价

本轮两个 commit 质量良好，没有高优先级问题。

todo 0.5（MyBatis XML 缺失）已完整修复，四个 SQL 语句与 Mapper 接口签名、实体字段、乐观锁语义均一一对应，可直接投入使用。

todo 1.1（worker_id_alloc 表结构）设计合理，字段选择与 todo 描述完全一致，`ON CONFLICT DO NOTHING` 保证了脚本幂等性。主要改进点集中在数据库约束的防御性加固（M1 status CHECK、M3 worker_id 范围 CHECK），建议在下一个 commit 中一并补上，成本很低但能从数据库层面杜绝非法数据。

M2（索引）优先级可以放低，32 行数据全表扫描无实际性能问题，等后续 DbWorkerIdRepository 实现时再根据实际查询模式决定是否添加。
