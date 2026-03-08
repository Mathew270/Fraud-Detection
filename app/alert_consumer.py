"""
alert_consumer.py — Fraud alert display service.

Subscribes to the 'fraud-alerts' Kafka topic and prints each alert to the
console in a human-readable format. In a production system this would
forward alerts to a notification service (email, Slack, PagerDuty, etc.).

Prometheus metrics are exposed so alert throughput can be monitored.
"""

from confluent_kafka import Consumer
from prometheus_client import Counter, start_http_server

from config import json_deserializer, settings

# ---------------------------------------------------------------------------
# Prometheus metrics
# ---------------------------------------------------------------------------

# Total alerts received, labelled by severity
ALERTS_RECEIVED = Counter(
    "alert_consumer_alerts_received_total",
    "Total fraud alerts consumed",
    ["severity"],  # "high" or "medium"
)
# Pre-initialize so Prometheus sees both labels immediately
ALERTS_RECEIVED.labels(severity="high")
ALERTS_RECEIVED.labels(severity="medium")


def main() -> None:
    # Expose Prometheus metrics endpoint
    start_http_server(settings.alert_consumer_metrics_port)
    print(f"Prometheus metrics at http://localhost:{settings.alert_consumer_metrics_port}/metrics")

    # Create a Kafka consumer in its own consumer group.
    # This group is separate from the fraud-detector group, so both
    # services independently read from their respective topics.
    consumer = Consumer({
        "bootstrap.servers": settings.kafka_bootstrap_servers,
        "group.id": "alert-service-group",
        "auto.offset.reset": "earliest",
        "enable.auto.commit": True,
    })
    consumer.subscribe([settings.fraud_alerts_topic])

    print(f"Listening for alerts on '{settings.fraud_alerts_topic}'...")

    try:
        while True:
            # Poll for the next alert message (1-second timeout)
            msg = consumer.poll(timeout=1.0)
            if msg is None:
                continue
            if msg.error():
                print(f"Consumer error: {msg.error()}")
                continue

            # Deserialize the alert JSON
            alert = json_deserializer(msg.value())
            txn = alert["transaction"]
            reasons = ", ".join(alert["fraud_reasons"])

            # Update Prometheus counter
            ALERTS_RECEIVED.labels(severity=alert["severity"]).inc()

            # Pretty-print the alert to stdout
            print("=" * 80)
            print("ALERT RECEIVED")
            print(f"alert_id: {alert['alert_id']}")
            print(f"user_id: {txn['user_id']}")
            print(f"transaction_id: {txn['transaction_id']}")
            print(f"amount: {txn['amount']} {txn['currency']}")
            print(f"location: {txn['country']} / {txn['city']}")
            print(f"reasons: {reasons}")
            print(f"severity: {alert['severity']}")
            print("=" * 80)
    finally:
        consumer.close()


if __name__ == "__main__":
    main()
