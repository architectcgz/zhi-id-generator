#!/bin/bash

# Start Full ID Generator Environment (including server)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(dirname "$SCRIPT_DIR")"

cd "$DOCKER_DIR"

echo "Starting full ID Generator environment..."
echo "- PostgreSQL (port 5435)"
echo "- ID Generator Server (port 8011)"
echo ""

# Check if .env exists, if not copy from example
if [ ! -f .env ]; then
    echo "Creating .env file from .env.example..."
    cp .env.example .env
fi

# Start all services including the server
docker-compose --profile full up -d

echo ""
echo "Waiting for services to be healthy..."
sleep 5
docker-compose ps

echo ""
echo "Full environment started successfully!"
echo ""
echo "Services:"
echo "  PostgreSQL: localhost:5435"
echo "  ID Generator API: http://localhost:8011"
echo ""
echo "Health check:"
echo "  curl http://localhost:8011/api/v1/id/health"
echo ""
echo "Generate Snowflake ID:"
echo "  curl http://localhost:8011/api/v1/id/snowflake"
echo ""
echo "Generate Segment ID:"
echo "  curl http://localhost:8011/api/v1/id/segment/default"
echo ""
echo "To view logs:"
echo "  docker-compose logs -f id-generator-server"
echo ""
echo "To stop all services:"
echo "  docker-compose --profile full down"
