#!/usr/bin/env bash
# stop_all.sh — Shuts down the entire fraud detection pipeline.
#
# Since all services (including Python apps) are Docker containers,
# a single 'docker compose down' stops and removes everything.
#
# Usage:  bash stop_all.sh
set -e

echo "=== Stopping all containers ==="
# 'down' sends SIGTERM to each container, waits for graceful shutdown,
# then removes the containers and the fraud-detection-net network.
docker compose down

echo ""
echo "=== All stopped ==="
