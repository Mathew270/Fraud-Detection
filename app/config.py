"""
config.py — Centralised configuration and serialization helpers.

All settings are read from environment variables (or a .env file via python-dotenv).
If a variable is not set, a sensible default is used for local development.
The frozen dataclass ensures settings are immutable after creation.
"""

import json
import os
from dataclasses import dataclass
from dotenv import load_dotenv

# Load variables from a .env file (if present) into os.environ,
# so os.getenv() can pick them up without manual exports.
load_dotenv()


@dataclass(frozen=True)
class Settings:
    """Immutable application settings populated from environment variables."""

    # --- Infrastructure connection ---
    # Defaults use Docker service names (container-to-container networking).
    # When running outside Docker, override via .env or environment variables:
    #   KAFKA_BOOTSTRAP_SERVERS=localhost:9094  REDIS_HOST=localhost
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    redis_host: str = os.getenv("REDIS_HOST", "redis")
    redis_port: int = int(os.getenv("REDIS_PORT", "6379"))

    # --- Kafka topic names ---
    transactions_topic: str = os.getenv("TRANSACTIONS_TOPIC", "transactions")
    fraud_alerts_topic: str = os.getenv("FRAUD_ALERTS_TOPIC", "fraud-alerts")

    # --- Fraud detection thresholds ---
    # Flag if a single transaction amount is >= this value
    fraud_amount_threshold: float = float(os.getenv("FRAUD_AMOUNT_THRESHOLD", "5000"))
    # Flag if two consecutive transactions are >= this many km apart
    location_max_distance_km: float = float(
        os.getenv("LOCATION_MAX_DISTANCE_KM", "800")
    )
    # Sliding window length (seconds) for high-frequency detection
    repeat_window_seconds: int = int(os.getenv("REPEAT_WINDOW_SECONDS", "60"))
    # Minimum number of transactions within the window to trigger alert
    repeat_txn_count_threshold: int = int(os.getenv("REPEAT_TXN_COUNT_THRESHOLD", "4"))

    # --- Prometheus metrics server ---
    # Each Python process exposes a /metrics endpoint on its own port
    producer_metrics_port: int = int(os.getenv("PRODUCER_METRICS_PORT", "8000"))
    detector_metrics_port: int = int(os.getenv("DETECTOR_METRICS_PORT", "8002"))
    alert_consumer_metrics_port: int = int(
        os.getenv("ALERT_CONSUMER_METRICS_PORT", "8003")
    )

    # --- Producer simulation parameters ---
    # How many user profiles the producer randomly picks from the generated JSON file
    num_users: int = int(os.getenv("NUM_USERS", "100"))


# Single shared instance used across all modules
settings = Settings()


def json_serializer(payload: dict, ctx=None) -> bytes:
    """Convert a Python dict to UTF-8 encoded JSON bytes for Kafka."""
    return json.dumps(payload).encode("utf-8")


def json_deserializer(payload: bytes, ctx=None) -> dict:
    """Convert UTF-8 JSON bytes back into a Python dict."""
    return json.loads(payload.decode("utf-8"))
