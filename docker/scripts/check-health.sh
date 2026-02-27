#!/bin/bash

# Check Health of ID Generator Services

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(dirname "$SCRIPT_DIR")"

cd "$DOCKER_DIR"

echo "Checking ID Generator services health..."
echo ""

# Check PostgreSQL
echo "=== PostgreSQL ==="
if docker-compose ps postgres | grep -q "Up"; then
    echo "Status: Running ✓"
    if docker exec id-generator-postgres pg_isready -U id_gen_user -d id_generator > /dev/null 2>&1; then
        echo "Health: Healthy ✓"
    else
        echo "Health: Unhealthy ✗"
    fi
else
    echo "Status: Not running ✗"
fi
echo ""

# Check ZooKeeper
echo "=== ZooKeeper ==="
if docker-compose ps zookeeper | grep -q "Up"; then
    echo "Status: Running ✓"
    if echo ruok | nc localhost 2181 2>/dev/null | grep -q imok; then
        echo "Health: Healthy ✓"
    else
        echo "Health: Unhealthy ✗"
    fi
else
    echo "Status: Not running ✗"
fi
echo ""

# Check ID Generator Server
echo "=== ID Generator Server ==="
if docker-compose ps id-generator-server 2>/dev/null | grep -q "Up"; then
    echo "Status: Running ✓"
    if curl -s http://localhost:8010/actuator/health > /dev/null 2>&1; then
        HEALTH=$(curl -s http://localhost:8010/actuator/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        echo "Health: $HEALTH"
    else
        echo "Health: Cannot connect ✗"
    fi
else
    echo "Status: Not running (use --profile full to start)"
fi
echo ""

# Check additional instances if running
for port in 8011 8012; do
    if curl -s http://localhost:$port/actuator/health > /dev/null 2>&1; then
        echo "=== ID Generator Server (port $port) ==="
        echo "Status: Running ✓"
        HEALTH=$(curl -s http://localhost:$port/actuator/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        echo "Health: $HEALTH"
        echo ""
    fi
done

echo "=== Docker Compose Status ==="
docker-compose ps
