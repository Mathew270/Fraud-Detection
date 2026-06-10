// =============================================================================
// useAlertStream.ts — Custom React hook for consuming the SSE fraud
// alert stream.
//
// This hook follows the exact same pattern as useTransactionStream.ts
// but connects to the /api/sse/alerts endpoint instead.
//
// ALERTS vs TRANSACTIONS:
//   - Transactions arrive at a high rate (many per second).
//   - Alerts are much rarer — they only fire when the fraud detector
//     flags a transaction. You might see 1 alert for every 20-50 transactions.
//   - We keep fewer alerts in memory (50 vs 100) since each alert contains
//     a full nested transaction object and is more expensive to store.
// =============================================================================

import { useState, useEffect, useRef, useCallback } from "react";
import type { AlertEvent, ConnectionStatus } from "../types/events";

/** Maximum number of alerts to keep in the browser's memory. */
const MAX_ALERTS = 50;

/** SSE endpoint for fraud alerts. */
const SSE_URL = "/api/sse/alerts";

/**
 * Custom hook that subscribes to the real-time fraud alert stream.
 *
 * @returns An object containing:
 *   - alerts: The latest fraud alerts (newest first, max 50)
 *   - status: Current SSE connection status
 *   - totalAlerts: Total number of alerts received since page load
 */
export function useAlertStream() {
  const [alerts, setAlerts] = useState<AlertEvent[]>([]);
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  const [totalAlerts, setTotalAlerts] = useState(0);

  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptRef = useRef(0);

  const connect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setStatus("reconnecting");
    const es = new EventSource(SSE_URL);
    eventSourceRef.current = es;

    es.onopen = () => {
      setStatus("connected");
      reconnectAttemptRef.current = 0;
    };

    es.onmessage = (event: MessageEvent) => {
      try {
        const alert: AlertEvent = JSON.parse(event.data);
        setAlerts((prev) => {
          const updated = [alert, ...prev];
          return updated.length > MAX_ALERTS
            ? updated.slice(0, MAX_ALERTS)
            : updated;
        });
        setTotalAlerts((prev) => prev + 1);
      } catch {
        console.warn("Failed to parse alert event:", event.data);
      }
    };

    es.onerror = () => {
      es.close();
      setStatus("disconnected");

      const attempt = reconnectAttemptRef.current;
      const delay = Math.min(1000 * Math.pow(2, attempt), 30000);
      reconnectAttemptRef.current += 1;

      console.log(
        `SSE alert stream disconnected. Reconnecting in ${delay}ms (attempt ${attempt + 1})...`
      );

      reconnectTimeoutRef.current = setTimeout(connect, delay);
    };
  }, []);

  useEffect(() => {
    connect();
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [connect]);

  return { alerts, status, totalAlerts };
}
