# ID Generator Docker Environment

本目录包含运行ID Generator所需的Docker基础设施配置。

## 基础设施组件

- **PostgreSQL 16**: 用于存储Segment模式的ID分配信息
- **ZooKeeper 3.8**: 用于Snowflake模式的分布式Worker ID管理
- **ID Generator Server**: ID生成服务（可选，用于本地测试）

## 快速开始

### 方式一：使用便捷脚本（推荐）

#### Windows系统

```cmd
# 仅启动基础设施（PostgreSQL + ZooKeeper）
cd docker\scripts
start-infra.bat

# 启动完整环境（包括服务）
start-full.bat

# 检查服务健康状态
check-health.bat

# 停止服务
stop-infra.bat
```

#### Linux/Mac系统

```bash
# 仅启动基础设施（PostgreSQL + ZooKeeper）
cd docker/scripts
chmod +x *.sh
./start-infra.sh

# 启动完整环境（包括服务）
./start-full.sh

# 启动多实例测试环境
./start-multi.sh

# 检查服务健康状态
./check-health.sh

# 停止服务
./stop-infra.sh

# 重置所有数据
./reset-data.sh
```

### 方式二：直接使用Docker Compose

#### 1. 仅启动基础设施（推荐用于开发）

```bash
cd docker
docker-compose up -d postgres zookeeper
```

这将启动PostgreSQL和ZooKeeper，你可以在IDE中直接运行ID Generator Server进行开发调试。

#### 2. 启动完整环境（包括服务）

```bash
cd docker
docker-compose --profile full up -d
```

这将启动所有服务，包括ID Generator Server。

#### 3. 停止服务

```bash
cd docker
docker-compose down
```

#### 4. 停止服务并清理数据

```bash
cd docker
docker-compose down -v
```

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL | 5434 | 数据库端口 |
| ZooKeeper | 2181 | 客户端连接端口 |
| ZooKeeper Admin | 8080 | 管理端口 |
| ID Generator Server | 8010 | HTTP API端口 |

## 环境变量配置

复制 `.env.example` 为 `.env` 并根据需要修改：

```bash
cp .env.example .env
```

主要配置项：

- `POSTGRES_*`: PostgreSQL数据库配置
- `DATACENTER_ID`: 数据中心ID (0-31)
- `WORKER_ID`: Worker ID (-1表示从ZooKeeper获取)
- `ENABLE_ZOOKEEPER`: 是否启用ZooKeeper
- `SNOWFLAKE_EPOCH`: Snowflake算法的起始时间戳

## 数据持久化

以下数据会持久化到Docker volumes：

- `postgres_data`: PostgreSQL数据
- `zookeeper_data`: ZooKeeper数据
- `zookeeper_datalog`: ZooKeeper事务日志
- `zookeeper_logs`: ZooKeeper日志

## 健康检查

所有服务都配置了健康检查：

```bash
# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f postgres
docker-compose logs -f zookeeper
docker-compose logs -f id-generator-server
```

## 数据库初始化

PostgreSQL容器启动时会自动执行 `../sql/schema.sql` 脚本，创建必要的表和初始数据。

## ZooKeeper管理

### 使用ZooKeeper CLI

```bash
# 进入ZooKeeper容器
docker exec -it id-generator-zookeeper zkCli.sh

# 查看Worker ID节点
ls /leaf/id-generator/snowflake

# 查看特定Worker节点信息
get /leaf/id-generator/snowflake/worker-0
```

### 使用ZooKeeper Admin Server

访问 http://localhost:8080/commands 查看ZooKeeper状态。

## 故障排查

### PostgreSQL连接失败

```bash
# 检查PostgreSQL日志
docker-compose logs postgres

# 测试数据库连接
docker exec -it id-generator-postgres psql -U id_gen_user -d id_generator
```

### ZooKeeper连接失败

```bash
# 检查ZooKeeper日志
docker-compose logs zookeeper

# 测试ZooKeeper连接
echo ruok | nc localhost 2181
```

### 服务启动失败

```bash
# 查看服务日志
docker-compose logs id-generator-server

# 重启服务
docker-compose restart id-generator-server
```

## 多实例部署

如需启动多个ID Generator实例进行测试：

```bash
# 启动第一个实例
docker-compose --profile full up -d

# 启动第二个实例（不同端口）
docker-compose -f docker-compose.yml -f docker-compose.multi.yml up -d id-generator-server-2
```

## 生产环境注意事项

1. **修改默认密码**: 更改PostgreSQL和其他服务的默认密码
2. **资源限制**: 为容器配置适当的CPU和内存限制
3. **网络隔离**: 使用适当的网络策略隔离服务
4. **监控告警**: 配置监控和告警系统
5. **备份策略**: 定期备份PostgreSQL和ZooKeeper数据
6. **ZooKeeper集群**: 生产环境建议使用ZooKeeper集群（至少3节点）

## 参考资料

- [PostgreSQL Docker Hub](https://hub.docker.com/_/postgres)
- [ZooKeeper Docker Hub](https://hub.docker.com/_/zookeeper)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
