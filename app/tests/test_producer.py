"""
test_producer.py — Unit tests for the transaction producer.

Tests cover:
  - generate_transaction() output structure and field types
  - Randomness constraints (amounts, user IDs, locations)
  - delivery_report() callback behaviour

These tests do NOT require a running Kafka broker — all Kafka interactions
are either not invoked or mocked.
"""

import uuid
from unittest.mock import MagicMock

from producer import (
    generate_transaction,
    delivery_report,
    USERS,
    MERCHANT_CATEGORIES,
    PAYMENT_METHODS,
    CHANNELS,
    DEVICE_TYPES,
)


# =============================================================================
# generate_transaction — structure and field validation
# =============================================================================


class TestGenerateTransaction:
    """Verify the shape, types, and constraints of generated transactions."""

    def test_returns_dict(self):
        """generate_transaction must return a Python dict."""
        txn = generate_transaction()
        assert isinstance(txn, dict)

    def test_required_fields_present(self):
        """Every transaction must contain all expected fields."""
        txn = generate_transaction()
        required = [
            "transaction_id",
            "timestamp",
            "event_epoch_ms",
            "user_id",
            "account_id",
            "amount",
            "currency",
            "merchant_id",
            "merchant_category",
            "payment_method",
            "channel",
            "card_present",
            "device_id",
            "device_type",
            "ip_address",
            "country",
            "city",
            "latitude",
            "longitude",
        ]
        for field in required:
            assert field in txn, f"Missing field: {field}"

    def test_transaction_id_is_valid_uuid(self):
        """transaction_id should be a valid UUID-4 string."""
        txn = generate_transaction()
        # uuid.UUID() will raise ValueError if the string is not a valid UUID
        parsed = uuid.UUID(txn["transaction_id"])
        assert parsed.version == 4

    def test_user_id_from_known_set(self):
        """user_id should be one of the predefined simulated users."""
        txn = generate_transaction()
        assert txn["user_id"] in USERS

    def test_amount_is_positive_float(self):
        """amount must be a positive number."""
        txn = generate_transaction()
        assert isinstance(txn["amount"], float)
        assert txn["amount"] > 0

    def test_currency_is_usd(self):
        """All simulated transactions use USD."""
        txn = generate_transaction()
        assert txn["currency"] == "USD"

    def test_merchant_category_valid(self):
        """merchant_category must be from the predefined list."""
        txn = generate_transaction()
        assert txn["merchant_category"] in MERCHANT_CATEGORIES

    def test_payment_method_valid(self):
        """payment_method must be from the predefined list."""
        txn = generate_transaction()
        assert txn["payment_method"] in PAYMENT_METHODS

    def test_channel_valid(self):
        """channel must be from the predefined list."""
        txn = generate_transaction()
        assert txn["channel"] in CHANNELS

    def test_device_type_valid(self):
        """device_type must be from the predefined list."""
        txn = generate_transaction()
        assert txn["device_type"] in DEVICE_TYPES

    def test_card_present_is_bool(self):
        """card_present should be a boolean."""
        txn = generate_transaction()
        assert isinstance(txn["card_present"], bool)

    def test_latitude_in_range(self):
        """latitude should be between -90 and 90."""
        txn = generate_transaction()
        assert -90 <= txn["latitude"] <= 90

    def test_longitude_in_range(self):
        """longitude should be between -180 and 180."""
        txn = generate_transaction()
        assert -180 <= txn["longitude"] <= 180

    def test_event_epoch_ms_is_int(self):
        """event_epoch_ms must be a positive integer (milliseconds)."""
        txn = generate_transaction()
        assert isinstance(txn["event_epoch_ms"], int)
        assert txn["event_epoch_ms"] > 0

    def test_ip_address_format(self):
        """ip_address should look like a private 10.x.x.x address."""
        txn = generate_transaction()
        parts = txn["ip_address"].split(".")
        assert len(parts) == 4
        assert parts[0] == "10"

    def test_unique_transaction_ids(self):
        """Two calls should produce different transaction IDs."""
        txn1 = generate_transaction()
        txn2 = generate_transaction()
        assert txn1["transaction_id"] != txn2["transaction_id"]


# =============================================================================
# delivery_report — Kafka produce callback
# =============================================================================


class TestDeliveryReport:
    """Test the delivery_report callback used by the Kafka producer."""

    def test_successful_delivery_no_exception(self):
        """When err is None (successful delivery), the callback should not
        raise any exception."""
        mock_msg = MagicMock()
        # Should not raise
        delivery_report(None, mock_msg)

    def test_failed_delivery_no_exception(self):
        """When err is set (failed delivery), the callback should handle the
        error gracefully without raising."""
        mock_msg = MagicMock()
        # Should not raise — it just prints and increments a counter
        delivery_report("Broker unavailable", mock_msg)
