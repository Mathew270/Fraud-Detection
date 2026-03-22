import json
import random
import uuid
from datetime import datetime, timezone
from pathlib import Path

from config import settings

# ---------------------------------------------------------------------------
# Simulated user profiles loaded from external dataset
# ---------------------------------------------------------------------------
_USERS_FILE = Path(__file__).parent / "data" / "users.json"
try:
    with open(_USERS_FILE, "r") as f:
        _ALL_USERS = json.load(f)
except FileNotFoundError:
    print(f"Warning: {_USERS_FILE} not found. Using fallback.")
    _ALL_USERS = {
        "u-1001": {"country": "SG", "city": "Singapore", "lat": 1.3521, "lon": 103.8198},
    }

# Restrict the active simulation pool based on configured dashboard limit
ACTIVE_USER_IDS = list(_ALL_USERS.keys())[:settings.num_users]

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


def generate_transaction() -> dict:
    """Build a single randomised transaction dict.

    ~10% chance of a large amount (>= fraud threshold).
    ~8%  chance of an anomalous location (far from user's home).
    """
    user_id = random.choice(ACTIVE_USER_IDS)
    base_location = _ALL_USERS[user_id]

    # Decide whether this transaction should be anomalous
    is_large_amount_case = random.random() < 0.1
    is_location_anomaly_case = random.random() < 0.08

    # Normal amount range $8–$250; large-amount range $5,500–$12,000
    amount = round(random.uniform(8, 250), 2)
    if is_large_amount_case:
        amount = round(random.uniform(5500, 12000), 2)

    # Pick user's home location or a distant anomaly location
    location = (
        random.choice(ANOMALY_LOCATIONS) if is_location_anomaly_case else base_location
    )
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
