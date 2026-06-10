// =============================================================================
// useStats.ts — Derives real-time statistics from the transaction and
// alert streams.
//
// HOW TPS (Transactions Per Second) IS CALCULATED:
//   We use a sliding window approach:
//   1. Every time a new transaction arrives, we record its timestamp
//      in a timestampBuffer (a plain array of epoch-ms values).
//   2. Every second, a setInterval callback runs and:
//      a. Removes timestamps older than WINDOW_SECONDS from the buffer
//      b. Counts remaining timestamps → that's the TPS
//
//   This gives us a smooth, accurate TPS that isn't affected by
//   bursty traffic. A 5-second window means TPS = (events in last 5s) / 5.
//
// WHY NOT JUST count / elapsed?
//   A simple total/elapsed average would be useless for a live dashboard.
//   If the system processed 10,000 transactions over 2 hours, the average
//   would be ~1.4 TPS even if the current rate is 50 TPS. The sliding
//   window reflects the *current* throughput, which is what operators need.
// =============================================================================

import { useState, useEffect, useRef, useMemo } from "react";

/** How many seconds of history to use for the TPS calculation. */
const WINDOW_SECONDS = 5;

/**
 * Custom hook that computes dashboard statistics from raw event counts.
 *
 * @param totalTransactions - Cumulative transaction count from useTransactionStream
 * @param totalAlerts - Cumulative alert count from useAlertStream
 * @returns Computed stats: tps, alertRate
 */
export function useStats(totalTransactions: number, totalAlerts: number) {
  // --- TPS State ---
  const [tps, setTps] = useState(0);

  // timestampBuffer stores the arrival time (Date.now()) of each transaction.
  // We use a ref instead of state because we don't want every push to trigger
  // a React re-render — the setInterval callback handles rendering via setTps.
  const timestampBufferRef = useRef<number[]>([]);
  const prevTotalRef = useRef(0);

  // --- Track new transactions ---
  // When totalTransactions changes, push a new timestamp into the buffer.
  // We compare with prevTotalRef to know how many NEW transactions arrived.
  useEffect(() => {
    const newCount = totalTransactions - prevTotalRef.current;
    if (newCount > 0) {
      const now = Date.now();
      // Push one timestamp per new transaction
      for (let i = 0; i < newCount; i++) {
        timestampBufferRef.current.push(now);
      }
      prevTotalRef.current = totalTransactions;
    }
  }, [totalTransactions]);

  // --- TPS calculation interval ---
  // Every second, prune old timestamps and compute TPS.
  useEffect(() => {
    const interval = setInterval(() => {
      const cutoff = Date.now() - WINDOW_SECONDS * 1000;
      // Remove timestamps older than the window
      timestampBufferRef.current = timestampBufferRef.current.filter(
        (t) => t >= cutoff
      );
      // TPS = events in window / window size
      setTps(timestampBufferRef.current.length / WINDOW_SECONDS);
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  // --- Alert Rate ---
  // Percentage of transactions that triggered a fraud alert.
  // useMemo avoids recalculating on every render.
  const alertRate = useMemo(() => {
    if (totalTransactions === 0) return 0;
    return (totalAlerts / totalTransactions) * 100;
  }, [totalTransactions, totalAlerts]);

  return { tps, alertRate };
}
