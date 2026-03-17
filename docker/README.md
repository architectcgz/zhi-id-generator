# ID Generator Docker Environment

本目录现在同时承载 `id-generator` 的两类 Docker 编排：

- 共享基础设施部署：所有正式部署相关文件集中在当前目录
- 本地开发验证：保留原有本地 PostgreSQL + 服务端编排

共享部署所需的 Nginx、DB 初始化脚本、SQL 和 Dockerfile 都已经收敛到 `docker/` 下，不再依赖 `deploy/` 和根目录的零散基础设施文件。

## 目录结构

```text
id-generator/docker/
├── Dockerfile
├── docker-compose.shared.yml
├── docker-compose.scale.yml
├── docker-compose.yml
├── docker-compose.multi.yml
├── init-id-generator-db.sh
├── nginx/
│   └── id-generator-lb.conf
├── postgres/
├── schema/
│   └── schema.sql
└── scripts/
```

## 共享基础设施部署

当前推荐的共享部署入口都在本目录：

- 单实例：`docker-compose.shared.yml`
- 扩缩容：`docker-compose.scale.yml`

依赖与 `file-service` 保持一致：

- PostgreSQL: `shared-postgres:5432`
- Redis: `shared-redis:6379`（当前服务未使用，但共享网络保持一致）
- Docker Network: `shared-infra`

### 单实例

```bash
# 先在仓库根目录构建可执行服务端 JAR
cd /home/azhi/workspace/projects/id-generator
mvn -q -pl id-generator-server -am -DskipTests package

# 启动共享基础设施
cd /home/azhi/workspace/projects/infra
docker compose up -d

# 启动单实例
cd /home/azhi/workspace/projects/id-generator/docker
cp .env.shared.example .env
docker compose -f docker-compose.shared.yml up -d
```

### 扩缩容

```bash
# 先在仓库根目录构建可执行服务端 JAR
cd /home/azhi/workspace/projects/id-generator
mvn -q -pl id-generator-server -am -DskipTests package

# 启动共享基础设施
cd /home/azhi/workspace/projects/infra
docker compose up -d

# 启动 2 个实例，8011 为 Nginx 统一入口
cd /home/azhi/workspace/projects/id-generator/docker
cp .env.shared.example .env
docker compose -f docker-compose.scale.yml up -d --scale id-generator=2
```

扩到 3 个及以上实例时，需要同步修改 [nginx/id-generator-lb.conf](/home/azhi/workspace/projects/id-generator/docker/nginx/id-generator-lb.conf)。

## 本地开发编排

本地开发环境仍然保留在当前目录，适合不依赖共享 `infra` 的单机验证。

### 组件

- `id-generator-postgres`: 存储 `leaf_alloc` 和 `worker_id_alloc`
- `id-generator-server`: 可选，方便直接本地验证 API

## 快速开始

### Linux / Mac

```bash
# 先在仓库根目录构建可执行服务端 JAR
cd ..
mvn -q -pl id-generator-server -am -DskipTests package

cd docker/scripts
chmod +x *.sh

# 仅启动数据库
./start-infra.sh

# 启动完整环境
./start-full.sh

# 启动多实例测试
./start-multi.sh

# 检查状态
./check-health.sh

# 停止环境
./stop-infra.sh
```

### Windows

```cmd
cd docker\scripts

start-infra.bat
start-full.bat
check-health.bat
stop-infra.bat
```

## 直接使用 Docker Compose

```bash
# 先在仓库根目录构建可执行服务端 JAR
cd ..
mvn -q -pl id-generator-server -am -DskipTests package

cd docker

# 仅启动数据库
docker-compose up -d id-generator-postgres

# 启动完整环境
docker-compose --profile full up -d

# 停止环境
docker-compose down
```

## 端口

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL | 5435 | 数据库 |
| ID Generator Server | 8011 | HTTP API |

## 关键环境变量

复制 `.env.example` 为 `.env`：

```bash
cp .env.example .env
```

共享基础设施部署建议复制 `.env.shared.example` 为 `.env`：

```bash
cp .env.shared.example .env
```

常用配置：

- `POSTGRES_*`: PostgreSQL 配置
- `DB_*`: 共享 PostgreSQL 配置
- `DATACENTER_ID`: Snowflake 数据中心 ID
- `WORKER_ID`: 固定 Worker ID，`-1` 表示自动从数据库抢占
- `SNOWFLAKE_EPOCH`: Snowflake 纪元
- `CLOCK_BACKWARDS_MAX_WAIT`: 小回拨等待阈值
- `CLOCK_BACKWARDS_ALERT_THRESHOLD`: 回拨告警阈值

## 数据持久化

- `postgres_data`: PostgreSQL 数据

## 常用排查命令

```bash
# 查看数据库日志
docker-compose logs -f id-generator-postgres

# 查看服务日志
docker-compose logs -f id-generator-server

# 进入数据库容器
docker exec -it id-generator-postgres psql -U id_gen_user -d id_generator
```

## 多实例说明

多实例测试依赖 `worker_id_alloc` 表自动分配 Worker ID，不需要额外协调组件。

如果需要更多实例：

- 调整端口映射范围
- 确保 `worker_id_alloc` 表和 `datacenter-id` 规划满足容量要求

## 生产建议

1. 修改默认数据库密码
2. 为 PostgreSQL 做备份与监控
3. 结合实例数量规划 `datacenter-id` / `worker-id` 容量
4. 在部署前初始化好 `worker_id_alloc` 和 `leaf_alloc`
