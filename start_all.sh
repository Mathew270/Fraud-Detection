#!/usr/bin/env bash
# start_all.sh — Boots the entire fraud detection pipeline.
#
# All services (infrastructure + Python apps) run as Docker containers.
# docker compose up --build starts everything with proper dependency ordering:
#   1. Kafka + Redis start first (with health checks)
#   2. fraud-detector + alert-consumer wait for healthy Kafka/Redis
#   3. producer waits for fraud-detector to be up
#
# Usage:  bash start_all.sh
set -e

echo "=== Building and starting all containers ==="
docker compose up -d --build

echo ""
echo "=== Waiting for services to be healthy ==="
for container in kafka redis; do
    echo -n "Waiting for ${container}..."
    until docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null | grep -q "healthy"; do
        sleep 2
        echo -n "."
    done
    echo " ready"
done

echo ""
echo "=== All running ==="
echo ""
echo "Web UIs:"
echo "  Kafka UI         http://localhost:8080"
echo "  Redis Commander   http://localhost:8081"
echo "  RedisInsight      http://localhost:8001"
echo "  Prometheus        http://localhost:9090"
echo "  Grafana           http://localhost:3000  (admin / admin)"
echo ""
echo "View logs:  docker compose logs -f producer fraud-detector alert-consumer"
echo "Stop all:   bash stop_all.sh"
