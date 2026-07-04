// =============================================================================
// Navbar.tsx — Top navigation bar for the fraud detection dashboard.
//
// Displays:
//   1. Application logo and title
//   2. Grafana dashboard shortcut links
//   3. Live connection status indicator (pulsing green dot when connected)
//   4. Real-time clock showing current time
// =============================================================================

import { useState, useEffect } from "react";
import type { ConnectionStatus } from "../types/events";

interface NavbarProps {
  /** Current SSE connection status — drives the indicator dot color. */
  connectionStatus: ConnectionStatus;
}

/**
 * Top navigation bar with branding, Grafana links, connection status, and live clock.
 */
export function Navbar({ connectionStatus }: NavbarProps) {
  const [time, setTime] = useState(new Date());

  // Update the clock every second
  useEffect(() => {
    const interval = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(interval);
  }, []);

  const formattedTime = time.toLocaleTimeString("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  });

  const formattedDate = time.toLocaleDateString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  return (
    <nav className="navbar" id="main-navbar">
      {/* --- Left: Logo and title --- */}
      <div className="navbar-brand">
        <div className="navbar-logo">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            <path d="M9 12l2 2 4-4" />
          </svg>
        </div>
        <div>
          <h1 className="navbar-title">Fraud Detection</h1>
          <span className="navbar-subtitle">Real-Time Monitoring Dashboard</span>
        </div>
      </div>

      {/* --- Right: Grafana links, connection status, clock --- */}
      <div className="navbar-right">

        {/* Grafana shortcut links */}
        <div className="grafana-links" id="grafana-links">
          <a
            id="grafana-backend-link"
            className="grafana-link"
            href="http://localhost:3000/d/backend-ops"
            target="_blank"
            rel="noopener noreferrer"
            title="Open Backend Operations dashboard in Grafana"
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
            </svg>
            Backend Ops
          </a>
          <a
            id="grafana-txn-link"
            className="grafana-link"
            href="http://localhost:3000/d/transaction-analytics"
            target="_blank"
            rel="noopener noreferrer"
            title="Open Transaction Analytics dashboard in Grafana"
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="18" y1="20" x2="18" y2="10" />
              <line x1="12" y1="20" x2="12" y2="4"  />
              <line x1="6"  y1="20" x2="6"  y2="14" />
            </svg>
            Transactions
          </a>
        </div>

        {/* Connection status indicator */}
        <div className="connection-status" id="connection-indicator">
          <span
            className={`status-dot ${connectionStatus}`}
            title={`SSE Stream: ${connectionStatus}`}
          />
          <span className="status-text">
            {connectionStatus === "connected"
              ? "Live"
              : connectionStatus === "reconnecting"
              ? "Reconnecting..."
              : "Disconnected"}
          </span>
        </div>

        {/* Live clock */}
        <div className="navbar-clock" id="live-clock">
          <span className="clock-time">{formattedTime}</span>
          <span className="clock-date">{formattedDate}</span>
        </div>
      </div>
    </nav>
  );
}
