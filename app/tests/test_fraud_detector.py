"""
test_fraud_detector.py — Unit tests for the fraud detection engine.

Tests cover every fraud rule independently and in combination:
  1. huge_amount         — flags transactions >= threshold
  2. location_anomaly    — flags impossible-travel distances
  3. high_frequency      — flags bursts of rapid transactions

Also tests the helper functions:
  - haversine_km()  — geo-distance calculation
  - parse_iso_time() — ISO-8601 timestamp normalisation

All Redis interactions use fakeredis so no running Redis server is needed.
"""

import uuid
import math

from fraud_detector import haversine_km, parse_iso_time, evaluate_fraud
from config import settings


# =============================================================================
# haversine_km — great-circle distance
# =============================================================================


class TestHaversineKm:
    """Unit tests for the Haversine distance formula."""

    def test_same_point_returns_zero(self):
        """Distance from a point to itself should be zero."""
        assert haversine_km(1.3521, 103.8198, 1.3521, 103.8198) == 0.0

    def test_singapore_to_kuala_lumpur(self):
        """Singapore → Kuala Lumpur ≈ 315 km (well-known reference)."""
        dist = haversine_km(1.3521, 103.8198, 3.1390, 101.6869)
        # Allow ±15 km tolerance for rounding / reference variation
        assert 300 < dist < 330, f"Expected ~315 km, got {dist}"

    def test_singapore_to_new_york(self):
        """Singapore → New York ≈ 15,300 km — should exceed 800 km threshold."""
        dist = haversine_km(1.3521, 103.8198, 40.7128, -74.0060)
        assert dist > settings.location_max_distance_km

    def test_short_distance_below_threshold(self):
        """Two nearby points should be well below the anomaly threshold."""
        # Singapore to a point ~50 km away
        dist = haversine_km(1.3521, 103.8198, 1.80, 103.90)
        assert dist < settings.location_max_distance_km

    def test_symmetry(self):
        """Distance from A→B should equal distance from B→A."""
        d1 = haversine_km(1.3521, 103.8198, 40.7128, -74.0060)
        d2 = haversine_km(40.7128, -74.0060, 1.3521, 103.8198)
        assert math.isclose(d1, d2, rel_tol=1e-9)

    def test_antipodal_points(self):
        """Opposite sides of the globe ≈ 20,015 km (half Earth circumference)."""
        dist = haversine_km(0, 0, 0, 180)
        assert 20000 < dist < 20100


# =============================================================================
# parse_iso_time — timestamp normalisation
# =============================================================================


class TestParseIsoTime:
    """Unit tests for ISO-8601 timestamp parsing."""

    def test_standard_iso_format(self):
        """Parse a standard UTC ISO timestamp with +00:00 offset."""
        result = parse_iso_time("2025-06-15T12:30:00+00:00")
        assert result.year == 2025
        assert result.month == 6
        assert result.hour == 12
        assert result.tzinfo is not None  # must be timezone-aware

    def test_z_suffix(self):
        """Trailing 'Z' (Zulu time) should be treated as UTC."""
        result = parse_iso_time("2025-01-01T00:00:00Z")
        assert result.tzinfo is not None
        assert result.utcoffset().total_seconds() == 0

    def test_microseconds_preserved(self):
        """Microseconds in the timestamp should be kept."""
        result = parse_iso_time("2025-03-08T14:23:45.123456+00:00")
        assert result.microsecond == 123456

    def test_output_is_utc(self):
        """Result should always be in UTC regardless of input offset."""
        # +08:00 input → should convert to UTC
        result = parse_iso_time("2025-06-15T20:00:00+08:00")
        assert result.hour == 12  # 20:00+08 → 12:00 UTC


# =============================================================================
# evaluate_fraud — Rule 1: huge_amount
# =============================================================================


class TestHugeAmountRule:
    """Test the high-amount fraud detection rule in isolation."""

    def test_amount_above_threshold_triggers_alert(
        self, fake_redis, sample_transaction
    ):
        """A transaction at or above the threshold should flag 'huge_amount'."""
        txn = sample_transaction(amount=settings.fraud_amount_threshold + 1000)
        is_fraud, reasons, _ = evaluate_fraud(txn, fake_redis)
        assert is_fraud is True
        assert "huge_amount" in reasons

    def test_amount_exactly_at_threshold(self, fake_redis, sample_transaction):
        """Amount == threshold should still trigger (>= comparison)."""
        txn = sample_transaction(amount=settings.fraud_amount_threshold)
        is_fraud, reasons, _ = evaluate_fraud(txn, fake_redis)
        assert "huge_amount" in reasons

    def test_amount_below_threshold_no_alert(self, fake_redis, sample_transaction):
        """A small amount should not trigger 'huge_amount'."""
        txn = sample_transaction(amount=50.0)
        is_fraud, reasons, _ = evaluate_fraud(txn, fake_redis)
        assert "huge_amount" not in reasons

    def test_amount_just_below_threshold(self, fake_redis, sample_transaction):
        """Amount one cent below threshold should not trigger."""
        txn = sample_transaction(amount=settings.fraud_amount_threshold - 0.01)
        is_fraud, reasons, _ = evaluate_fraud(txn, fake_redis)
        assert "huge_amount" not in reasons


# =============================================================================
# evaluate_fraud — Rule 2: location_anomaly
# =============================================================================


class TestLocationAnomalyRule:
    """Test the impossible-travel / location anomaly detection rule."""

    def test_distant_location_triggers_alert(
        self, fake_redis, sample_transaction, previous_transaction_in_redis
    ):
        """If the previous transaction was in Singapore and the current one is
        in New York, the distance (>15,000 km) should flag 'location_anomaly'."""
        # Seed a previous transaction in Singapore
        previous_transaction_in_redis("u-1001", latitude=1.3521, longitude=103.8198)
        # Current transaction in New York
        txn = sample_transaction(
            user_id="u-1001",
            latitude=40.7128,
            longitude=-74.0060,
            country="US",
            city="New York",
        )
        is_fraud, reasons, extra = evaluate_fraud(txn, fake_redis)
        assert "location_anomaly" in reasons
        # The context should include the computed distance
        assert "distance_from_last_km" in extra

    def test_nearby_location_no_alert(
        self, fake_redis, sample_transaction, previous_transaction_in_redis
    ):
        """Two transactions in nearby cities should not flag location anomaly."""
        # Previous in Singapore
        previous_transaction_in_redis("u-1001", latitude=1.3521, longitude=103.8198)
        # Current also in Singapore (same coordinates)
        txn = sample_transaction(user_id="u-1001", latitude=1.3521, longitude=103.8198)
        _, reasons, _ = evaluate_fraud(txn, fake_redis)
        assert "location_anomaly" not in reasons

    def test_no_previous_transaction_no_alert(self, fake_redis, sample_transaction):
        """First-ever transaction for a user (no Redis history) should not
        trigger location anomaly — there's nothing to compare against."""
        txn = sample_transaction(user_id="u-9999")
        _, reasons, _ = evaluate_fraud(txn, fake_redis)
        assert "location_anomaly" not in reasons

    def test_distance_context_populated(
        self, fake_redis, sample_transaction, previous_transaction_in_redis
    ):
        """The extra context dict should contain last_country and last_city
        when a previous transaction exists."""
        previous_transaction_in_redis(
            "u-1001", latitude=1.35, longitude=103.82, country="SG", city="Singapore"
        )
        txn = sample_transaction(user_id="u-1001")
        _, _, extra = evaluate_fraud(txn, fake_redis)
        assert extra["last_country"] == "SG"
        assert extra["last_city"] == "Singapore"


# =============================================================================
# evaluate_fraud — Rule 3: high_frequency_transactions
# =============================================================================


class TestHighFrequencyRule:
    """Test the sliding-window, high-frequency transaction detection rule."""

    def test_burst_triggers_alert(self, fake_redis, sample_transaction):
        """Sending >= repeat_txn_count_threshold transactions within the
        window should trigger 'high_frequency_transactions'."""
        reasons_final = []
        # Send exactly threshold-count transactions in quick succession
        for i in range(settings.repeat_txn_count_threshold):
            txn = sample_transaction(
                user_id="u-1001",
                transaction_id=str(uuid.uuid4()),
            )
            _, reasons_final, _ = evaluate_fraud(txn, fake_redis)

        assert "high_frequency_transactions" in reasons_final

    def test_below_threshold_no_alert(self, fake_redis, sample_transaction):
        """Fewer transactions than the threshold should not trigger."""
        reasons_final = []
        for i in range(settings.repeat_txn_count_threshold - 1):
            txn = sample_transaction(
                user_id="u-1002",
                transaction_id=str(uuid.uuid4()),
            )
            _, reasons_final, _ = evaluate_fraud(txn, fake_redis)

        assert "high_frequency_transactions" not in reasons_final

    def test_window_count_in_context(self, fake_redis, sample_transaction):
        """The extra context should report how many transactions are in the
        current sliding window."""
        txn = sample_transaction(user_id="u-1003", transaction_id=str(uuid.uuid4()))
        _, _, extra = evaluate_fraud(txn, fake_redis)
        assert "recent_transaction_count_in_window" in extra
        assert extra["recent_transaction_count_in_window"] >= 1

    def test_different_users_independent(self, fake_redis, sample_transaction):
        """Transactions from different users should not count toward each
        other's sliding window."""
        # Send 3 transactions for user A
        for _ in range(3):
            txn = sample_transaction(user_id="u-1001", transaction_id=str(uuid.uuid4()))
            evaluate_fraud(txn, fake_redis)

        # Send 1 transaction for user B — should only see count=1 for user B
        txn_b = sample_transaction(user_id="u-1002", transaction_id=str(uuid.uuid4()))
        _, _, extra = evaluate_fraud(txn_b, fake_redis)
        assert extra["recent_transaction_count_in_window"] == 1


# =============================================================================
# evaluate_fraud — multiple rules triggered simultaneously
# =============================================================================


class TestMultipleRules:
    """Test scenarios where more than one fraud rule fires at once."""

    def test_huge_amount_and_location_anomaly(
        self, fake_redis, sample_transaction, previous_transaction_in_redis
    ):
        """A large transaction from a distant location should trigger both
        'huge_amount' AND 'location_anomaly'."""
        previous_transaction_in_redis("u-1001", latitude=1.35, longitude=103.82)
        txn = sample_transaction(
            user_id="u-1001",
            amount=settings.fraud_amount_threshold + 500,
            latitude=51.5072,  # London
            longitude=-0.1276,
        )
        is_fraud, reasons, _ = evaluate_fraud(txn, fake_redis)
        assert is_fraud is True
        assert "huge_amount" in reasons
        assert "location_anomaly" in reasons

    def test_clean_transaction_no_fraud(self, fake_redis, sample_transaction):
        """A normal-amount, first-time, single transaction should not trigger
        any fraud rule."""
        txn = sample_transaction(user_id="u-9999", amount=25.00)
        is_fraud, reasons, _ = evaluate_fraud(txn, fake_redis)
        assert is_fraud is False
        assert reasons == []


# =============================================================================
# evaluate_fraud — Redis state persistence
# =============================================================================


class TestRedisStatePersistence:
    """Verify that evaluate_fraud correctly writes state to Redis for
    subsequent evaluations."""

    def test_last_transaction_stored(self, fake_redis, sample_transaction):
        """After evaluation, the user's last transaction should be saved in
        Redis so the next call can compute location distance."""
        txn = sample_transaction(user_id="u-1001")
        evaluate_fraud(txn, fake_redis)
        # The key should now exist
        stored = fake_redis.get("user:last_txn:u-1001")
        assert stored is not None

    def test_transaction_times_sorted_set(self, fake_redis, sample_transaction):
        """The sliding-window sorted set should contain the transaction ID
        after evaluation."""
        txn = sample_transaction(user_id="u-1001", transaction_id="txn-abc-123")
        evaluate_fraud(txn, fake_redis)
        members = fake_redis.zrange("user:txn_times:u-1001", 0, -1)
        assert "txn-abc-123" in members
