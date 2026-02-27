#!/bin/bash

# Start Multiple ID Generator Server Instances for Testing

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(dirname "$SCRIPT_DIR")"

cd "$DOCKER_DIR"

echo "Starting multiple ID Generator server instances..."
echo "- PostgreSQL (port 5434)"
echo "- ZooKeeper (port 2181)"
echo "- ID Generator Server 1 (port 8010)"
echo "- ID Generator Server 2 (port 8011)"
echo "- ID Generator Server 3 (port 8012)"
echo ""

# Check if .env exists, if not copy from example
if [ ! -f .env ]; then
    echo "Creating .env file from .env.example..."
    cp .env.example .env
fi

# Start infrastructure first
docker-compose up -d postgres zookeeper

echo "Waiting for infrastructure to be ready..."
sleep 10

# Start all server instances
docker-compose --profile full up -d
docker-compose -f docker-compose.yml -f docker-compose.multi.yml up -d

echo ""
echo "Waiting for services to be healthy..."
sleep 5
docker-compose ps

echo ""
echo "Multiple instances started successfully!"
echo ""
echo "Services:"
echo "  PostgreSQL: localhost:5434"
echo "  ZooKeeper: localhost:2181"
echo "  Server 1: http://localhost:8010"
echo "  Server 2: http://localhost:8011"
echo "  Server 3: http://localhost:8012"
echo ""
echo "Test distributed Worker ID allocation:"
echo "  curl http://localhost:8010/api/v1/id/snowflake"
echo "  curl http://localhost:8011/api/v1/id/snowflake"
echo "  curl http://localhost:8012/api/v1/id/snowflake"
echo ""
echo "Check Worker IDs in ZooKeeper:"
echo "  docker exec -it id-generator-zookeeper zkCli.sh ls /leaf/id-generator/snowflake"
echo ""
echo "To stop all instances:"
echo "  docker-compose -f docker-compose.yml -f docker-compose.multi.yml down"
