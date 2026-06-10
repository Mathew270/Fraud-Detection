// =============================================================================
// TransactionFeed.tsx — Live-scrolling table of recent transactions.
//
// This is the primary data visualization component. It displays the most
// recent 100 transactions in a reverse-chronological table with:
//   - Animated row insertion (new rows fade in from the top)
//   - Color-coded amounts (green for small, amber for medium, red for large)
//   - Country flag emojis for quick geographic scanning
//   - Merchant category badges
//
// PERFORMANCE CONSIDERATIONS:
//   With transactions arriving multiple times per second, React's virtual
//   DOM diffing does heavy lifting here. We use the transaction_id as the
//   key prop so React can efficiently identify which rows are new vs
//   which have shifted down. The parent hook caps the array at 100,
//   preventing DOM bloat.
//
// AMOUNT COLOR CODING:
//   - Green  (< $500)   — Normal consumer transactions
//   - Amber  ($500-$5000) — Medium transactions worth monitoring
//   - Red    (> $5000)  — Large transactions that often trigger fraud rules
//
// These thresholds match the fraud_detector.py "huge_amount" rule
// which flags transactions over $10,000.
// =============================================================================

import type { TransactionEvent } from "../types/events";

interface TransactionFeedProps {
  /** Array of recent transactions, newest first. */
  transactions: TransactionEvent[];
}

/**
 * Returns a CSS class name based on transaction amount.
 * Used to visually highlight high-value transactions.
 */
function getAmountClass(amount: number): string {
  if (amount >= 5000) return "amount-high";
  if (amount >= 500) return "amount-medium";
  return "amount-low";
}

/**
 * Formats an ISO-8601 timestamp into a human-readable time string.
 * Shows only the time portion (HH:MM:SS) since all transactions
 * are happening "now" on a live dashboard.
 */
function formatTime(timestamp: string): string {
  try {
    return new Date(timestamp).toLocaleTimeString("en-US", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    });
  } catch {
    return timestamp;
  }
}

/**
 * Maps a country code to its flag emoji.
 * Uses the regional indicator symbol technique:
 *   "US" → 🇺🇸, "SG" → 🇸🇬, etc.
 */
function countryToFlag(countryCode: string): string {
  if (!countryCode || countryCode.length !== 2) return "🌍";
  const codePoints = [...countryCode.toUpperCase()].map(
    (char) => 0x1f1e6 + char.charCodeAt(0) - 65
  );
  return String.fromCodePoint(...codePoints);
}

/**
 * Live transaction feed table.
 *
 * New rows appear at the top with a CSS fade-in animation.
 * The table uses a fixed layout for consistent column widths
 * regardless of content length.
 */
export function TransactionFeed({ transactions }: TransactionFeedProps) {
  return (
    <div className="panel transaction-panel" id="transaction-feed">
      {/* Panel header */}
      <div className="panel-header">
        <h2 className="panel-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
          </svg>
          Live Transactions
        </h2>
        <span className="panel-badge">{transactions.length} buffered</span>
      </div>

      {/* Scrollable table container */}
      <div className="table-container">
        <table className="transaction-table">
          <thead>
            <tr>
              <th>Time</th>
              <th>User</th>
              <th>Amount</th>
              <th>Category</th>
              <th>Location</th>
              <th>Method</th>
            </tr>
          </thead>
          <tbody>
            {transactions.length === 0 ? (
              <tr>
                <td colSpan={6} className="empty-state">
                  <div className="empty-icon">📡</div>
                  <div>Waiting for transactions...</div>
                  <div className="empty-hint">
                    Make sure Docker containers are running
                  </div>
                </td>
              </tr>
            ) : (
              transactions.map((tx) => (
                <tr key={tx.transaction_id} className="tx-row fade-in">
                  {/* Time — shows only HH:MM:SS */}
                  <td className="tx-time">{formatTime(tx.timestamp)}</td>

                  {/* User ID — truncated for compact display */}
                  <td className="tx-user" title={tx.user_id}>
                    {tx.user_id}
                  </td>

                  {/* Amount — color-coded by value */}
                  <td className={`tx-amount ${getAmountClass(tx.amount)}`}>
                    {tx.currency} {tx.amount.toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })}
                  </td>

                  {/* Merchant category badge */}
                  <td>
                    <span className="category-badge">{tx.merchant_category}</span>
                  </td>

                  {/* Location with flag emoji */}
                  <td className="tx-location">
                    {countryToFlag(tx.country)} {tx.city || tx.country}
                  </td>

                  {/* Payment method */}
                  <td className="tx-method">{tx.payment_method}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
