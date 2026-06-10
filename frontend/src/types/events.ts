// =============================================================================
// events.ts — TypeScript interfaces for SSE event payloads.
//
// These interfaces mirror the Java DTOs in the SSE_stream service:
//   - TransactionEvent.java → TransactionEvent
//   - AlertEvent.java       → AlertEvent
//
// The SSE endpoint sends JSON with Python-style snake_case keys
// (e.g. "transaction_id", "user_id"). We keep the same naming here
// so JSON.parse() maps directly without any transformation layer.
//
// WHY snake_case IN TYPESCRIPT?
//   Normally TypeScript uses camelCase. But since these objects arrive
//   as raw JSON from the SSE stream and are only used for display
//   (never sent back to a server), it's cleaner to match the wire
//   format exactly rather than adding a mapping layer.
// =============================================================================

/**
 * A single financial transaction produced by the Python producer.
 *
 * Data flow:
 *   Python producer.py → Kafka "transactions" topic
 *     → SSE_stream KafkaConsumerService → SseController
 *     → Browser EventSource → this interface
 */
export interface TransactionEvent {
  /** UUID assigned by the producer at creation time. */
  transaction_id: string;

  /** ISO-8601 timestamp string (e.g. "2026-04-01T12:00:00Z"). */
  timestamp: string;

  /** Unix epoch milliseconds — useful for time-based calculations. */
  event_epoch_ms: number;

  /** Synthetic user identifier (e.g. "u-1001"). */
  user_id: string;

  /** Synthetic account identifier. */
  account_id: string;

  /** Transaction amount in the specified currency. */
  amount: number;

  /** ISO-4217 currency code (e.g. "USD", "SGD", "EUR"). */
  currency: string;

  /** Unique merchant identifier. */
  merchant_id: string;

  /** Category of the merchant (e.g. "grocery", "electronics", "travel"). */
  merchant_category: string;

  /** Payment method used (e.g. "credit_card", "debit_card", "mobile_wallet"). */
  payment_method: string;

  /** Channel through which the transaction was initiated (e.g. "online", "pos"). */
  channel: string;

  /** Whether a physical card was present during the transaction. */
  card_present: boolean;

  /** Device identifier (for online/mobile transactions). */
  device_id: string;

  /** Device type (e.g. "mobile", "desktop", "tablet"). */
  device_type: string;

  /** IP address of the device initiating the transaction. */
  ip_address: string;

  /** Country where the transaction occurred (ISO-3166 code). */
  country: string;

  /** City where the transaction occurred. */
  city: string;

  /** GPS latitude of the transaction location. */
  latitude: number;

  /** GPS longitude of the transaction location. */
  longitude: number;
}

/**
 * A fraud alert produced by the Python fraud detector.
 *
 * Data flow:
 *   Python fraud_detector.py → Kafka "fraud-alerts" topic
 *     → SSE_stream KafkaConsumerService → SseController
 *     → Browser EventSource → this interface
 *
 * Each alert wraps the offending transaction along with metadata
 * about why it was flagged.
 */
export interface AlertEvent {
  /** UUID assigned by the fraud detector. */
  alert_id: string;

  /** ISO-8601 timestamp of when the alert was created. */
  created_at: string;

  /** The transaction that triggered this fraud alert. */
  transaction: TransactionEvent;

  /**
   * List of fraud rule names that fired (e.g. "huge_amount", "location_anomaly").
   * Multiple rules can fire for the same transaction.
   */
  fraud_reasons: string[];

  /**
   * Flexible key-value context from the fraud detector.
   * May include fields like:
   *   - recent_transaction_count_in_window: number
   *   - distance_from_last_km: number
   *   - time_since_last_seconds: number
   *
   * Uses Record<string, unknown> because the Python side may add
   * arbitrary keys depending on which fraud rules triggered.
   */
  detector_context: Record<string, unknown>;

  /** Severity level: "high" for huge_amount alerts, "medium" otherwise. */
  severity: string;
}

/**
 * Possible states of an SSE connection.
 * Used by the custom hooks to communicate connection health to the UI.
 */
export type ConnectionStatus = "connected" | "disconnected" | "reconnecting";
