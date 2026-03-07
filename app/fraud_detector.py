"""
fraud_detector.py — Core fraud detection pipeline.

Consumes transactions from the 'transactions' Kafka topic, evaluates each one
against a set of fraud heuristics using Redis for state, and publishes alerts
to the 'fraud-alerts' topic when suspicious activity is detected.

Fraud rules:
  1. huge_amount           — single transaction >= configured threshold
  2. location_anomaly      — consecutive transactions far apart geographically
  3. high_frequency        — too many transactions in a short time window
"""

import json
import math
from datetime import datetime, timezone

import redis
from confluent_kafka import Consumer, Producer

from config import json_deserializer, json_serializer, settings


# ---------------------------------------------------------------------------
# Geo-distance helper
# ---------------------------------------------------------------------------

def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate the great-circle distance between two points using the
    Haversine formula. Returns distance in kilometres."""
    radius_km = 6371.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)

    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return radius_km * c


def parse_iso_time(timestamp: str) -> datetime:
    """Normalise an ISO-8601 timestamp (with or without trailing 'Z')
    into a timezone-aware UTC datetime."""
    if timestamp.endswith("Z"):
        timestamp = timestamp[:-1] + "+00:00"
    return datetime.fromisoformat(timestamp).astimezone(timezone.utc)


# ---------------------------------------------------------------------------
# Fraud evaluation logic — all heuristics applied per transaction
# ---------------------------------------------------------------------------

def evaluate_fraud(txn: dict, redis_client: redis.Redis) -> tuple[bool, list[str], dict]:
    """Evaluate a single transaction against all fraud rules.

    Returns:
        (is_fraud, reasons, extra_context)
    """
    user_id = txn["user_id"]
    reasons: list[str] = []

    # --- Rule 1: Unusually large amount ---
    if float(txn["amount"]) >= settings.fraud_amount_threshold:
        reasons.append("huge_amount")

    # --- Rule 2: Location anomaly (impossible travel) ---
    # Retrieve the user's previous transaction from Redis (stored as JSON string)
    last_txn_key = f"user:last_txn:{user_id}"
    previous_raw = redis_client.get(last_txn_key)
    previous_txn = json.loads(previous_raw) if previous_raw else None

    location_context = {}
    if previous_txn:
        # Calculate distance between last and current transaction locations
        distance = haversine_km(
            float(previous_txn["latitude"]),
            float(previous_txn["longitude"]),
            float(txn["latitude"]),
            float(txn["longitude"]),
        )
        location_context = {
            "distance_from_last_km": round(distance, 2),
            "last_country": previous_txn["country"],
            "last_city": previous_txn["city"],
        }
        if distance >= settings.location_max_distance_km:
            reasons.append("location_anomaly")

    # --- Rule 3: High-frequency transactions (sliding window) ---
    # Convert current timestamp to epoch milliseconds for the sorted set score
    now_epoch_ms = int(parse_iso_time(txn["timestamp"]).timestamp() * 1000)
    window_start_ms = now_epoch_ms - (settings.repeat_window_seconds * 1000)

    # Sorted set key: members are transaction IDs, scores are epoch-ms timestamps
    user_txn_zset_key = f"user:txn_times:{user_id}"

    # Add this transaction's timestamp to the sorted set
    redis_client.zadd(user_txn_zset_key, {txn["transaction_id"]: now_epoch_ms})
    # Remove entries that have fallen outside the sliding window
    redis_client.zremrangebyscore(user_txn_zset_key, 0, window_start_ms)
    # Count how many transactions remain inside the window
    repeat_count = redis_client.zcard(user_txn_zset_key)
    # Auto-expire the key if the user goes inactive
    redis_client.expire(user_txn_zset_key, settings.repeat_window_seconds * 2)

    if repeat_count >= settings.repeat_txn_count_threshold:
        reasons.append("high_frequency_transactions")

    # --- Update last-transaction cache for the next evaluation ---
    redis_client.setex(
        last_txn_key,
        24 * 60 * 60,  # TTL: 24 hours
        json.dumps(
            {
                "transaction_id": txn["transaction_id"],
                "timestamp": txn["timestamp"],
                "country": txn["country"],
                "city": txn["city"],
                "latitude": txn["latitude"],
                "longitude": txn["longitude"],
            }
        ),
    )

    extra = {
        "recent_transaction_count_in_window": repeat_count,
        "window_seconds": settings.repeat_window_seconds,
        **location_context,
    }
    return len(reasons) > 0, reasons, extra


# ---------------------------------------------------------------------------
# Main consumer loop
# ---------------------------------------------------------------------------

def main() -> None:
    # Connect to Redis (decode_responses=True so we get str instead of bytes)
    redis_client = redis.Redis(host=settings.redis_host, port=settings.redis_port, decode_responses=True)

    # Create a Kafka consumer in the 'fraud-detector-group' consumer group.
    # 'earliest' means if no committed offset exists, start from the beginning.
    consumer = Consumer({
        "bootstrap.servers": settings.kafka_bootstrap_servers,
        "group.id": "fraud-detector-group",
        "auto.offset.reset": "earliest",
        "enable.auto.commit": True,
    })
    consumer.subscribe([settings.transactions_topic])

    # Producer for publishing fraud alerts to the alerts topic
    alert_producer = Producer({"bootstrap.servers": settings.kafka_bootstrap_servers})

    print(
        f"Listening to '{settings.transactions_topic}' and publishing alerts to '{settings.fraud_alerts_topic}'..."
    )

    try:
        while True:
            # Poll for a single message with a 1-second timeout
            msg = consumer.poll(timeout=1.0)
            if msg is None:
                continue
            if msg.error():
                print(f"Consumer error: {msg.error()}")
                continue

            # Deserialize the message value from JSON bytes into a dict
            txn = json_deserializer(msg.value())

            is_fraud, reasons, extra = evaluate_fraud(txn, redis_client)

            if is_fraud:
                # Build and publish the alert event to the fraud-alerts topic
                alert_event = {
                    "alert_id": f"alert-{txn['transaction_id']}",
                    "created_at": datetime.now(timezone.utc).isoformat(),
                    "transaction": txn,
                    "fraud_reasons": reasons,
                    "detector_context": extra,
                    "severity": "high" if "huge_amount" in reasons else "medium",
                }

                alert_producer.produce(
                    settings.fraud_alerts_topic,
                    key=txn["user_id"].encode("utf-8"),
                    value=json_serializer(alert_event),
                )
                alert_producer.flush()

                print(
                    "FRAUD ALERT",
                    txn["transaction_id"],
                    txn["user_id"],
                    f"reasons={','.join(reasons)}",
                )
            else:
                print(
                    "OK",
                    txn["transaction_id"],
                    txn["user_id"],
                    f"amount={txn['amount']}",
                )
    finally:
        consumer.close()


if __name__ == "__main__":
    main()
