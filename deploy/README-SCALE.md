# ID Generator 灵活扩缩容部署

这是最灵活的部署方式，允许你随时增加或减少 ID Generator 实例数量。

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
# 启动 3 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3

# 扩容到 5 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=5 --no-recreate

# 缩容到 2 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=2 --no-recreate

# 查看状态
docker compose -f docker-compose.scale.yml ps

# 停止所有服务
docker compose -f docker-compose.scale.yml down
```

## 工作原理

### 架构

```
┌─────────────────────────────────────┐
│      ZooKeeper (单实例)              │
│   自动分配 Worker ID: 0, 1, 2...    │
└─────────────────────────────────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
┌───▼───┐   ┌───▼───┐   ┌───▼───┐
│ ID-1  │   │ ID-2  │   │ ID-3  │  ... 可扩展到 1024 个
│ WID:0 │   │ WID:1 │   │ WID:2 │
│:8011  │   │:8012  │   │:8013  │
└───────┘   └───────┘   └───────┘
```

### 关键特性

1. **动态端口映射**: 使用端口范围 `8011-8020:8011`
   - 第 1 个实例: `localhost:8011`
   - 第 2 个实例: `localhost:8012`
   - 第 3 个实例: `localhost:8013`
   - 以此类推...

2. **自动 Worker ID 分配**: ZooKeeper 自动为每个实例分配唯一 ID

3. **独立数据卷**: 每个实例使用匿名卷，互不干扰

4. **零停机扩缩容**: 使用 `--no-recreate` 标志，不影响现有实例

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
| 1 | id-generator-deploy-id-generator-1 | 8011 | 8011 |
| 2 | id-generator-deploy-id-generator-2 | 8012 | 8011 |
| 3 | id-generator-deploy-id-generator-3 | 8013 | 8011 |
| ... | ... | ... | ... |
| N | id-generator-deploy-id-generator-N | 8010+N | 8011 |

## 负载均衡

### 使用 Nginx

创建 `nginx.conf`:

```nginx
upstream id_generator_cluster {
    least_conn;  # 最少连接数负载均衡
    
    server localhost:8011 max_fails=3 fail_timeout=30s;
    server localhost:8012 max_fails=3 fail_timeout=30s;
    server localhost:8013 max_fails=3 fail_timeout=30s;
    # 根据实例数量添加更多...
}

server {
    listen 8010;
    
    location / {
        proxy_pass http://id_generator_cluster;
        proxy_next_upstream error timeout http_500 http_502 http_503;
        proxy_connect_timeout 5s;
        proxy_send_timeout 10s;
        proxy_read_timeout 10s;
    }
}
```

### 使用应用层负载均衡

在应用代码中轮询多个实例：

```java
List<String> endpoints = Arrays.asList(
    "http://localhost:8011",
    "http://localhost:8012",
    "http://localhost:8013"
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
id-generator-postgres                   postgres:16-alpine          Up (healthy)
id-generator-zookeeper                  zookeeper:3.8               Up (healthy)
id-generator-deploy-id-generator-1      id-generator-server:latest  Up (healthy)
id-generator-deploy-id-generator-2      id-generator-server:latest  Up (healthy)
id-generator-deploy-id-generator-3      id-generator-server:latest  Up (healthy)

ID Generator Instances:
  Running: 3 instance(s)

  • id-generator-deploy-id-generator-1
    Port: 8011
    Worker ID: 0
    Status: UP

  • id-generator-deploy-id-generator-2
    Port: 8012
    Worker ID: 1
    Status: UP

  • id-generator-deploy-id-generator-3
    Port: 8013
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

✓ id-generator-deploy-id-generator-1 (Port: 8011, Worker ID: 0)
  Generated ID: 139370018650464256

✓ id-generator-deploy-id-generator-2 (Port: 8012, Worker ID: 1)
  Generated ID: 139370018650464257

✓ id-generator-deploy-id-generator-3 (Port: 8013, Worker ID: 2)
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
      cpus: '1.0'
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

默认端口范围是 `8011-8020`（10个端口）。如需更多实例，修改 `docker-compose.scale.yml`:

```yaml
ports:
  - "8011-8050:8011"  # 支持 40 个实例
```

## 最佳实践

1. **生产环境**: 至少部署 3 个实例保证高可用
2. **开发环境**: 1-2 个实例即可
3. **压测环境**: 根据需要动态调整
4. **监控**: 使用 Prometheus + Grafana 监控所有实例
5. **日志**: 集中收集日志到 ELK 或类似系统
6. **备份**: 定期备份 PostgreSQL 和 ZooKeeper 数据

## 限制

- **最大实例数**: 受端口范围限制（默认 10 个，可调整）
- **Worker ID 上限**: 1024 个（Snowflake 算法限制）
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
