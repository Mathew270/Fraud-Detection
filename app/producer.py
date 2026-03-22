"""
producer.py — Simulated transaction generator.

Continuously produces synthetic financial transactions to the 'transactions'
Kafka topic. Transactions are randomly generated with occasional anomalies
(large amounts, distant locations) to trigger the fraud detector.

Some bursts of rapid-fire transactions are injected (~25% of the time) to
exercise the high-frequency fraud rule.

Prometheus metrics are exposed so throughput can be monitored in Grafana.
"""

import random
import time

from confluent_kafka import Producer
from prometheus_client import Counter, start_http_server

from config import json_serializer, settings
from transaction_generator import generate_transaction

# ---------------------------------------------------------------------------
# Prometheus metrics
# ---------------------------------------------------------------------------

# Total transactions successfully produced
TRANSACTIONS_PRODUCED = Counter(
    "producer_transactions_produced_total",
    "Total transactions sent to Kafka",
)

# Count of produce errors (delivery callback failures)
PRODUCE_ERRORS = Counter(
    "producer_errors_total",
    "Total produce delivery failures",
)


def delivery_report(err, msg):
    """Callback invoked by the Kafka producer once a message is delivered
    (or permanently fails). Updates Prometheus counters."""
    if err is not None:
        PRODUCE_ERRORS.inc()
        print(f"Delivery failed: {err}")
    else:
        TRANSACTIONS_PRODUCED.inc()


# ---------------------------------------------------------------------------
# Main producer loop
# ---------------------------------------------------------------------------


def main() -> None:
    # Expose Prometheus metrics endpoint
    start_http_server(settings.producer_metrics_port)
    print(
        f"Prometheus metrics at http://localhost:{settings.producer_metrics_port}/metrics"
    )

    # Create Kafka producer pointed at the bootstrap server
    producer = Producer({"bootstrap.servers": settings.kafka_bootstrap_servers})

    print(f"Producing to topic '{settings.transactions_topic}'...")
    try:
        while True:
            # ~25% chance of a burst (2–5 rapid transactions in a row)
            # to exercise the high-frequency fraud rule
            burst_mode = random.random() < 0.25
            burst_count = random.randint(2, 5) if burst_mode else 1

            for _ in range(burst_count):
                transaction = generate_transaction()
                # Send to Kafka — key is user_id so all of a user's
                # transactions land on the same partition (ordering guarantee)
                producer.produce(
                    settings.transactions_topic,
                    value=json_serializer(transaction),
                    key=transaction["user_id"].encode(),
                    callback=delivery_report,
                )
                print(
                    "TXN",
                    transaction["transaction_id"],
                    transaction["user_id"],
                    f"amount={transaction['amount']}",
                    f"loc={transaction['country']}/{transaction['city']}",
                )

            # Flush ensures all buffered messages are actually sent
            producer.flush()
            # Random delay between batches to simulate realistic traffic
            time.sleep(random.uniform(0.3, 1.2))
    except KeyboardInterrupt:
        print("Stopping producer...")
    finally:
        producer.flush()
        producer.close()


if __name__ == "__main__":
    main()
