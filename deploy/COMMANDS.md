# ID Generator Docker 命令速查

## 基础命令

### 启动服务（指定实例数量）

```bash
# 启动 1 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=1

# 启动 3 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3

# 启动 5 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=5

# 启动 10 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=10
```

### 动态扩缩容（不重启现有实例）

```bash
# 扩容到 5 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=5 --no-recreate

# 扩容到 10 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=10 --no-recreate

# 缩容到 3 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3 --no-recreate

# 缩容到 1 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=1 --no-recreate
```

### 查看状态

```bash
# 查看所有服务状态
docker compose -f docker-compose.scale.yml ps

# 查看 ID Generator 实例
docker ps --filter "name=id-generator"

# 查看详细信息
docker compose -f docker-compose.scale.yml ps --format json
```

### 查看日志

```bash
# 查看所有 ID Generator 日志
docker compose -f docker-compose.scale.yml logs id-generator

# 实时跟踪日志
docker compose -f docker-compose.scale.yml logs -f id-generator

# 查看最近 100 行日志
docker compose -f docker-compose.scale.yml logs --tail 100 id-generator

# 查看特定实例日志
docker logs deploy-id-generator-1
docker logs deploy-id-generator-2
```

### 停止和清理

```bash
# 停止所有服务
docker compose -f docker-compose.scale.yml down

# 停止并删除数据卷
docker compose -f docker-compose.scale.yml down -v

# 只停止 ID Generator 实例（保留基础设施）
docker compose -f docker-compose.scale.yml stop id-generator

# 重启所有服务
docker compose -f docker-compose.scale.yml restart

# 重启 ID Generator 实例
docker compose -f docker-compose.scale.yml restart id-generator
```

## 测试命令

### 健康检查

```bash
# 实例 1
curl http://localhost:8011/api/v1/id/health

# 实例 2
curl http://localhost:8012/api/v1/id/health

# 实例 3
curl http://localhost:8013/api/v1/id/health
```

### 生成 ID

```bash
# 从实例 1 生成 ID
curl http://localhost:8011/api/v1/id/snowflake

# 从实例 2 生成 ID
curl http://localhost:8012/api/v1/id/snowflake

# 批量生成 10 个 ID
curl "http://localhost:8011/api/v1/id/snowflake/batch?count=10"
```

### PowerShell 测试

```powershell
# 健康检查
Invoke-RestMethod http://localhost:8011/api/v1/id/health

# 生成 ID
Invoke-RestMethod http://localhost:8011/api/v1/id/snowflake

# 查看 Worker ID
(Invoke-RestMethod http://localhost:8011/api/v1/id/health).data.snowflake.workerId
(Invoke-RestMethod http://localhost:8012/api/v1/id/health).data.snowflake.workerId
(Invoke-RestMethod http://localhost:8013/api/v1/id/health).data.snowflake.workerId
```

## 高级命令

### 查看端口映射

```bash
# 查看所有实例的端口
docker ps --filter "name=id-generator" --format "table {{.Names}}\t{{.Ports}}"

# 查看特定实例端口
docker port deploy-id-generator-1
docker port deploy-id-generator-2
```

### 进入容器

```bash
# 进入实例 1
docker exec -it deploy-id-generator-1 sh

# 进入共享 PostgreSQL
docker exec -it shared-postgres psql -U postgres -d id_generator
```

### 查看资源使用

```bash
# 查看所有容器资源使用
docker stats

# 只查看 ID Generator 实例
docker stats $(docker ps --filter "name=id-generator" -q)
```

## 常见操作流程

### 首次部署

```bash
cd /home/azhi/workspace/projects/infra
docker compose up -d

cd id-generator/deploy
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3
docker compose -f docker-compose.scale.yml ps
```

### 扩容（流量增加）

```bash
# 从 3 个扩容到 10 个
docker compose -f docker-compose.scale.yml up -d --scale id-generator=10 --no-recreate
```

### 缩容（流量减少）

```bash
# 从 10 个缩容到 5 个
docker compose -f docker-compose.scale.yml up -d --scale id-generator=5 --no-recreate
```

### 重启服务

```bash
# 重启所有服务
docker compose -f docker-compose.scale.yml restart

# 只重启 ID Generator 实例
docker compose -f docker-compose.scale.yml restart id-generator
```

### 更新镜像

```bash
# 重新构建镜像
docker compose -f docker-compose.scale.yml build id-generator

# 重启服务使用新镜像
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3 --force-recreate
```

### 完全清理

```bash
# 停止并删除所有容器、网络、卷
docker compose -f docker-compose.scale.yml down -v

# 删除镜像
docker rmi id-generator-server:latest
```

## 故障排查

### 查看容器状态

```bash
# 查看退出的容器
docker ps -a --filter "name=id-generator" --filter "status=exited"

# 查看容器详细信息
docker inspect deploy-id-generator-1
```

### 查看错误日志

```bash
# 查看最近的错误
docker compose -f docker-compose.scale.yml logs --tail 50 id-generator | grep -i error

# 查看特定实例的错误
docker logs deploy-id-generator-1 2>&1 | grep -i error
```

### 重启问题实例

```bash
# 重启特定实例
docker restart deploy-id-generator-1

# 删除并重新创建实例
docker rm -f deploy-id-generator-1
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3 --no-recreate
```

## 监控命令

### 实时监控

```bash
# 监控所有容器
watch -n 2 'docker compose -f docker-compose.scale.yml ps'

# 监控资源使用
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"
```

### 健康检查

```bash
# 检查所有实例健康状态
for port in {8011..8020}; do
  echo "Port $port:"
  curl -s http://localhost:$port/api/v1/id/health 2>/dev/null | grep -o '"status":"[^"]*"' || echo "Not running"
done
```

## 快速参考

| 操作 | 命令 |
|-----|------|
| 启动 3 个实例 | `docker compose -f docker-compose.scale.yml up -d --scale id-generator=3` |
| 扩容到 10 个 | `docker compose -f docker-compose.scale.yml up -d --scale id-generator=10 --no-recreate` |
| 缩容到 5 个 | `docker compose -f docker-compose.scale.yml up -d --scale id-generator=5 --no-recreate` |
| 查看状态 | `docker compose -f docker-compose.scale.yml ps` |
| 查看日志 | `docker compose -f docker-compose.scale.yml logs -f id-generator` |
| 停止服务 | `docker compose -f docker-compose.scale.yml down` |
| 测试实例 | `curl http://localhost:8011/api/v1/id/health` |

## 注意事项

1. **端口范围**: 默认支持 8011-8020（10个实例），如需更多请修改配置
2. **--no-recreate**: 扩缩容时使用此标志避免重启现有实例
3. **Worker ID**: 由 PostgreSQL 租约表自动分配，无需手动配置
4. **数据持久化**: 使用命名卷，停止服务不会丢失数据
5. **清理数据**: 使用 `down -v` 会删除所有数据，谨慎使用
