#!/usr/bin/env bash
# stop_all.sh — Shuts down the entire fraud detection pipeline.
#
# 1. Sends SIGINT to each Python process (graceful shutdown)
# 2. Waits 2 seconds, then force-kills any survivors
# 3. Runs 'docker compose down' to stop and remove all containers
#
# Usage:  bash stop_all.sh
set -e

echo "=== Stopping Python processes ==="

# Gracefully terminate all 3 Python scripts via SIGINT (same as Ctrl+C).
# The [p] trick in the grep pattern prevents grep from matching itself.
for script in producer.py fraud_detector.py alert_consumer.py; do
    pids=$(ps aux 2>/dev/null | grep "[p]ython.*${script}" | awk '{print $1}' || true)
    if [[ -n "$pids" ]]; then
        echo "Sending SIGINT to ${script} (PID: ${pids})"
        echo "$pids" | xargs kill -INT 2>/dev/null || true
    else
        echo "${script} not running"
    fi
done

# Give processes a moment to shut down gracefully
sleep 2

# Force-kill any stragglers that didn't exit in time
for script in producer.py fraud_detector.py alert_consumer.py; do
    pids=$(ps aux 2>/dev/null | grep "[p]ython.*${script}" | awk '{print $1}' || true)
    if [[ -n "$pids" ]]; then
        echo "Force-killing ${script} (PID: ${pids})"
        echo "$pids" | xargs kill -9 2>/dev/null || true
    fi
done

echo ""
echo "=== Stopping Docker containers ==="
# 'down' stops and removes containers, networks created by 'up'
docker compose down

echo ""
echo "=== All stopped ==="
