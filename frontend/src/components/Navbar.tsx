// =============================================================================
// Navbar.tsx — Top navigation bar for the fraud detection dashboard.
//
// Displays:
//   1. Application logo and title
//   2. Live connection status indicator (pulsing green dot when connected)
//   3. Real-time clock showing current time
//
// The connection indicator is crucial for operators — if the SSE stream
// disconnects (e.g., the sse-stream container crashes), the dot turns
// red immediately, giving instant visual feedback without needing to
// check the browser console.
// =============================================================================

import { useState, useEffect } from "react";
import type { ConnectionStatus } from "../types/events";

interface NavbarProps {
  /** Current SSE connection status — drives the indicator dot color. */
  connectionStatus: ConnectionStatus;
}

/**
 * Top navigation bar with branding, connection status, and live clock.
 *
 * The clock updates every second using a local setInterval. This is
 * intentionally not synced to the SSE stream — it should always tick
 * even when the stream is disconnected.
 */
export function Navbar({ connectionStatus }: NavbarProps) {
  const [time, setTime] = useState(new Date());

  // Update the clock every second
  useEffect(() => {
    const interval = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(interval);
  }, []);

  // Format time as HH:MM:SS with 12-hour format
  const formattedTime = time.toLocaleTimeString("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  });

  // Format date as "Mon, Jun 10 2026"
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
          {/* Shield icon representing fraud protection */}
          <svg
            width="28"
            height="28"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            <path d="M9 12l2 2 4-4" />
          </svg>
        </div>
        <div>
          <h1 className="navbar-title">Fraud Detection</h1>
          <span className="navbar-subtitle">Real-Time Monitoring Dashboard</span>
        </div>
      </div>

      {/* --- Right: Connection status and clock --- */}
      <div className="navbar-right">
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
