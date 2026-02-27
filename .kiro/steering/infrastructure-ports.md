# ID Generator Infrastructure Port Configuration

## Overview

This document defines the port mappings and connection details for all infrastructure services in the ID Generator system. Understanding these mappings is critical for proper service configuration and avoiding port conflicts with other platform services.

---

## Port Mapping Concept

Docker port mapping format: `HOST_PORT:CONTAINER_PORT`

- **HOST_PORT**: Port accessible from your local machine (outside Docker)
- **CONTAINER_PORT**: Port used inside the Docker network

**Example**: `5436:5432` means:
- Connect to `localhost:5436` from your local machine
- Connect to `id-generator-postgres:5432` from inside Docker containers

---

## ID Generator Infrastructure Services

### PostgreSQL (ID Generator Database)

| Property | Value |
|----------|-------|
| Container Name | `id-generator-dev-postgres` |
| Image | `postgres:16-alpine` |
| Host Port | `5435` |
| Container Port | `5432` |
| Default User | `id_gen_user` |
| Default Password | `id_gen_password` |
| Default Database | `id_generator` |
| Encoding | `UTF8` |

**Port Assignment**: PostgreSQL instance #4 (5435)

**Connection Strings**:
- From Host: `jdbc:postgresql://localhost:5435/id_generator`
- From Container: `jdbc:postgresql://id-generator-postgres:5432/id_generator`

**Environment Variables**:
```yaml
DB_HOST: localhost (host) / id-generator-postgres (container)
DB_PORT: 5435 (host) / 5432 (container)
DB_USER: id_gen_user
DB_PASSWORD: id_gen_password
```

**Database Schema**:
- Tables: `leaf_alloc` (for segment mode)
- Initialization: Automatic via `sql/schema.sql`

---

### ZooKeeper (Distributed Coordination)

| Property | Value |
|----------|-------|
| Container Name | `id-generator-dev-zookeeper` |
| Image | `zookeeper:3.8` |
| Client Port | `2181` |
| Admin Port (Host) | `8888` |
| Admin Port (Container) | `8080` |
| Mode | `standalone` |

**Connection Strings**:
- From Host: `localhost:2181`
- From Container: `id-generator-zookeeper:2181`
- Admin Console: `http://localhost:8888`

**Environment Variables**:
```yaml
ZOOKEEPER_CONNECTION_STRING: localhost:2181 (host) / id-generator-zookeeper:2181 (container)
```

**ZooKeeper Configuration**:
- Tick Time: `2000ms`
- Init Limit: `10`
- Sync Limit: `5`
- Max Client Connections: `60`

**Purpose**:
- Worker ID coordination for Snowflake algorithm
- Prevents duplicate worker IDs across multiple instances
- Automatic worker ID assignment when `WORKER_ID=-1`

---

### ID Generator Server

| Property | Value |
|----------|-------|
| Container Name | `id-generator-dev-server` |
| Port | `8010` |
| Datacenter ID | `0` |
| Worker ID | `-1` (auto-assigned via ZooKeeper) |
| ZooKeeper Enabled | `true` |
| Profile | `docker` |

**Access URLs**:
- Base URL: `http://localhost:8010`
- Snowflake ID: `GET /api/v1/id/snowflake`
- Segment ID: `GET /api/v1/id/segment/{bizTag}`
- Health Check: `GET /actuator/health`

**Environment Variables**:
```yaml
SERVER_PORT: 8010
DATACENTER_ID: 0
WORKER_ID: -1
ENABLE_ZOOKEEPER: true
SPRING_PROFILES_ACTIVE: docker
```

**Startup Profile**:
- Use `--profile full` to start the server
- Default: Only infrastructure (PostgreSQL + ZooKeeper)

---

## ID Generation Modes

### 1. Snowflake Mode

**Algorithm**: Twitter Snowflake
- 64-bit ID
- 1 bit: sign (always 0)
- 41 bits: timestamp (milliseconds)
- 5 bits: datacenter ID
- 5 bits: worker ID
- 12 bits: sequence number

**Endpoint**: `GET /api/v1/id/snowflake`

**Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": 1234567890123456789
}
```

**Characteristics**:
- Time-ordered
- Globally unique
- High performance (millions per second)
- Requires ZooKeeper for worker ID coordination

### 2. Segment Mode

**Algorithm**: Database-based segment allocation
- Pre-allocates ID ranges from database
- Reduces database pressure
- Configurable step size

**Endpoint**: `GET /api/v1/id/segment/{bizTag}`

**Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": 1001
}
```

**Characteristics**:
- Business-specific ID sequences
- Configurable per business tag
- Lower performance than Snowflake
- No external coordination required

---

## External Service Ports (DO NOT USE)

These ports are used by other platform services. **DO NOT** assign these ports to ID Generator to avoid conflicts:

### Blog Microservice
- **PostgreSQL**: `5432` (blog-postgres)
- **Redis**: `6379` (blog-redis)
- **MySQL (Nacos)**: `3307` (blog-mysql-nacos)
- **Nacos**: `8848`, `9848`, `9849` (blog-nacos)
- **RocketMQ NameServer**: `9876` (blog-rocketmq-namesrv)
- **RocketMQ Broker**: `10909`, `10911`, `10912` (blog-rocketmq-broker)
- **RocketMQ Dashboard**: `8180` (blog-rocketmq-dashboard)
- **Elasticsearch**: `9200`, `9300` (blog-elasticsearch)
- **Kibana**: `5601` (blog-kibana)
- **Prometheus**: `9090` (blog-prometheus)
- **Grafana**: `3100` (blog-grafana)
- **SkyWalking OAP**: `11800`, `12800` (blog-skywalking-oap)
- **SkyWalking UI**: `8088` (blog-skywalking-ui)
- **Microservices**: `8000`, `8081-8090` (various blog services)

### File Service
- **PostgreSQL**: `5434` (file-service-postgres)
- **RustFS API**: `9001` (file-service-rustfs)
- **RustFS Console**: `9002` (file-service-rustfs)
- **File Service**: `8089` (file-service-app)

### IM System
- **PostgreSQL**: `5433` (im-postgres)
- **Redis**: `6380` (im-redis)
- **RocketMQ NameServer**: `9877` (im-rocketmq-nameserver)
- **RocketMQ Broker**: `10913`, `10915`, `10916`, `8080`, `8081` (im-rocketmq-broker)
- **RocketMQ Dashboard**: `8082` (im-rocketmq-dashboard)

### Reserved Port Ranges
- **5432-5435**: PostgreSQL instances (5432=blog, 5433=im, 5434=file, 5435=id-gen)
- **6379-6381**: Redis instances (6379=blog, 6380=im, 6381=reserved)
- **3100, 3307**: MySQL/Grafana
- **5601**: Kibana
- **8000-8009, 8011-8090**: Various application services (8010 is ID Generator)
- **8848-8849**: Nacos
- **9001-9002**: Object storage (RustFS)
- **9090**: Prometheus
- **9200, 9300**: Elasticsearch
- **9848-9849**: Nacos gRPC
- **9876-9877**: RocketMQ NameServer
- **10909-10912**: RocketMQ Broker
- **11800, 12800**: SkyWalking

---

## Configuration Best Practices

### 1. Environment-Specific Configuration

Use environment variables with defaults:

```yaml
# Good - Works in both environments
datasource:
  url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5435}/id_generator
  username: ${DB_USER:id_gen_user}
  password: ${DB_PASSWORD:id_gen_password}
```

### 2. Docker Deployment

For services running in Docker, override with container names:

```yaml
# docker-compose.yml
environment:
  - DB_HOST=id-generator-postgres
  - DB_PORT=5432
  - ZOOKEEPER_CONNECTION_STRING=id-generator-zookeeper:2181
```

### 3. Local Development

For local development (services running outside Docker):

```yaml
# application-dev.yml or .env
DB_HOST=localhost
DB_PORT=5435
ZOOKEEPER_CONNECTION_STRING=localhost:2181
```

---

## Client Integration

### Using ID Generator Client

**Maven Dependency**:
```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>id-generator-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Configuration**:
```yaml
id-generator:
  client:
    server-url: http://localhost:8010
    connect-timeout: 5000
    read-timeout: 5000
```

**Usage**:
```java
@Autowired
private IdGeneratorClient idGeneratorClient;

// Get Snowflake ID
Long id = idGeneratorClient.getSnowflakeId();

// Get Segment ID
Long bizId = idGeneratorClient.getSegmentId("user");
```

### Using Spring Boot Starter

**Maven Dependency**:
```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>id-generator-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Configuration**:
```yaml
id-generator:
  server-url: http://localhost:8010
  mode: snowflake  # or segment
  default-biz-tag: default
```

---

## Troubleshooting

### PostgreSQL Connection Issues

**Symptom**: `Connection refused` or `database does not exist`

**Solution**:
```bash
# Test connection from host
psql -h localhost -p 5435 -U id_gen_user -d id_generator

# Test from container
docker exec id-generator-dev-postgres psql -U id_gen_user -d id_generator -c "SELECT * FROM leaf_alloc"

# Check logs
docker logs id-generator-dev-postgres
```

### ZooKeeper Connection Issues

**Symptom**: `Connection refused` or worker ID assignment fails

**Solution**:
```bash
# Test ZooKeeper
echo ruok | nc localhost 2181

# Check ZooKeeper status
docker exec id-generator-dev-zookeeper zkServer.sh status

# Check logs
docker logs id-generator-dev-zookeeper

# Access admin console
open http://localhost:8888
```

### ID Generator Server Issues

**Symptom**: Service not starting or ID generation fails

**Solution**:
```bash
# Check server logs
docker logs id-generator-dev-server

# Test Snowflake endpoint
curl http://localhost:8010/api/v1/id/snowflake

# Test Segment endpoint
curl http://localhost:8010/api/v1/id/segment/test

# Check health
curl http://localhost:8010/actuator/health
```

---

## Quick Reference

### Start Infrastructure Only
```bash
cd docker
docker-compose up -d
```

### Start with Server (Full Profile)
```bash
cd docker
docker-compose --profile full up -d
```

### Check Service Health
```bash
# Check all containers
docker ps

# Check PostgreSQL
docker exec id-generator-dev-postgres pg_isready -U id_gen_user -d id_generator

# Check ZooKeeper
echo ruok | nc localhost 2181

# Check ID Generator Server (if running)
curl http://localhost:8010/actuator/health
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker logs -f id-generator-dev-server
docker logs -f id-generator-dev-postgres
docker logs -f id-generator-dev-zookeeper
```

### Stop All Services
```bash
cd docker
docker-compose --profile full down
```

### Clean Up (Remove Volumes)
```bash
cd docker
docker-compose --profile full down -v
```

---

## Performance Considerations

### Snowflake Mode
- **Throughput**: Millions of IDs per second per instance
- **Latency**: < 1ms
- **Scalability**: Horizontal scaling with ZooKeeper coordination
- **Limitations**: Max 32 datacenters × 32 workers = 1024 instances

### Segment Mode
- **Throughput**: Thousands of IDs per second per business tag
- **Latency**: < 10ms (with pre-allocated segments)
- **Scalability**: Limited by database performance
- **Limitations**: Requires database access for segment allocation

---

## Important Notes

1. **Port Allocation Strategy**: Services use sequential port numbers for the same type of infrastructure:
   - PostgreSQL: 5432 (blog), 5433 (im), 5434 (file), 5435 (id-gen)
   - Redis: 6379 (blog), 6380 (im), 6381 (reserved)

2. **PostgreSQL Port**: Use `5435` from host, `5432` from containers
3. **ZooKeeper Port**: Use `2181` from host and containers
4. **ID Generator Port**: `8010` (same for host and container)
5. **Worker ID**: Set to `-1` for automatic assignment via ZooKeeper
6. **Datacenter ID**: Configure based on deployment region (0-31)
7. **Network**: All services must be on the `id-generator-network` Docker network
8. **Profile**: Use `--profile full` to start the server container

---

## Integration Examples

### Blog Microservice Integration

```yaml
# In blog-microservice application.yml
id-generator:
  client:
    server-url: http://localhost:8010
```

### IM System Integration

```yaml
# In im-system application.yml
id-generator:
  client:
    server-url: http://localhost:8010
```

---

## Related Documentation

- [ID Generator README](../README.md)
- [Deployment Guide](../DEPLOYMENT_SUMMARY.md)
- [Client Documentation](../id-generator-client/README.md)
- [Spring Boot Starter](../id-generator-spring-boot-starter/README.md)
- [Docker Configuration](../docker/docker-compose.yml)
