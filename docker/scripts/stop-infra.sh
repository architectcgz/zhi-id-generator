#!/bin/bash

# Stop ID Generator Infrastructure

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(dirname "$SCRIPT_DIR")"

cd "$DOCKER_DIR"

echo "Stopping ID Generator infrastructure..."

# Stop services
docker-compose down

echo ""
echo "Infrastructure stopped successfully!"
echo ""
echo "To remove all data volumes, run:"
echo "  docker-compose down -v"
