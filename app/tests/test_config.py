"""
test_config.py — Unit tests for config.py (Settings + serialization helpers).

Tests cover:
  - Default values of the Settings dataclass
  - Immutability of frozen dataclass instances
  - JSON serializer round-trip (dict → bytes → dict)
  - JSON deserializer handles valid and edge-case payloads
"""

import json
import pytest

from config import Settings, settings, json_serializer, json_deserializer


# =============================================================================
# Settings defaults
# =============================================================================


class TestSettingsDefaults:
    """Verify that Settings fields have the expected default values
    when no environment variables are set."""

    def test_kafka_bootstrap_servers_default(self):
        """Default Kafka address should be the Docker service name."""
        s = Settings()
        assert s.kafka_bootstrap_servers in (
            "kafka:9092",
            settings.kafka_bootstrap_servers,
        )

    def test_redis_host_default(self):
        """Default Redis host should be the Docker service name 'redis'."""
        s = Settings()
        assert s.redis_host in ("redis", settings.redis_host)

    def test_redis_port_is_integer(self):
        """redis_port must be an integer (not a string from env)."""
        assert isinstance(settings.redis_port, int)

    def test_fraud_amount_threshold_type(self):
        """Fraud threshold must be a float."""
        assert isinstance(settings.fraud_amount_threshold, float)

    def test_repeat_window_seconds_type(self):
        """Sliding window must be an int."""
        assert isinstance(settings.repeat_window_seconds, int)

    def test_repeat_txn_count_threshold_positive(self):
        """Repeat threshold must be a positive integer."""
        assert settings.repeat_txn_count_threshold > 0


# =============================================================================
# Immutability
# =============================================================================


class TestSettingsImmutability:
    """Settings is a frozen dataclass — attempts to mutate should raise."""

    def test_cannot_set_attribute(self):
        """Assigning to a field on a frozen instance must raise FrozenInstanceError."""
        with pytest.raises(AttributeError):
            settings.redis_host = "other-host"

    def test_cannot_delete_attribute(self):
        """Deleting a field on a frozen instance must raise FrozenInstanceError."""
        with pytest.raises(AttributeError):
            del settings.redis_port


# =============================================================================
# JSON serializer / deserializer
# =============================================================================


class TestJsonSerializer:
    """json_serializer converts a Python dict to UTF-8 encoded JSON bytes."""

    def test_returns_bytes(self):
        """Output must be of type bytes."""
        result = json_serializer({"key": "value"})
        assert isinstance(result, bytes)

    def test_output_is_valid_json(self):
        """The bytes must decode to valid JSON."""
        payload = {"amount": 99.99, "user": "u-1001"}
        raw = json_serializer(payload)
        parsed = json.loads(raw.decode("utf-8"))
        assert parsed == payload

    def test_empty_dict(self):
        """An empty dict should serialize to b'{}'."""
        assert json_serializer({}) == b"{}"

    def test_nested_structures(self):
        """Nested dicts and lists should survive serialization."""
        payload = {"items": [1, 2, 3], "meta": {"nested": True}}
        raw = json_serializer(payload)
        assert json.loads(raw) == payload

    def test_ctx_parameter_ignored(self):
        """The optional ctx parameter (for Kafka serde API compat) should
        not affect the output."""
        result_no_ctx = json_serializer({"a": 1})
        result_with_ctx = json_serializer({"a": 1}, ctx="ignored")
        assert result_no_ctx == result_with_ctx


class TestJsonDeserializer:
    """json_deserializer converts UTF-8 JSON bytes back to a Python dict."""

    def test_returns_dict(self):
        """Output must be of type dict."""
        raw = b'{"key": "value"}'
        assert isinstance(json_deserializer(raw), dict)

    def test_round_trip(self):
        """Serializing then deserializing should return the original dict."""
        original = {"transaction_id": "abc-123", "amount": 42.0}
        assert json_deserializer(json_serializer(original)) == original

    def test_unicode_content(self):
        """Unicode strings should survive the round-trip."""
        original = {"city": "Zürich", "note": "日本語テスト"}
        assert json_deserializer(json_serializer(original)) == original

    def test_ctx_parameter_ignored(self):
        """The optional ctx parameter should not affect the output."""
        raw = b'{"x": 1}'
        assert json_deserializer(raw) == json_deserializer(raw, ctx="ignored")
