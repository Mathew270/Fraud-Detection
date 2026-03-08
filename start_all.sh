#!/usr/bin/env bash
# start_all.sh — Boots the entire fraud detection pipeline.
#
# 1. Starts all Docker containers (Kafka, Redis, UIs, Prometheus, Grafana)
# 2. Waits for Kafka and Redis health checks to pass
# 3. Launches the three Python processes in the background
# 4. Traps Ctrl+C to gracefully stop the Python processes
#
# Usage:  bash start_all.sh
set -e

echo "=== Starting Docker containers ==="
docker compose up -d --build

echo ""
echo "=== Waiting for services to be healthy ==="
# Only Kafka and Redis have health checks defined; other containers
# start quickly and don't need gating.
for container in kafka redis; do
    echo -n "Waiting for ${container}..."
    until docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null | grep -q "healthy"; do
        sleep 2
        echo -n "."
    done
    echo " ready"
done

echo ""
echo "=== Starting Python processes ==="
cd app

# Start fraud detector first — it must be consuming before the producer
# sends messages, otherwise alerts could be missed.
python fraud_detector.py &
FRAUD_PID=$!
echo "fraud_detector.py started (PID: ${FRAUD_PID})"

python alert_consumer.py &
ALERT_PID=$!
echo "alert_consumer.py started (PID: ${ALERT_PID})"

# Short delay to let consumers subscribe before traffic begins
sleep 2

python producer.py &
PRODUCER_PID=$!
echo "producer.py started (PID: ${PRODUCER_PID})"

cd ..

echo ""
echo "=== All running ==="
echo "fraud_detector  PID: ${FRAUD_PID}"
echo "alert_consumer  PID: ${ALERT_PID}"
echo "producer        PID: ${PRODUCER_PID}"
echo ""
echo "Web UIs:"
echo "  Kafka UI         http://localhost:8080"
echo "  Redis Commander   http://localhost:8082"
echo "  RedisInsight      http://localhost:8001"
echo "  Prometheus        http://localhost:9090"
echo "  Grafana           http://localhost:3000  (admin / admin)"
echo ""
echo "Press Ctrl+C to stop all Python processes"

# Gracefully forward Ctrl+C to all child processes
trap "echo ''; echo 'Stopping...'; kill $FRAUD_PID $ALERT_PID $PRODUCER_PID 2>/dev/null; wait; echo 'All Python processes stopped.'" INT TERM

wait
