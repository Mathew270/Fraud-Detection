"""
producer.py — Simulated transaction generator.

Continuously produces synthetic financial transactions to the 'transactions'
Kafka topic. Transactions are randomly generated with occasional anomalies
(large amounts, distant locations) to trigger the fraud detector.

Some bursts of rapid-fire transactions are injected (~25% of the time) to
exercise the high-frequency fraud rule.
"""

import random
import time
import uuid
from datetime import datetime, timezone

from confluent_kafka import Producer

from config import json_serializer, settings

# ---------------------------------------------------------------------------
# Simulated user profiles with home locations in Southeast Asia
# ---------------------------------------------------------------------------
USERS = {
    "u-1001": {"country": "SG", "city": "Singapore", "lat": 1.3521, "lon": 103.8198},
    "u-1002": {"country": "MY", "city": "Kuala Lumpur", "lat": 3.1390, "lon": 101.6869},
    "u-1003": {"country": "ID", "city": "Jakarta", "lat": -6.2088, "lon": 106.8456},
    "u-1004": {"country": "TH", "city": "Bangkok", "lat": 13.7563, "lon": 100.5018},
}

# Possible values for transaction metadata fields
MERCHANT_CATEGORIES = [
    "grocery",
    "electronics",
    "fashion",
    "gaming",
    "travel",
    "food_delivery",
    "fuel",
]

PAYMENT_METHODS = ["credit_card", "debit_card", "bank_transfer", "e_wallet"]
CHANNELS = ["online", "in_store", "mobile_app"]
DEVICE_TYPES = ["android", "ios", "desktop"]

# Far-away cities used to simulate impossible-travel anomalies
ANOMALY_LOCATIONS = [
    {"country": "US", "city": "New York", "lat": 40.7128, "lon": -74.0060},
    {"country": "GB", "city": "London", "lat": 51.5072, "lon": -0.1276},
    {"country": "AU", "city": "Sydney", "lat": -33.8688, "lon": 151.2093},
]


# ---------------------------------------------------------------------------
# Transaction generation
# ---------------------------------------------------------------------------

def generate_transaction() -> dict:
    """Build a single randomised transaction dict.

    ~10% chance of a large amount (>= fraud threshold).
    ~8%  chance of an anomalous location (far from user's home).
    """
    user_id = random.choice(list(USERS.keys()))
    base_location = USERS[user_id]

    # Decide whether this transaction should be anomalous
    is_large_amount_case = random.random() < 0.1
    is_location_anomaly_case = random.random() < 0.08

    # Normal amount range $8–$250; large-amount range $5,500–$12,000
    amount = round(random.uniform(8, 250), 2)
    if is_large_amount_case:
        amount = round(random.uniform(5500, 12000), 2)

    # Pick user's home location or a distant anomaly location
    location = random.choice(ANOMALY_LOCATIONS) if is_location_anomaly_case else base_location
    transaction_time = datetime.now(timezone.utc)

    return {
        "transaction_id": str(uuid.uuid4()),
        "timestamp": transaction_time.isoformat(),
        "event_epoch_ms": int(transaction_time.timestamp() * 1000),
        "user_id": user_id,
        "account_id": f"acc-{user_id[-4:]}",
        "amount": amount,
        "currency": "USD",
        "merchant_id": f"m-{random.randint(100, 999)}",
        "merchant_category": random.choice(MERCHANT_CATEGORIES),
        "payment_method": random.choice(PAYMENT_METHODS),
        "channel": random.choice(CHANNELS),
        "card_present": random.choice([True, False]),
        "device_id": f"d-{random.randint(1000, 9999)}",
        "device_type": random.choice(DEVICE_TYPES),
        "ip_address": f"10.{random.randint(1, 254)}.{random.randint(1, 254)}.{random.randint(1, 254)}",
        "country": location["country"],
        "city": location["city"],
        "latitude": location["lat"],
        "longitude": location["lon"],
    }


def delivery_report(err, msg):
    """Callback invoked by the Kafka producer once a message is delivered
    (or permanently fails)."""
    if err is not None:
        print(f"Delivery failed: {err}")


# ---------------------------------------------------------------------------
# Main producer loop
# ---------------------------------------------------------------------------

def main() -> None:
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
