# ID Generator 快速开始指南

## 三种部署方式对比

### 1. 单实例部署 ⭐⭐⭐
**适用场景**: 开发、测试、小规模应用

```powershell
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
# 启动 3 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3

# 扩容到 10 个
docker compose -f docker-compose.scale.yml up -d --scale id-generator=10 --no-recreate

# 缩容到 5 个
docker compose -f docker-compose.scale.yml up -d --scale id-generator=5 --no-recreate
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
cd id-generator/deploy
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3
```

#### 第二步：查看状态

```bash
docker compose -f docker-compose.scale.yml ps
```

#### 第三步：测试服务

```bash
# 测试实例 1
curl http://localhost:8011/api/v1/id/health

# 测试实例 2
curl http://localhost:8012/api/v1/id/health

# 测试实例 3
curl http://localhost:8013/api/v1/id/health
```

#### 第四步：根据需要扩缩容

```bash
# 流量增加时扩容到 10 个
docker compose -f docker-compose.scale.yml up -d --scale id-generator=10 --no-recreate

# 流量减少时缩容到 3 个
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
# 启动 3 个实例
docker compose -f docker-compose.scale.yml up -d --scale id-generator=3

# 扩容到 10 个（不重启现有实例）
docker compose -f docker-compose.scale.yml up -d --scale id-generator=10 --no-recreate

# 缩容到 5 个
docker compose -f docker-compose.scale.yml up -d --scale id-generator=5 --no-recreate
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
# 健康检查
curl http://localhost:8011/api/v1/id/health

# 生成 ID
curl http://localhost:8011/api/v1/id/snowflake
```

**完整命令参考**: 查看 [COMMANDS.md](COMMANDS.md)

---

## 访问服务

### 单个实例

```powershell
# 实例 1
Invoke-RestMethod http://localhost:8011/api/v1/id/snowflake

# 实例 2
Invoke-RestMethod http://localhost:8012/api/v1/id/snowflake

# 实例 3
Invoke-RestMethod http://localhost:8013/api/v1/id/snowflake
```

### 使用负载均衡

配置 Nginx 或在应用层轮询多个实例。

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
