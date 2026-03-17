#!/bin/bash

# Reset all Docker data used by ID Generator

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(dirname "$SCRIPT_DIR")"

cd "$DOCKER_DIR"

echo "WARNING: This will delete all PostgreSQL data used by ID Generator!"
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

echo ""
echo "Data reset complete!"
echo ""
echo "To start fresh infrastructure:"
echo "  ./scripts/start-infra.sh"
