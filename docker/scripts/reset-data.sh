#!/bin/bash

# Reset all data (PostgreSQL and ZooKeeper)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(dirname "$SCRIPT_DIR")"

cd "$DOCKER_DIR"

echo "WARNING: This will delete all data in PostgreSQL and ZooKeeper!"
echo ""
read -p "Are you sure you want to continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Operation cancelled."
    exit 0
fi

echo ""
echo "Stopping all services..."
docker-compose --profile full down
docker-compose -f docker-compose.yml -f docker-compose.multi.yml down 2>/dev/null || true

echo ""
echo "Removing data volumes..."
docker volume rm docker_postgres_data 2>/dev/null || echo "postgres_data volume not found"
docker volume rm docker_zookeeper_data 2>/dev/null || echo "zookeeper_data volume not found"
docker volume rm docker_zookeeper_datalog 2>/dev/null || echo "zookeeper_datalog volume not found"
docker volume rm docker_zookeeper_logs 2>/dev/null || echo "zookeeper_logs volume not found"

echo ""
echo "Data reset complete!"
echo ""
echo "To start fresh infrastructure:"
echo "  ./scripts/start-infra.sh"
