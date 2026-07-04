// =============================================================================
// App.tsx — Root component of the Fraud Detection Dashboard.
//
// This is the top-level "orchestrator" component. It:
//   1. Initialises the two SSE stream hooks (transactions + alerts)
//   2. Computes derived statistics (TPS, alert rate) from the raw data
//   3. Passes data down to each visual component via props
//
// COMPONENT HIERARCHY:
//   App
//   ├── Navbar              — Branding, live status, clock
//   ├── StatsBar            — 4 KPI metric cards
//   ├── Main Grid
//   │   ├── TransactionFeed — Scrolling table of recent transactions
//   │   └── AlertPanel      — Fraud alert cards with severity badges
//   └── ClusterControls     — Scaling buttons (connected via API Gateway)
//
// DATA FLOW:
//   The two SSE hooks (useTransactionStream, useAlertStream) each open
//   an independent EventSource connection to the sse-stream backend.
//   They maintain their own state arrays and expose them to this component.
//   useStats then derives computed values (TPS, alert rate) from the raw
//   counts without re-fetching any data.
//
//   SSE_stream:8085 ──→ useTransactionStream ──→ transactions[] ──→ TransactionFeed
//                                                                 ──→ StatsBar
//   SSE_stream:8085 ──→ useAlertStream       ──→ alerts[]       ──→ AlertPanel
//                                                                 ──→ StatsBar
// =============================================================================

import { Navbar } from "./components/Navbar";
import { StatsBar } from "./components/StatsBar";
import { TransactionFeed } from "./components/TransactionFeed";
import { AlertPanel } from "./components/AlertPanel";
import { ClusterControls } from "./components/ClusterControls";
import { SystemHealth } from "./components/SystemHealth";
import { PipelineAnimation } from "./components/PipelineAnimation";
import { useTransactionStream } from "./hooks/useTransactionStream";
import { useAlertStream } from "./hooks/useAlertStream";
import { useStats } from "./hooks/useStats";

function App() {
  // --- SSE Stream Hooks ---
  // Each hook manages its own EventSource connection and state.
  // They return: the data array, connection status, and cumulative count.
  const {
    transactions,
    status: txStatus,
    totalCount: totalTransactions,
  } = useTransactionStream();

  const { alerts, totalAlerts } = useAlertStream();

  // --- Derived Statistics ---
  // Computed from the raw event counts. TPS uses a 5-second sliding window.
  const { tps, alertRate } = useStats(totalTransactions, totalAlerts);

  return (
    <div className="app">
      {/* Top navigation bar — shows connection status */}
      <Navbar connectionStatus={txStatus} />

      {/* Main content area */}
      <main className="main-content">
        {/* KPI metric cards */}
        <StatsBar
          tps={tps}
          totalTransactions={totalTransactions}
          totalAlerts={totalAlerts}
          alertRate={alertRate}
        />

        {/* Two-column grid: transactions on left, alerts on right */}
        <div className="dashboard-grid">
          <TransactionFeed transactions={transactions} />
          <AlertPanel alerts={alerts} />
        </div>

        {/* Transaction pipeline visualisation — great for demos & onboarding */}
        <PipelineAnimation />

        {/* Cluster scaling + simulation controls */}
        <ClusterControls />

        {/* System-wide health overview for all Compose services */}
        <SystemHealth />
      </main>
    </div>
  );
}

export default App;
