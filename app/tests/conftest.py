"""
conftest.py — Shared pytest fixtures for the fraud detection test suite.

Provides reusable fixtures such as:
  - A fake Redis client (fakeredis) so unit tests don't need a running Redis
  - Helper functions to build sample transaction dicts
  - Automatic sys.path setup so tests can import from the app directory
"""

import sys
import os
import json
import uuid
from datetime import datetime, timezone

import pytest
import fakeredis

# ---------------------------------------------------------------------------
# Ensure the app/ directory is on sys.path so `import config`, `import
# fraud_detector`, etc. work from anywhere pytest is invoked.
# ---------------------------------------------------------------------------
APP_DIR = os.path.join(os.path.dirname(__file__), os.pardir)
sys.path.insert(0, os.path.abspath(APP_DIR))


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def fake_redis():
    """Return a fakeredis client that behaves like redis.Redis but runs
    entirely in-memory.  Each test gets a fresh, empty instance so tests
    never interfere with each other."""
    r = fakeredis.FakeRedis(decode_responses=True)
    yield r
    r.flushall()   # clean up after the test


@pytest.fixture
def sample_transaction():
    """Return a factory function that builds a realistic transaction dict.

    Usage in tests:
        txn = sample_transaction()            # defaults
        txn = sample_transaction(amount=9999)  # override specific fields
    """
    def _make(**overrides) -> dict:
        now = datetime.now(timezone.utc)
        base = {
            "transaction_id": str(uuid.uuid4()),
            "timestamp": now.isoformat(),
            "event_epoch_ms": int(now.timestamp() * 1000),
            "user_id": "u-1001",
            "account_id": "acc-1001",
            "amount": 100.00,
            "currency": "USD",
            "merchant_id": "m-500",
            "merchant_category": "grocery",
            "payment_method": "credit_card",
            "channel": "online",
            "card_present": False,
            "device_id": "d-1234",
            "device_type": "android",
            "ip_address": "10.0.0.1",
            "country": "SG",
            "city": "Singapore",
            "latitude": 1.3521,
            "longitude": 103.8198,
        }
        base.update(overrides)
        return base
    return _make


@pytest.fixture
def previous_transaction_in_redis(fake_redis):
    """Return a helper that seeds a 'last transaction' record in Redis,
    simulating a user who already has transaction history.

    Usage:
        previous_transaction_in_redis("u-1001", lat=1.35, lon=103.82, ...)
    """
    def _seed(user_id: str, **overrides) -> dict:
        now = datetime.now(timezone.utc)
        data = {
            "transaction_id": str(uuid.uuid4()),
            "timestamp": now.isoformat(),
            "country": "SG",
            "city": "Singapore",
            "latitude": 1.3521,
            "longitude": 103.8198,
        }
        data.update(overrides)
        key = f"user:last_txn:{user_id}"
        fake_redis.setex(key, 86400, json.dumps(data))
        return data
    return _seed
