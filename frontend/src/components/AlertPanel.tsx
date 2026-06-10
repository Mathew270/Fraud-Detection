// =============================================================================
// AlertPanel.tsx — Fraud alert feed with severity badges and detailed context.
//
// Each alert card shows:
//   - Severity badge (HIGH = red pulsing, MEDIUM = amber)
//   - Alert ID and timestamp
//   - Fraud reasons as clickable tags
//   - The offending transaction's key details
//   - Detector context (distance from last transaction, recent tx count, etc.)
//
// WHY CARDS INSTEAD OF A TABLE?
//   Alerts are rare but information-dense events. Each alert contains:
//     - 5+ fraud reason tags
//     - A nested transaction with 18+ fields
//     - Variable-length detector context
//   A table row can't accommodate this complexity. Cards allow flexible
//   vertical layout where each section (reasons, transaction, context)
//   gets its own visual area.
//
// SEVERITY LEVELS (from fraud_detector.py):
//   - "high"   — Triggered by the "huge_amount" rule ($10,000+)
//   - "medium" — All other fraud rules (velocity, location anomaly, etc.)
// =============================================================================

import type { AlertEvent } from "../types/events";

interface AlertPanelProps {
  /** Array of recent fraud alerts, newest first. */
  alerts: AlertEvent[];
}

/**
 * Maps fraud reason codes (from Python) to human-readable labels.
 * The Python fraud_detector.py uses short snake_case codes like
 * "huge_amount" or "rapid_succession". This map translates them
 * into labels that non-technical operators can understand.
 */
const REASON_LABELS: Record<string, string> = {
  huge_amount: "💰 Huge Amount",
  rapid_succession: "⚡ Rapid Succession",
  location_anomaly: "🌍 Location Anomaly",
  velocity_spike: "📈 Velocity Spike",
  unusual_time: "🕐 Unusual Time",
  new_device: "📱 New Device",
};

/**
 * Formats detector context keys from snake_case to Title Case.
 * e.g. "distance_from_last_km" → "Distance From Last Km"
 */
function formatContextKey(key: string): string {
  return key
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

/**
 * Formats detector context values for display.
 * Numbers get formatted with locale-appropriate separators.
 * Booleans get checkmarks/crosses. Everything else stays as-is.
 */
function formatContextValue(value: unknown): string {
  if (typeof value === "number") {
    return value.toLocaleString(undefined, { maximumFractionDigits: 2 });
  }
  if (typeof value === "boolean") {
    return value ? "✅ Yes" : "❌ No";
  }
  return String(value);
}

/**
 * Fraud alert card feed.
 *
 * Alerts appear as stacked cards with the newest at the top.
 * High-severity alerts get a pulsing red left border to draw
 * operator attention immediately.
 */
export function AlertPanel({ alerts }: AlertPanelProps) {
  return (
    <div className="panel alert-panel" id="alert-panel">
      {/* Panel header */}
      <div className="panel-header">
        <h2 className="panel-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
          Fraud Alerts
        </h2>
        <span className="panel-badge panel-badge-alert">{alerts.length} recent</span>
      </div>

      {/* Scrollable alert container */}
      <div className="alert-container">
        {alerts.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">🛡️</div>
            <div>No fraud alerts yet</div>
            <div className="empty-hint">
              Alerts appear when the detector flags suspicious activity
            </div>
          </div>
        ) : (
          alerts.map((alert) => (
            <div
              key={alert.alert_id}
              className={`alert-card fade-in severity-${alert.severity}`}
            >
              {/* --- Alert header: severity badge + timestamp --- */}
              <div className="alert-header">
                <span className={`severity-badge severity-${alert.severity}`}>
                  {alert.severity === "high" ? "🔴 HIGH" : "🟡 MEDIUM"}
                </span>
                <span className="alert-time">
                  {new Date(alert.created_at).toLocaleTimeString("en-US", {
                    hour: "2-digit",
                    minute: "2-digit",
                    second: "2-digit",
                    hour12: false,
                  })}
                </span>
              </div>

              {/* --- Fraud reasons tags --- */}
              <div className="alert-reasons">
                {alert.fraud_reasons.map((reason) => (
                  <span key={reason} className="reason-tag" title={reason}>
                    {REASON_LABELS[reason] || reason}
                  </span>
                ))}
              </div>

              {/* --- Offending transaction summary --- */}
              {alert.transaction && (
                <div className="alert-transaction">
                  <div className="alert-tx-row">
                    <span className="alert-tx-label">User:</span>
                    <span>{alert.transaction.user_id}</span>
                  </div>
                  <div className="alert-tx-row">
                    <span className="alert-tx-label">Amount:</span>
                    <span className="alert-tx-amount">
                      {alert.transaction.currency}{" "}
                      {alert.transaction.amount.toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}
                    </span>
                  </div>
                  <div className="alert-tx-row">
                    <span className="alert-tx-label">Location:</span>
                    <span>
                      {alert.transaction.city}, {alert.transaction.country}
                    </span>
                  </div>
                  <div className="alert-tx-row">
                    <span className="alert-tx-label">Category:</span>
                    <span>{alert.transaction.merchant_category}</span>
                  </div>
                </div>
              )}

              {/* --- Detector context (variable key-value pairs) --- */}
              {alert.detector_context &&
                Object.keys(alert.detector_context).length > 0 && (
                  <div className="alert-context">
                    <span className="context-title">Detector Context</span>
                    {Object.entries(alert.detector_context).map(
                      ([key, value]) => (
                        <div key={key} className="context-row">
                          <span className="context-key">
                            {formatContextKey(key)}:
                          </span>
                          <span className="context-value">
                            {formatContextValue(value)}
                          </span>
                        </div>
                      )
                    )}
                  </div>
                )}

              {/* --- Alert ID footer --- */}
              <div className="alert-footer">
                <span className="alert-id" title={alert.alert_id}>
                  ID: {alert.alert_id.slice(0, 8)}...
                </span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
