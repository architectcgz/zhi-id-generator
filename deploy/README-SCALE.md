# ID Generator 灵活扩缩容部署

这是最灵活的部署方式，允许你随时增加或减少 ID Generator 实例数量。

当前编排复用和 `file-service` 相同的共享基础设施：

- PostgreSQL: `shared-postgres:5432`
- Redis: `shared-redis:6379`（当前服务未使用，但共享网络保持一致）
- Docker Network: `shared-infra`

当前 `docker-compose.scale.yml` 已内置一个简单的 Nginx 负载均衡入口：

- 统一入口: `http://localhost:8011`
- 实例直连调试端口: 从 `8012` 开始顺延

当前 `docker/nginx/id-generator-lb.conf` 为固定双实例配置，默认转发到
`deploy-id-generator-1:8011` 和 `deploy-id-generator-2:8011`。
如果把 `id-generator` 扩到 3 个及以上实例，需要同步修改 Nginx upstream；
否则请直接使用实例直连端口或应用层负载均衡。

## 快速开始

### 使用脚本（推荐）

```powershell
# 启动 2 个实例（默认）
.\scale.ps1 -Action up

# 启动 5 个实例
.\scale.ps1 -Action up -Count 5

# 查看当前状态
.\scale.ps1 -Action status

# 扩容到 10 个实例
.\scale.ps1 -Action scale -Count 10

# 缩容到 3 个实例
.\scale.ps1 -Action scale -Count 3

# 测试所有实例
.\scale.ps1 -Action test

# 查看日志
.\scale.ps1 -Action logs

# 停止所有服务
.\scale.ps1 -Action down
```

### 使用 Docker Compose 命令

```bash
# 先启动共享基础设施
cd /home/azhi/workspace/projects/infra
docker compose up -d

# 启动 2 个实例（8011 为 Nginx 入口，实例直连端口从 8012 开始）
cd /home/azhi/workspace/projects/id-generator/deploy
docker compose -f docker-compose.scale.yml up -d --scale id-generator=2

# 如果需要扩到 3 个及以上实例，先修改 docker/nginx/id-generator-lb.conf
# 再执行 scale；否则 Nginx 不会自动把新增实例纳入转发
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3 --no-recreate

# 查看状态
docker compose -f docker-compose.scale.yml ps

# 停止所有服务
docker compose -f docker-compose.scale.yml down
```

## 工作原理

### 架构

```
┌─────────────────────────────────────┐
│    PostgreSQL worker_id_alloc       │
│  自动抢占 Worker ID: 0, 1, 2...     │
└─────────────────────────────────────┘
                 │
           ┌─────▼─────┐
           │ Nginx:8011│
           └─────┬─────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
┌───▼───┐   ┌───▼───┐   ┌───▼───┐
│ ID-1  │   │ ID-2  │   │ ID-3  │  ... 最多 32 个活跃实例
│ WID:0 │   │ WID:1 │   │ WID:2 │
│:8012  │   │:8013  │   │:8014  │
└───────┘   └───────┘   └───────┘
```

### 关键特性

1. **Nginx 统一入口**: `localhost:8011`
   - 业务侧优先访问 `http://localhost:8011`
   - 当前 upstream 为固定双实例 round-robin，目标是稳定支撑双实例部署
   - 如果实例数发生变化，需要同步更新 `docker/nginx/id-generator-lb.conf`

2. **实例直连端口映射**: 使用端口范围 `8012-8021:8011`
   - 第 1 个实例: `localhost:8012`
   - 第 2 个实例: `localhost:8013`
   - 第 3 个实例: `localhost:8014`
   - 以此类推...

3. **自动 Worker ID 分配**: PostgreSQL 租约表自动为每个实例分配唯一 ID

4. **独立数据卷**: 每个实例使用匿名卷，互不干扰

5. **零停机扩缩容**: 使用 `--no-recreate` 标志，不影响现有实例

## 使用场景

### 场景 1: 开发测试

```powershell
# 启动 1 个实例进行开发
.\scale.ps1 -Action up -Count 1

# 测试多实例场景
.\scale.ps1 -Action scale -Count 3
```

### 场景 2: 生产环境

```powershell
# 启动 3 个实例保证高可用
.\scale.ps1 -Action up -Count 3

# 流量高峰期扩容到 10 个
.\scale.ps1 -Action scale -Count 10

# 流量低谷期缩容到 5 个
.\scale.ps1 -Action scale -Count 5
```

### 场景 3: 压力测试

```powershell
# 启动 20 个实例进行压测
.\scale.ps1 -Action up -Count 20

# 测试所有实例
.\scale.ps1 -Action test
```

## 端口分配

实例会自动分配端口：

| 实例编号 | 容器名称 | 宿主机端口 | 容器端口 |
|---------|---------|-----------|---------|
| LB | id-generator-nginx | 8011 | 8011 |
| 1 | id-generator-deploy-id-generator-1 | 8012 | 8011 |
| 2 | id-generator-deploy-id-generator-2 | 8013 | 8011 |
| 3 | id-generator-deploy-id-generator-3 | 8014 | 8011 |
| ... | ... | ... | ... |
| N | id-generator-deploy-id-generator-N | 8011+N | 8011 |

说明：实例直连端口从 `8012-8021` 范围内动态分配，重建后不保证一定连续。实际映射请以 `docker compose -f docker-compose.scale.yml ps` 为准。

## 负载均衡

### 使用内置 Nginx

`docker-compose.scale.yml` 已经内置 Nginx，默认监听 `8011`，配置文件位于：

`docker/nginx/id-generator-lb.conf`

常用访问方式：

- 统一入口: `http://localhost:8011/api/v1/id/snowflake`
- 实例直连示例: `http://localhost:8012/api/v1/id/snowflake`
- 具体端口请先执行 `docker compose -f docker-compose.scale.yml ps`
- 默认只纳入两个后端实例；如果扩到 3 个及以上实例，需手工补充 upstream

### 使用应用层负载均衡

在应用代码中轮询多个实例：

```java
List<String> endpoints = Arrays.asList(
    "http://localhost:8012",
    "http://localhost:8013",
    "http://localhost:8014"
);

// 轮询
int index = counter.getAndIncrement() % endpoints.size();
String endpoint = endpoints.get(index);
```

## 监控和管理

### 查看所有实例状态

```powershell
.\scale.ps1 -Action status
```

输出示例：
```
Current Service Status:

NAME                                    IMAGE                       STATUS
id-generator-db-init                    postgres:16-alpine          Exited (0)
id-generator-nginx                      nginx:1.27-alpine           Up (healthy)
id-generator-deploy-id-generator-1      id-generator-server:latest  Up (healthy)
id-generator-deploy-id-generator-2      id-generator-server:latest  Up (healthy)
id-generator-deploy-id-generator-3      id-generator-server:latest  Up (healthy)

ID Generator Instances:
  Running: 3 instance(s)

  • id-generator-deploy-id-generator-1
    Port: 8012
    Worker ID: 0
    Status: UP

  • id-generator-deploy-id-generator-2
    Port: 8013
    Worker ID: 1
    Status: UP

  • id-generator-deploy-id-generator-3
    Port: 8014
    Worker ID: 2
    Status: UP
```

### 测试所有实例

```powershell
.\scale.ps1 -Action test
```

输出示例:
```
Testing all ID Generator instances...

✓ id-generator-deploy-id-generator-1 (Port: 8012, Worker ID: 0)
  Generated ID: 139370018650464256

✓ id-generator-deploy-id-generator-2 (Port: 8013, Worker ID: 1)
  Generated ID: 139370018650464257

✓ id-generator-deploy-id-generator-3 (Port: 8014, Worker ID: 2)
  Generated ID: 139370018650464258

Test Summary:
  Passed: 3 / 3
  Worker IDs: All unique ✓
  Generated IDs: All unique ✓
```

### 查看实时日志

```powershell
.\scale.ps1 -Action logs
```

## 性能优化

### 资源限制

在 `docker-compose.scale.yml` 中已配置：

```yaml
deploy:
  resources:
    limits:
      memory: 768M
      cpus: '2.0'
    reservations:
      memory: 256M
      cpus: '0.25'
```

### 调整 JVM 参数

修改 `.env` 文件或环境变量：

```bash
JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

## 故障排查

### 实例无法启动

```powershell
# 查看日志
docker logs id-generator-deploy-id-generator-1

# 检查端口占用
netstat -ano | findstr "8011"
```

### Worker ID 冲突

```powershell
# 停止所有服务并清理
docker compose -f docker-compose.scale.yml down -v

# 重新启动
.\scale.ps1 -Action up -Count 3
```

### 端口不足

默认端口范围是 `8012-8021`（10个实例直连端口），`8011` 预留给 Nginx。如需更多实例，修改 `docker-compose.scale.yml`:

```yaml
ports:
  - "8012-8051:8011"  # 支持 40 个实例直连端口
```

## 最佳实践

1. **生产环境**: 至少部署 3 个实例保证高可用
2. **开发环境**: 1-2 个实例即可
3. **压测环境**: 根据需要动态调整
4. **监控**: 使用 Prometheus + Grafana 监控所有实例
5. **日志**: 集中收集日志到 ELK 或类似系统
6. **备份**: 定期备份 PostgreSQL 数据

## 限制

- **最大实例数**: 受端口范围限制（默认 10 个，可调整）
- **Worker ID 上限**: 32 个活跃实例（当前实现使用 5 bit worker id）
- **网络性能**: 所有实例共享宿主机网络带宽

## 与其他部署方式对比

| 特性 | 单实例 | 多实例（固定） | 灵活扩缩容 |
|-----|-------|--------------|-----------|
| 部署复杂度 | ⭐ | ⭐⭐ | ⭐⭐⭐ |
| 扩缩容灵活性 | ❌ | ⚠️ 需修改配置 | ✅ 一条命令 |
| 资源利用率 | 低 | 中 | 高 |
| 适用场景 | 开发/测试 | 生产（固定负载） | 生产（动态负载） |
| 推荐指数 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

## 总结

灵活扩缩容部署是**最推荐**的生产部署方式，它提供了：

✅ 一条命令即可扩缩容  
✅ 零停机动态调整  
✅ 自动 Worker ID 管理  
✅ 完整的管理脚本  
✅ 适应各种负载场景  

开始使用：
```powershell
.\scale.ps1 -Action up -Count 3
```
