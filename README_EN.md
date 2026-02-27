# ID Generator

A high-performance distributed ID generation service supporting both Snowflake and Segment algorithms.

English | [简体中文](./README.md)

## ✨ Features

- 🚀 **High Performance**: Snowflake mode 100,000+ QPS, Segment mode 50,000+ QPS
- 🔄 **Dual Modes**: Supports both Snowflake (timestamp-based) and Segment (database sequence) modes
- 📦 **Out-of-the-Box**: Provides Spring Boot Starter for zero-configuration integration
- 🎯 **Business Isolation**: Segment mode supports ID sequence isolation by business tags
- 💾 **Local Buffering**: Built-in client buffering mechanism to reduce network requests
- 🔧 **Easy Deployment**: One-click deployment with Docker Compose

## 📦 Modules

- **id-generator-server**: ID generation service providing REST APIs
- **id-generator-client**: Java SDK client
- **id-generator-spring-boot-starter**: Spring Boot auto-configuration

## 🚀 Quick Start

### 1. Start the Service

#### Using Docker (Recommended)

```bash
# Start infrastructure (PostgreSQL + ZooKeeper)
cd docker/scripts
./start-infra.sh    # Linux/Mac
start-infra.bat     # Windows

# Start ID Generator service
cd ../../id-generator-server
mvn spring-boot:run
```

Or start the full environment:

```bash
cd docker/scripts
./start-full.sh     # Linux/Mac
start-full.bat      # Windows
```

#### Verify Service

```bash
curl http://localhost:8010/actuator/health
curl http://localhost:8010/api/v1/id/snowflake
```

### 2. Integrate into Your Project

#### Spring Boot Project (Recommended)

**Add Dependency**:

```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>id-generator-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Configuration** (application.yml):

```yaml
id-generator:
  client:
    server-url: http://localhost:8010
    buffer-enabled: true
    buffer-size: 100
```

**Usage**:

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final IdGeneratorClient idGeneratorClient;
    
    public Order createOrder() {
        // Generate order ID (Snowflake mode)
        long orderId = idGeneratorClient.nextSnowflakeId();
        
        Order order = new Order();
        order.setId(orderId);
        // ... business logic
        
        return order;
    }
    
    public User createUser() {
        // Generate user ID (Segment mode)
        long userId = idGeneratorClient.nextSegmentId("user");
        
        User user = new User();
        user.setId(userId);
        // ... business logic
        
        return user;
    }
}
```

#### Plain Java Project

```java
// Create client configuration
IdGeneratorClientConfig config = IdGeneratorClientConfig.builder()
    .serverUrl("http://localhost:8010")
    .bufferSize(100)
    .bufferEnabled(true)
    .build();

// Create client instance
IdGeneratorClient client = new BufferedIdGeneratorClient(config);

// Use
long id = client.nextSnowflakeId();
long seqId = client.nextSegmentId("order");

// Close client
client.close();
```

## 📖 Usage Guide

### Snowflake Mode

Suitable for scenarios requiring time-based ordering (orders, messages, logs, etc.):

```java
// Generate single ID
long id = idGeneratorClient.nextSnowflakeId();

// Batch generation (more efficient)
List<Long> ids = idGeneratorClient.nextSnowflakeIds(100);

// Parse ID
SnowflakeIdInfo info = idGeneratorClient.parseSnowflakeId(id);
System.out.println("Timestamp: " + info.getTimestamp());
System.out.println("Datacenter ID: " + info.getDatacenterId());
System.out.println("Worker ID: " + info.getWorkerId());
```

### Segment Mode

Suitable for scenarios requiring business isolation (users, products, categories, etc.):

```java
// Generate IDs for different businesses
long userId = idGeneratorClient.nextSegmentId("user");
long orderId = idGeneratorClient.nextSegmentId("order");
long productId = idGeneratorClient.nextSegmentId("product");

// Batch generation
List<Long> orderIds = idGeneratorClient.nextSegmentIds("order", 100);
```

### Adding Business Tags

Before using Segment mode, add business tags to the database:

```sql
INSERT INTO leaf_alloc (biz_tag, max_id, step, description)
VALUES ('your-tag', 1, 1000, 'Your business description');
```

## 🔧 Building

```bash
# Build all modules
mvn clean install

# Build server only
cd id-generator-server
mvn clean package

# Build client only
cd id-generator-client
mvn clean package
```

## 🐳 Docker Deployment

For detailed Docker deployment instructions, see [docker/README.md](docker/README.md)

```bash
# Start infrastructure
cd docker
docker-compose up -d postgres zookeeper

# Start full environment (including service)
docker-compose --profile full up -d

# Stop services
docker-compose down
```

## 📚 API Endpoints

### Snowflake Mode

- `GET /api/v1/id/snowflake` - Generate single Snowflake ID
- `GET /api/v1/id/snowflake/batch?count=10` - Batch generate Snowflake IDs
- `GET /api/v1/id/snowflake/parse/{id}` - Parse Snowflake ID
- `GET /api/v1/id/snowflake/info` - Get Snowflake worker info

### Segment Mode

- `GET /api/v1/id/segment/{bizTag}` - Generate single Segment ID
- `GET /api/v1/id/segment/{bizTag}/batch?count=10` - Batch generate Segment IDs
- `GET /api/v1/id/tags` - Get all business tags

### Health Check

- `GET /api/v1/id/health` - Health check
- `GET /actuator/health` - Spring Boot health check

## 📖 Documentation

- [📘 Integration Guide](docs/INTEGRATION_GUIDE.md) - Detailed integration steps and examples
- [📝 Quick Reference](docs/QUICK_REFERENCE.md) - Common commands and code snippets
- [🐳 Docker Deployment](docker/README.md) - Docker deployment guide
- [💡 Example Code](examples/spring-boot-example/) - Complete Spring Boot example

## ⚙️ Configuration

### Server Configuration

For detailed configuration, see [application.yml](id-generator-server/src/main/resources/application.yml)

Key configuration items:

```yaml
# Database configuration
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/id_generator
    username: id_gen_user
    password: id_gen_password

# Snowflake configuration
id-generator:
  snowflake:
    datacenter-id: 0
    enable-zookeeper: true
    
  # ZooKeeper configuration
  zookeeper:
    connection-string: localhost:2181
```

### Client Configuration

```yaml
id-generator:
  client:
    server-url: http://localhost:8010
    buffer-enabled: true
    buffer-size: 100
    refill-threshold: 20
    async-refill: true
```

## 🎯 Performance Metrics

| Metric | Snowflake Mode | Segment Mode |
|--------|---------------|--------------|
| QPS | 100,000+ | 50,000+ |
| Latency (P99) | < 1ms | < 5ms |
| Availability | 99.99% | 99.9% |

## 🛠️ Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.1
- **Database**: PostgreSQL 16
- **Coordination**: ZooKeeper 3.8
- **ORM**: MyBatis 3.0.3
- **Build Tool**: Maven 3.9+

## 📊 Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client 1  │     │   Client 2  │     │   Client N  │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                  ┌────────▼────────┐
                  │  ID Generator   │
                  │     Server      │
                  └────────┬────────┘
                           │
              ┌────────────┼────────────┐
              │                         │
       ┌──────▼──────┐          ┌──────▼──────┐
       │  PostgreSQL │          │  ZooKeeper  │
       │  (Segment)  │          │ (Snowflake) │
       └─────────────┘          └─────────────┘
```

## 🤝 Contributing

Contributions are welcome! Feel free to submit code, documentation, or suggestions.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

## 💬 Support

- 📧 Email: support@platform.com
- 💬 Issues: [GitHub Issues](https://github.com/your-org/id-generator/issues)
- 📖 Documentation: [Online Docs](https://docs.platform.com/id-generator)

## 🙏 Acknowledgments

This project is inspired by the following excellent open-source projects:

- [Leaf - Meituan Distributed ID Generation System](https://github.com/Meituan-Dianping/Leaf)
- [Twitter Snowflake](https://github.com/twitter-archive/snowflake)

---

**Quick Links**: [Integration Guide](docs/INTEGRATION_GUIDE.md) | [Quick Reference](docs/QUICK_REFERENCE.md) | [Example Code](examples/spring-boot-example/) | [Docker Deployment](docker/README.md)
