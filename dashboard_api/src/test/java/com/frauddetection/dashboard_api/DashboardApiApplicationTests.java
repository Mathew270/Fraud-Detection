package com.frauddetection.dashboard_api;

import com.frauddetection.dashboard_api.model.TransactionEvent;
import com.frauddetection.dashboard_api.model.AlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Dashboard API — no Spring context or Kafka broker needed.
 *
 * These tests verify that our manual POJOs (no Lombok) correctly map
 * to/from the JSON format produced by the Python workers.
 *
 * Why unit tests instead of @SpringBootTest?
 * The full context test requires an embedded Kafka broker. We will add
 * integration tests in Phase 1.5. For now, we validate the critical
 * data contract between Python and Java.
 */
class DashboardApiApplicationTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	// =========================================================================
	// TransactionEvent Tests
	// =========================================================================

	@Test
	void transactionEvent_deserializesFromPythonJson() throws Exception {
		// This JSON matches the exact output of transaction_generator.py
		String json = """
			{
				"transaction_id": "abc-123",
				"timestamp": "2026-04-01T12:00:00Z",
				"event_epoch_ms": 1775217600000,
				"user_id": "u-1001",
				"account_id": "acc-1001",
				"amount": 250.50,
				"currency": "USD",
				"merchant_id": "m-456",
				"merchant_category": "grocery",
				"payment_method": "credit_card",
				"channel": "online",
				"card_present": false,
				"device_id": "d-7890",
				"device_type": "android",
				"ip_address": "10.1.2.3",
				"country": "SG",
				"city": "Singapore",
				"latitude": 1.3521,
				"longitude": 103.8198
			}
			""";

		TransactionEvent event = objectMapper.readValue(json, TransactionEvent.class);

		// Verify snake_case -> camelCase mapping via @JsonProperty
		assertEquals("abc-123", event.getTransactionId());
		assertEquals("u-1001", event.getUserId());
		assertEquals(250.50, event.getAmount());
		assertEquals("SG", event.getCountry());
		assertEquals(1.3521, event.getLatitude(), 0.0001);
		assertFalse(event.isCardPresent());
	}

	@Test
	void transactionEvent_ignoresUnknownFields() throws Exception {
		// Python might add new fields in the future — @JsonIgnoreProperties
		// should prevent deserialization from breaking
		String json = """
			{
				"transaction_id": "abc-123",
				"user_id": "u-1001",
				"amount": 100.0,
				"some_future_field": "should_be_ignored"
			}
			""";

		TransactionEvent event = objectMapper.readValue(json, TransactionEvent.class);
		assertEquals("u-1001", event.getUserId());
	}

	@Test
	void transactionEvent_manualGettersAndSettersWork() {
		TransactionEvent event = new TransactionEvent();
		event.setUserId("u-2002");
		event.setAmount(99.99);
		event.setCountry("US");

		assertEquals("u-2002", event.getUserId());
		assertEquals(99.99, event.getAmount());
		assertEquals("US", event.getCountry());
	}

	// =========================================================================
	// AlertEvent Tests
	// =========================================================================

	@Test
	void alertEvent_deserializesFromPythonJson() throws Exception {
		// This JSON matches the alert structure from fraud_detector.py (line 245)
		String json = """
			{
				"alert_id": "alert-abc-123",
				"created_at": "2026-04-01T12:00:01Z",
				"transaction": {
					"transaction_id": "abc-123",
					"user_id": "u-1001",
					"amount": 7500.00,
					"country": "US"
				},
				"fraud_reasons": ["huge_amount", "location_anomaly"],
				"detector_context": {
					"recent_transaction_count_in_window": 5,
					"distance_from_last_km": 9800.50
				},
				"severity": "high"
			}
			""";

		AlertEvent alert = objectMapper.readValue(json, AlertEvent.class);

		assertEquals("alert-abc-123", alert.getAlertId());
		assertEquals("high", alert.getSeverity());
		assertEquals(2, alert.getFraudReasons().size());
		assertTrue(alert.getFraudReasons().contains("huge_amount"));

		// Verify nested TransactionEvent deserialization
		assertNotNull(alert.getTransaction());
		assertEquals("u-1001", alert.getTransaction().getUserId());
		assertEquals(7500.00, alert.getTransaction().getAmount());

		// Verify flexible detector context map
		assertEquals(5, ((Number) alert.getDetectorContext().get("recent_transaction_count_in_window")).intValue());
	}

	@Test
	void alertEvent_toStringIncludesUserId() {
		TransactionEvent txn = new TransactionEvent();
		txn.setUserId("u-3003");

		AlertEvent alert = new AlertEvent();
		alert.setAlertId("alert-xyz");
		alert.setSeverity("medium");
		alert.setFraudReasons(List.of("high_frequency_transactions"));
		alert.setTransaction(txn);

		String str = alert.toString();
		assertTrue(str.contains("u-3003"));
		assertTrue(str.contains("medium"));
	}
}
