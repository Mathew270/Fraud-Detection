"""
test_integration.py — Integration tests for the fraud detection pipeline.

These tests exercise the full evaluate_fraud flow end-to-end using fakeredis,
simulating realistic multi-step scenarios that span multiple function calls
and verify that Redis state is correctly maintained across evaluations.

Scenarios tested:
  1. Full pipeline: normal → normal → anomalous transaction sequence
  2. Impossible travel: Singapore → London in rapid succession
  3. High-frequency burst: many transactions within the sliding window
  4. Multi-user isolation: concurrent users don't interfere with each other
  5. Combined rules: all three fraud rules triggering simultaneously
  6. Serialization round-trip through the Kafka serde helpers

These tests use fakeredis (in-memory) so no external services are required.
"""

import json
import uuid
from datetime import datetime, timezone, timedelta

import pytest

from fraud_detector import evaluate_fraud, haversine_km
from config import settings, json_serializer, json_deserializer


# =============================================================================
# Scenario 1: Normal transaction sequence — no fraud
# =============================================================================

class TestNormalTransactionSequence:
    """Simulate a user making a series of ordinary purchases in the same city.
    No fraud rule should fire."""

    def test_sequential_normal_transactions(self, fake_redis, sample_transaction):
        """Three small transactions from the same location should all be OK."""
        for i in range(3):
            txn = sample_transaction(
                user_id="u-1001",
                transaction_id=str(uuid.uuid4()),
                amount=50.0 + i * 10,  # 50, 60, 70
                latitude=1.3521,
                longitude=103.8198,
                country="SG",
                city="Singapore",
            )
            is_fraud, reasons, extra = evaluate_fraud(txn, fake_redis)
            # None of these should trigger any fraud rule
            assert is_fraud is False, f"Transaction {i+1} unexpectedly flagged: {reasons}"
            assert reasons == []


# =============================================================================
# Scenario 2: Impossible travel detection
# =============================================================================

class TestImpossibleTravelScenario:
    """Simulate a user transacting in Singapore, then immediately in London.
    The distance (~10,800 km) should trigger location_anomaly."""

    def test_singapore_then_london(self, fake_redis, sample_transaction):
        """First transaction in Singapore, second in London — second should
        trigger 'location_anomaly'."""
        # Step 1: Normal transaction in Singapore
        txn1 = sample_transaction(
            user_id="u-1001",
            transaction_id=str(uuid.uuid4()),
            amount=100.0,
            latitude=1.3521,
            longitude=103.8198,
            country="SG",
            city="Singapore",
        )
        is_fraud_1, reasons_1, _ = evaluate_fraud(txn1, fake_redis)
        assert is_fraud_1 is False  # first txn, no history to compare

        # Step 2: Transaction from London — impossible travel
        txn2 = sample_transaction(
            user_id="u-1001",
            transaction_id=str(uuid.uuid4()),
            amount=100.0,
            latitude=51.5072,
            longitude=-0.1276,
            country="GB",
            city="London",
        )
        is_fraud_2, reasons_2, extra_2 = evaluate_fraud(txn2, fake_redis)

        assert is_fraud_2 is True
        assert "location_anomaly" in reasons_2
        # Verify the context reports the correct previous location
        assert extra_2["last_country"] == "SG"
        assert extra_2["last_city"] == "Singapore"
        # Distance should be roughly 10,800 km
        assert extra_2["distance_from_last_km"] > settings.location_max_distance_km

    def test_location_history_updates_after_anomaly(self, fake_redis, sample_transaction):
        """After flagging an anomaly, the 'last transaction' in Redis should
        be updated to the new (anomalous) location, so a subsequent
        transaction from that new location is NOT flagged again."""
        # Transaction 1: Singapore
        txn1 = sample_transaction(
            user_id="u-1001", transaction_id=str(uuid.uuid4()),
            latitude=1.3521, longitude=103.8198, country="SG", city="Singapore",
        )
        evaluate_fraud(txn1, fake_redis)

        # Transaction 2: London (triggers anomaly)
        txn2 = sample_transaction(
            user_id="u-1001", transaction_id=str(uuid.uuid4()),
            latitude=51.5072, longitude=-0.1276, country="GB", city="London",
        )
        evaluate_fraud(txn2, fake_redis)

        # Transaction 3: London again (should NOT trigger — same location)
        txn3 = sample_transaction(
            user_id="u-1001", transaction_id=str(uuid.uuid4()),
            amount=30.0,
            latitude=51.5072, longitude=-0.1276, country="GB", city="London",
        )
        is_fraud_3, reasons_3, _ = evaluate_fraud(txn3, fake_redis)
        assert "location_anomaly" not in reasons_3


# =============================================================================
# Scenario 3: High-frequency burst detection
# =============================================================================

class TestHighFrequencyBurstScenario:
    """Simulate a rapid burst of transactions exceeding the threshold count
    within the sliding window."""

    def test_burst_exceeds_threshold(self, fake_redis, sample_transaction):
        """Sending repeat_txn_count_threshold transactions from the same user
        within a few seconds should trigger 'high_frequency_transactions'
        on the last one."""
        threshold = settings.repeat_txn_count_threshold
        all_results = []

        for i in range(threshold):
            txn = sample_transaction(
                user_id="u-1002",
                transaction_id=str(uuid.uuid4()),
                amount=20.0,
            )
            is_fraud, reasons, extra = evaluate_fraud(txn, fake_redis)
            all_results.append((is_fraud, reasons, extra))

        # The final transaction should have triggered the frequency rule
        final_fraud, final_reasons, final_extra = all_results[-1]
        assert "high_frequency_transactions" in final_reasons
        assert final_extra["recent_transaction_count_in_window"] >= threshold

    def test_transactions_outside_window_not_counted(self, fake_redis, sample_transaction):
        """Transactions with timestamps outside the sliding window should be
        pruned and not count toward the threshold."""
        # Create a transaction with a timestamp far in the past (outside window)
        old_time = datetime.now(timezone.utc) - timedelta(seconds=settings.repeat_window_seconds + 60)
        old_txn = sample_transaction(
            user_id="u-1003",
            transaction_id="old-txn-001",
            timestamp=old_time.isoformat(),
        )
        evaluate_fraud(old_txn, fake_redis)

        # Now send a fresh transaction — should only count itself (1)
        new_txn = sample_transaction(
            user_id="u-1003",
            transaction_id=str(uuid.uuid4()),
        )
        _, _, extra = evaluate_fraud(new_txn, fake_redis)
        assert extra["recent_transaction_count_in_window"] == 1


# =============================================================================
# Scenario 4: Multi-user isolation
# =============================================================================

class TestMultiUserIsolation:
    """Verify that fraud detection state is isolated per user — one user's
    activity does not affect another user's evaluation."""

    def test_user_a_burst_does_not_affect_user_b(self, fake_redis, sample_transaction):
        """A burst from user A should not cause user B's single transaction
        to be flagged."""
        # User A: send many transactions (triggers high_frequency for user A)
        for _ in range(settings.repeat_txn_count_threshold + 2):
            txn_a = sample_transaction(
                user_id="u-1001", transaction_id=str(uuid.uuid4()), amount=30.0,
            )
            evaluate_fraud(txn_a, fake_redis)

        # User B: single normal transaction — should be clean
        txn_b = sample_transaction(
            user_id="u-1002", transaction_id=str(uuid.uuid4()), amount=30.0,
        )
        is_fraud, reasons, extra = evaluate_fraud(txn_b, fake_redis)
        assert is_fraud is False
        assert extra["recent_transaction_count_in_window"] == 1

    def test_user_a_location_does_not_affect_user_b(
        self, fake_redis, sample_transaction, previous_transaction_in_redis
    ):
        """User A's previous location (Singapore) should not be used when
        evaluating user B's transaction."""
        # Seed user A's last transaction in Singapore
        previous_transaction_in_redis("u-1001", latitude=1.3521, longitude=103.8198)

        # User B transacts from London — should NOT trigger location_anomaly
        # because user B has no transaction history
        txn_b = sample_transaction(
            user_id="u-1002", transaction_id=str(uuid.uuid4()),
            latitude=51.5072, longitude=-0.1276, country="GB", city="London",
        )
        _, reasons, _ = evaluate_fraud(txn_b, fake_redis)
        assert "location_anomaly" not in reasons


# =============================================================================
# Scenario 5: All three rules triggered at once
# =============================================================================

class TestAllRulesTriggered:
    """Scenario where a single transaction triggers all three fraud rules
    simultaneously: huge_amount + location_anomaly + high_frequency."""

    def test_triple_fraud(self, fake_redis, sample_transaction, previous_transaction_in_redis):
        """Build up enough history to trigger high_frequency, set up a distant
        previous location, and use a huge amount — all three rules should fire."""
        user = "u-1004"

        # Seed a previous location in Singapore for location_anomaly
        previous_transaction_in_redis(user, latitude=1.3521, longitude=103.8198)

        # Send (threshold - 1) normal transactions to prime the frequency window
        for _ in range(settings.repeat_txn_count_threshold - 1):
            txn = sample_transaction(
                user_id=user, transaction_id=str(uuid.uuid4()), amount=20.0,
            )
            evaluate_fraud(txn, fake_redis)

        # The final transaction: huge amount + distant location
        final_txn = sample_transaction(
            user_id=user,
            transaction_id=str(uuid.uuid4()),
            amount=settings.fraud_amount_threshold + 5000,
            latitude=40.7128,   # New York
            longitude=-74.0060,
            country="US",
            city="New York",
        )
        is_fraud, reasons, extra = evaluate_fraud(final_txn, fake_redis)

        assert is_fraud is True
        assert "huge_amount" in reasons
        assert "location_anomaly" in reasons
        assert "high_frequency_transactions" in reasons
        assert len(reasons) == 3


# =============================================================================
# Scenario 6: Kafka serialization round-trip
# =============================================================================

class TestSerializationRoundTrip:
    """Verify that a transaction dict survives serialization → deserialization
    (simulating what happens when travelling through Kafka)."""

    def test_transaction_round_trip(self, sample_transaction):
        """Serialize a transaction to bytes and deserialize it back — the
        result should be identical to the original."""
        original = sample_transaction(amount=1234.56, user_id="u-1001")
        raw_bytes = json_serializer(original)
        recovered = json_deserializer(raw_bytes)
        assert recovered == original

    def test_alert_event_round_trip(self, sample_transaction):
        """Simulate building an alert event (as the fraud detector does) and
        verify it survives the Kafka serde round-trip."""
        txn = sample_transaction(amount=9999.99)
        alert_event = {
            "alert_id": f"alert-{txn['transaction_id']}",
            "created_at": datetime.now(timezone.utc).isoformat(),
            "transaction": txn,
            "fraud_reasons": ["huge_amount", "location_anomaly"],
            "detector_context": {
                "distance_from_last_km": 15320.45,
                "recent_transaction_count_in_window": 2,
            },
            "severity": "high",
        }
        raw = json_serializer(alert_event)
        recovered = json_deserializer(raw)
        assert recovered["alert_id"] == alert_event["alert_id"]
        assert recovered["fraud_reasons"] == ["huge_amount", "location_anomaly"]
        assert recovered["transaction"]["amount"] == 9999.99
