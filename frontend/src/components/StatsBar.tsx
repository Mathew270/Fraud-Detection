// =============================================================================
// StatsBar.tsx — Row of metric cards showing real-time dashboard KPIs.
//
// Displays four glassmorphism cards:
//   1. TPS (Transactions Per Second) — computed via sliding window
//   2. Total Transactions — cumulative count since page load
//   3. Fraud Alerts — cumulative alert count since page load
//   4. Alert Rate — percentage of transactions flagged as fraud
//
// Each card has:
//   - An icon (SVG) for quick visual identification
//   - A large animated number that smoothly updates
//   - A descriptive label
//
// ANIMATION:
//   Numbers use CSS transitions to smoothly animate value changes.
//   This prevents the "flickering" effect that raw number updates cause
//   on fast-moving dashboards.
// =============================================================================

interface StatsBarProps {
  /** Current transactions per second (from useStats). */
  tps: number;
  /** Total transactions received since page load. */
  totalTransactions: number;
  /** Total fraud alerts received since page load. */
  totalAlerts: number;
  /** Percentage of transactions flagged as fraud. */
  alertRate: number;
}

/**
 * Horizontal bar of four KPI metric cards.
 *
 * These cards give operators an at-a-glance view of system health.
 * A sudden TPS drop might indicate a producer failure, while a
 * spiking alert rate could mean the fraud rules need tuning.
 */
export function StatsBar({
  tps,
  totalTransactions,
  totalAlerts,
  alertRate,
}: StatsBarProps) {
  return (
    <div className="stats-bar" id="stats-bar">
      {/* --- TPS Card --- */}
      <div className="stat-card" id="stat-tps">
        <div className="stat-icon stat-icon-tps">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
          </svg>
        </div>
        <div className="stat-content">
          <span className="stat-value">{tps.toFixed(1)}</span>
          <span className="stat-label">TPS</span>
        </div>
      </div>

      {/* --- Total Transactions Card --- */}
      <div className="stat-card" id="stat-total-transactions">
        <div className="stat-icon stat-icon-transactions">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
            <line x1="1" y1="10" x2="23" y2="10" />
          </svg>
        </div>
        <div className="stat-content">
          <span className="stat-value">{totalTransactions.toLocaleString()}</span>
          <span className="stat-label">Transactions</span>
        </div>
      </div>

      {/* --- Fraud Alerts Card --- */}
      <div className="stat-card stat-card-alert" id="stat-total-alerts">
        <div className="stat-icon stat-icon-alerts">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>
        <div className="stat-content">
          <span className="stat-value">{totalAlerts.toLocaleString()}</span>
          <span className="stat-label">Fraud Alerts</span>
        </div>
      </div>

      {/* --- Alert Rate Card --- */}
      <div className="stat-card" id="stat-alert-rate">
        <div className="stat-icon stat-icon-rate">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 6v6l4 2" />
          </svg>
        </div>
        <div className="stat-content">
          <span className="stat-value">{alertRate.toFixed(2)}%</span>
          <span className="stat-label">Alert Rate</span>
        </div>
      </div>
    </div>
  );
}
