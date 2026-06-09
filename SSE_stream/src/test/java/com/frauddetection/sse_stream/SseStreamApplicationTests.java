package com.frauddetection.sse_stream;

import com.frauddetection.sse_stream.model.TransactionEvent;
import com.frauddetection.sse_stream.model.AlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Dashboard API DTOs.
 *
 * These tests validate the JSON data contract between the Python workers
 * and the Java backend. Each test uses sample JSON that matches the exact
 * format produced by the Python services (transaction_generator.py,
 * fraud_detector.py), ensuring that @JsonProperty mappings and
 * @JsonIgnoreProperties work correctly.
 *
 * <p>No Spring context or Kafka broker is required. Tests run against
 * a plain Jackson {@link ObjectMapper} to isolate deserialization logic.
 */
class SseStreamApplicationTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	// =========================================================================
	// TransactionEvent — Deserialization
	// =========================================================================

	@Test
	void transactionEvent_deserializesAllFieldsFromPythonJson() throws Exception {
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

		assertEquals("abc-123", event.getTransactionId());
		assertEquals("2026-04-01T12:00:00Z", event.getTimestamp());
		assertEquals(1775217600000L, event.getEventEpochMs());
		assertEquals("u-1001", event.getUserId());
		assertEquals("acc-1001", event.getAccountId());
		assertEquals(250.50, event.getAmount());
		assertEquals("USD", event.getCurrency());
		assertEquals("m-456", event.getMerchantId());
		assertEquals("grocery", event.getMerchantCategory());
		assertEquals("credit_card", event.getPaymentMethod());
		assertEquals("online", event.getChannel());
		assertFalse(event.isCardPresent());
		assertEquals("d-7890", event.getDeviceId());
		assertEquals("android", event.getDeviceType());
		assertEquals("10.1.2.3", event.getIpAddress());
		assertEquals("SG", event.getCountry());
		assertEquals("Singapore", event.getCity());
		assertEquals(1.3521, event.getLatitude(), 0.0001);
		assertEquals(103.8198, event.getLongitude(), 0.0001);
	}

	@Test
	void transactionEvent_ignoresUnknownFields() throws Exception {
		// @JsonIgnoreProperties ensures forward compatibility when
		// the Python producer adds new fields in future versions.
		String json = """
			{
				"transaction_id": "abc-123",
				"user_id": "u-1001",
				"amount": 100.0,
				"some_future_field": "should_be_ignored",
				"another_new_field": 42
			}
			""";

		TransactionEvent event = objectMapper.readValue(json, TransactionEvent.class);
		assertEquals("u-1001", event.getUserId());
		assertEquals(100.0, event.getAmount());
	}

	@Test
	void transactionEvent_handlesMinimalJson() throws Exception {
		// Verifies that partially populated JSON doesn't throw.
		// Missing fields default to null (String), 0 (double/long), false (boolean).
		String json = """
			{
				"transaction_id": "minimal-001"
			}
			""";

		TransactionEvent event = objectMapper.readValue(json, TransactionEvent.class);
		assertEquals("minimal-001", event.getTransactionId());
		assertNull(event.getUserId());
		assertEquals(0.0, event.getAmount());
		assertFalse(event.isCardPresent());
	}

	@Test
	void transactionEvent_serializesToJsonWithSnakeCaseKeys() throws Exception {
		TransactionEvent event = new TransactionEvent();
		event.setTransactionId("txn-001");
		event.setUserId("u-2002");
		event.setAmount(99.99);

		String json = objectMapper.writeValueAsString(event);

		// Verify @JsonProperty produces snake_case keys in the output JSON
		assertTrue(json.contains("\"transaction_id\""));
		assertTrue(json.contains("\"user_id\""));
		assertTrue(json.contains("99.99"));
	}

	@Test
	void transactionEvent_gettersAndSettersWork() {
		TransactionEvent event = new TransactionEvent();
		event.setUserId("u-2002");
		event.setAmount(99.99);
		event.setCountry("US");
		event.setCardPresent(true);

		assertEquals("u-2002", event.getUserId());
		assertEquals(99.99, event.getAmount());
		assertEquals("US", event.getCountry());
		assertTrue(event.isCardPresent());
	}

	@Test
	void transactionEvent_toStringContainsKeyFields() {
		TransactionEvent event = new TransactionEvent();
		event.setTransactionId("txn-001");
		event.setUserId("u-1001");
		event.setAmount(500.0);
		event.setCountry("SG");

		String str = event.toString();
		assertTrue(str.contains("txn-001"));
		assertTrue(str.contains("u-1001"));
		assertTrue(str.contains("500.0"));
		assertTrue(str.contains("SG"));
	}

	// =========================================================================
	// AlertEvent — Deserialization
	// =========================================================================

	@Test
	void alertEvent_deserializesFromPythonJson() throws Exception {
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
					"distance_from_last_km": 9800.50,
					"window_seconds": 60
				},
				"severity": "high"
			}
			""";

		AlertEvent alert = objectMapper.readValue(json, AlertEvent.class);

		assertEquals("alert-abc-123", alert.getAlertId());
		assertEquals("2026-04-01T12:00:01Z", alert.getCreatedAt());
		assertEquals("high", alert.getSeverity());
		assertEquals(2, alert.getFraudReasons().size());
		assertTrue(alert.getFraudReasons().contains("huge_amount"));
		assertTrue(alert.getFraudReasons().contains("location_anomaly"));

		// Verify nested TransactionEvent deserialization
		assertNotNull(alert.getTransaction());
		assertEquals("abc-123", alert.getTransaction().getTransactionId());
		assertEquals("u-1001", alert.getTransaction().getUserId());
		assertEquals(7500.00, alert.getTransaction().getAmount());

		// Verify flexible detector context map
		Map<String, Object> ctx = alert.getDetectorContext();
		assertEquals(5, ((Number) ctx.get("recent_transaction_count_in_window")).intValue());
		assertEquals(9800.50, ((Number) ctx.get("distance_from_last_km")).doubleValue(), 0.01);
		assertEquals(60, ((Number) ctx.get("window_seconds")).intValue());
	}

	@Test
	void alertEvent_singleFraudReason() throws Exception {
		// Alerts can have a single reason (e.g. only huge_amount)
		String json = """
			{
				"alert_id": "alert-single",
				"fraud_reasons": ["high_frequency_transactions"],
				"severity": "medium",
				"transaction": {
					"transaction_id": "txn-single",
					"user_id": "u-3003",
					"amount": 200.0
				},
				"detector_context": {}
			}
			""";

		AlertEvent alert = objectMapper.readValue(json, AlertEvent.class);
		assertEquals(1, alert.getFraudReasons().size());
		assertEquals("high_frequency_transactions", alert.getFraudReasons().get(0));
		assertEquals("medium", alert.getSeverity());
		assertTrue(alert.getDetectorContext().isEmpty());
	}

	@Test
	void alertEvent_serializesToJsonWithSnakeCaseKeys() throws Exception {
		TransactionEvent txn = new TransactionEvent();
		txn.setTransactionId("txn-001");
		txn.setUserId("u-1001");

		AlertEvent alert = new AlertEvent();
		alert.setAlertId("alert-001");
		alert.setCreatedAt("2026-04-01T12:00:00Z");
		alert.setSeverity("high");
		alert.setFraudReasons(List.of("huge_amount"));
		alert.setDetectorContext(Map.of("distance_from_last_km", 500.0));
		alert.setTransaction(txn);

		String json = objectMapper.writeValueAsString(alert);

		assertTrue(json.contains("\"alert_id\""));
		assertTrue(json.contains("\"created_at\""));
		assertTrue(json.contains("\"fraud_reasons\""));
		assertTrue(json.contains("\"detector_context\""));
	}

	@Test
	void alertEvent_toStringContainsKeyFields() {
		TransactionEvent txn = new TransactionEvent();
		txn.setUserId("u-3003");

		AlertEvent alert = new AlertEvent();
		alert.setAlertId("alert-xyz");
		alert.setSeverity("medium");
		alert.setFraudReasons(List.of("high_frequency_transactions"));
		alert.setTransaction(txn);

		String str = alert.toString();
		assertTrue(str.contains("alert-xyz"));
		assertTrue(str.contains("u-3003"));
		assertTrue(str.contains("medium"));
	}

	@Test
	void alertEvent_toStringHandlesNullTransaction() {
		AlertEvent alert = new AlertEvent();
		alert.setAlertId("alert-null");

		String str = alert.toString();
		assertTrue(str.contains("null"));
	}
}
