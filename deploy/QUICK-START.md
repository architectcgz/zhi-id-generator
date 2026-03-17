# ID Generator 快速开始指南

## 三种部署方式对比

### 1. 单实例部署 ⭐⭐⭐
**适用场景**: 开发、测试、小规模应用

```powershell
# 先在仓库根目录构建可执行服务端 JAR
cd ..
mvn -q -pl id-generator-server -am -DskipTests package

# 确保共享基础设施已启动（与 file-service 一致）
cd ..\infra
docker compose up -d

cd deploy
docker compose up -d
```

- ✅ 最简单
- ✅ 资源占用少
- ❌ 无高可用
- ❌ 性能有限

---

### 2. 灵活扩缩容部署 ⭐⭐⭐⭐⭐ **推荐**
**适用场景**: 所有生产环境

```bash
# 启动 2 个实例（8011 为 Nginx 统一入口，实例直连从 8012 开始）
# 确保共享基础设施已启动
cd /home/azhi/workspace/projects/infra
docker compose up -d

cd /home/azhi/workspace/projects/id-generator/deploy
docker compose -f docker-compose.scale.yml up -d --scale id-generator=2

# 如果扩到 3 个及以上实例，需先同步修改 docker/nginx/id-generator-lb.conf
```

- ✅ 一条命令扩缩容
- ✅ 零停机调整
- ✅ 自动管理
- ✅ 适应动态负载

---

## 推荐使用：灵活扩缩容

### 纯命令方式（无需脚本）

#### 第一步：启动服务

```bash
# 先在仓库根目录构建可执行服务端 JAR
cd ..
mvn -q -pl id-generator-server -am -DskipTests package

# 确保共享基础设施已启动（shared-postgres/shared-redis/shared-infra）
cd /home/azhi/workspace/projects/infra
docker compose up -d

cd id-generator/deploy
docker compose -f docker-compose.scale.yml up -d --scale id-generator=2
```

#### 第二步：查看状态

```bash
docker compose -f docker-compose.scale.yml ps
```

#### 第三步：测试服务

```bash
# 先查看实际端口映射（实例直连端口来自 8012-8021，重建后可能不连续）
docker compose -f docker-compose.scale.yml ps

# 测试 Nginx 统一入口
curl http://localhost:8011/api/v1/id/health

# 测试实例 1（以下端口仅为示例，请以 ps 输出为准）
curl http://localhost:8012/api/v1/id/health

# 测试实例 2
curl http://localhost:8013/api/v1/id/health
```

#### 第四步：根据需要扩缩容

```bash
# 当前内置 Nginx 默认只代理 2 个实例
# 如果要扩到 3 个及以上实例，先改 docker/nginx/id-generator-lb.conf
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3 --no-recreate
```

### 使用脚本方式（可选）

如果你想要更方便的管理，也可以使用提供的脚本：

```powershell
# 启动
.\scale.ps1 -Action up -Count 3

# 状态
.\scale.ps1 -Action status

# 扩容
.\scale.ps1 -Action scale -Count 5

# 测试
.\scale.ps1 -Action test

# 日志
.\scale.ps1 -Action logs

# 停止
.\scale.ps1 -Action down
```

---

## 常用命令速查

### 启动和扩缩容

```bash
# 启动 2 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=2

# 如需 3 个及以上实例，先改 docker/nginx/id-generator-lb.conf
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3 --no-recreate
```

### 查看和管理

```bash
# 查看状态
docker compose -f docker-compose.scale.yml ps

# 查看日志
docker compose -f docker-compose.scale.yml logs -f id-generator

# 停止服务
docker compose -f docker-compose.scale.yml down
```

### 测试服务

```bash
# 健康检查（统一入口）
curl http://localhost:8011/api/v1/id/health

# 生成 ID（统一入口）
curl http://localhost:8011/api/v1/id/snowflake
```

**完整命令参考**: 查看 [COMMANDS.md](COMMANDS.md)

---

## 访问服务

### 单个实例

```powershell
# 先执行 `docker compose -f docker-compose.scale.yml ps` 查看当前实例端口

# Nginx 统一入口
Invoke-RestMethod http://localhost:8011/api/v1/id/snowflake

# 实例 1（以下端口仅为示例）
Invoke-RestMethod http://localhost:8012/api/v1/id/snowflake

# 实例 2
Invoke-RestMethod http://localhost:8013/api/v1/id/snowflake
```

### 使用负载均衡

当前内置 Nginx 为固定双实例。若要代理更多实例，先修改
`docker/nginx/id-generator-lb.conf`，或者直接在应用层轮询多个实例。

---

## 故障排查

### 查看日志
```powershell
.\scale.ps1 -Action logs
```

### 重启服务
```powershell
.\scale.ps1 -Action down
.\scale.ps1 -Action up -Count 3
```

### 清理并重新开始
```powershell
docker compose -f docker-compose.scale.yml down -v
.\scale.ps1 -Action up -Count 3
```

---

## 下一步

- 阅读 [COMMANDS.md](COMMANDS.md) 查看完整命令参考
- 阅读 [README-SCALE.md](README-SCALE.md) 了解扩缩容详细配置
- 阅读 [README.md](README.md) 了解单实例部署
