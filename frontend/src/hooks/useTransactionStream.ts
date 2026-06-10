// =============================================================================
// useTransactionStream.ts — Custom React hook for consuming the SSE
// transaction stream.
//
// HOW IT WORKS:
//   1. On mount, opens an EventSource connection to /api/sse/transactions
//   2. Each incoming SSE "data:" line is parsed as a TransactionEvent JSON
//   3. Events are pushed into a React state array (newest first)
//   4. The array is capped at MAX_EVENTS to prevent unbounded memory growth
//   5. If the connection drops, the hook auto-reconnects with backoff
//
// WHY EventSource (SSE) INSTEAD OF WebSocket?
//   SSE is simpler for our use case: we only need server→client streaming.
//   The browser's EventSource API handles reconnection automatically,
//   works through HTTP/2, and is natively supported by Spring WebFlux.
//   WebSocket would be overkill since we never send data back to the server.
//
// BACKPRESSURE NOTE:
//   The server-side SseController already drops events for slow clients.
//   On the browser side, we cap the array at 100 events. Between these two
//   mechanisms, memory is bounded on both ends of the pipeline.
// =============================================================================

import { useState, useEffect, useRef, useCallback } from "react";
import type { TransactionEvent, ConnectionStatus } from "../types/events";

/** Maximum number of transactions to keep in the browser's memory. */
const MAX_EVENTS = 100;

/**
 * The base URL for SSE endpoints.
 *
 * In development, Vite's proxy (configured in vite.config.ts) forwards
 * /api/* requests to the SSE_stream backend on port 8085.
 *
 * In production (Docker), nginx handles the proxying.
 */
const SSE_URL = "/api/sse/transactions";

/**
 * Custom hook that subscribes to the real-time transaction stream.
 *
 * @returns An object containing:
 *   - transactions: The latest transactions (newest first, max 100)
 *   - status: Current SSE connection status
 *   - totalCount: Total number of transactions received since page load
 */
export function useTransactionStream() {
  // --- State ---
  // transactions: the rolling window of recent events displayed in the feed
  const [transactions, setTransactions] = useState<TransactionEvent[]>([]);
  // status: drives the connection indicator dot in the Navbar
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  // totalCount: cumulative counter that never resets (used for stats)
  const [totalCount, setTotalCount] = useState(0);

  // --- Refs ---
  // eventSourceRef: holds the EventSource instance so we can close it on unmount
  const eventSourceRef = useRef<EventSource | null>(null);
  // reconnectTimeoutRef: holds the setTimeout ID for reconnection backoff
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // reconnectAttemptRef: tracks how many consecutive reconnect attempts we've made
  const reconnectAttemptRef = useRef(0);

  /**
   * Establishes the SSE connection.
   *
   * This is wrapped in useCallback so it can be called both on initial
   * mount and during reconnection without creating stale closures.
   */
  const connect = useCallback(() => {
    // Clean up any existing connection before opening a new one
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setStatus("reconnecting");

    const es = new EventSource(SSE_URL);
    eventSourceRef.current = es;

    // --- Connection opened successfully ---
    es.onopen = () => {
      setStatus("connected");
      // Reset the reconnect counter on successful connection
      reconnectAttemptRef.current = 0;
    };

    // --- New transaction event received ---
    // The SSE protocol sends each event as:
    //   data:{"transaction_id":"...","amount":250.50,...}\n\n
    // The browser parses this and fires onmessage with e.data = the JSON string.
    es.onmessage = (event: MessageEvent) => {
      try {
        const tx: TransactionEvent = JSON.parse(event.data);
        setTransactions((prev) => {
          // Prepend the new event (newest first) and cap at MAX_EVENTS.
          // slice() creates a new array reference, triggering React re-render.
          const updated = [tx, ...prev];
          return updated.length > MAX_EVENTS
            ? updated.slice(0, MAX_EVENTS)
            : updated;
        });
        setTotalCount((prev) => prev + 1);
      } catch {
        // Malformed JSON — log and skip. This can happen if the server
        // sends a heartbeat comment (lines starting with ":").
        console.warn("Failed to parse transaction event:", event.data);
      }
    };

    // --- Connection error (server down, network issue, etc.) ---
    // The browser's EventSource will fire this on any connection failure.
    // We implement exponential backoff to avoid hammering the server.
    es.onerror = () => {
      es.close();
      setStatus("disconnected");

      // Exponential backoff: 1s, 2s, 4s, 8s, max 30s
      const attempt = reconnectAttemptRef.current;
      const delay = Math.min(1000 * Math.pow(2, attempt), 30000);
      reconnectAttemptRef.current += 1;

      console.log(
        `SSE transaction stream disconnected. Reconnecting in ${delay}ms (attempt ${attempt + 1})...`
      );

      reconnectTimeoutRef.current = setTimeout(connect, delay);
    };
  }, []);

  // --- Effect: connect on mount, clean up on unmount ---
  useEffect(() => {
    connect();

    // Cleanup function: runs when the component unmounts or the effect re-runs.
    // This prevents memory leaks from orphaned EventSource connections.
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [connect]);

  return { transactions, status, totalCount };
}
