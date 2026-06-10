# Dashboard UI: Service Documentation

The `frontend/` directory contains a **React + TypeScript** single-page application that provides a real-time monitoring dashboard for the fraud detection pipeline. It connects to the SSE Stream backend (`SSE_stream`) to display live transaction data and fraud alerts as they flow through the system.

## Architecture Overview

```mermaid
graph LR
    subgraph "Docker Network"
        Producer["Python Producer"]
        Detector["Python Fraud Detector"]
        Kafka["Kafka"]
        SSE["SSE Stream :8085"]
    end

    subgraph "Browser"
        React["React Dashboard :5173"]
    end

    Producer -->|transactions| Kafka
    Detector -->|fraud-alerts| Kafka
    Kafka --> SSE
    SSE -->|"EventSource /api/sse/transactions"| React
    SSE -->|"EventSource /api/sse/alerts"| React
```

## Technology Stack

| Technology | Purpose |
|------------|---------|
| **React 19** | Component-based UI framework |
| **TypeScript** | Type safety — catches bugs at compile time instead of runtime |
| **Vite** | Dev server with hot module reload + production bundler |
| **Vanilla CSS** | Design system with CSS custom properties (no Tailwind dependency) |
| **nginx** | Production static file server + reverse proxy (Docker only) |

## Component Architecture

```
App.tsx                          ← Root orchestrator
├── Navbar.tsx                   ← Logo, connection indicator, live clock
├── StatsBar.tsx                 ← 4 KPI metric cards (TPS, totals, rates)
├── Dashboard Grid (CSS Grid)
│   ├── TransactionFeed.tsx      ← Scrolling table of recent transactions
│   └── AlertPanel.tsx           ← Fraud alert cards with severity badges
└── ClusterControls.tsx          ← Scaling buttons (disabled until Phase 2)
```

### Data Flow

```
SSE_stream:8085 ──→ useTransactionStream hook ──→ transactions[] ──→ TransactionFeed
                                                                   ──→ StatsBar (TPS, total)
SSE_stream:8085 ──→ useAlertStream hook       ──→ alerts[]        ──→ AlertPanel
                                                                   ──→ StatsBar (alerts, rate)
```

## Custom Hooks

### `useTransactionStream`
Opens an `EventSource` connection to `/api/sse/transactions`. Maintains a rolling buffer of the 100 most recent transactions. Auto-reconnects with exponential backoff (1s → 2s → 4s → ... → 30s max) if the connection drops.

### `useAlertStream`
Same pattern as above, but connects to `/api/sse/alerts` and maintains up to 50 alerts.

### `useStats`
Derives computed statistics from the raw event counts:
- **TPS (Transactions Per Second):** Uses a 5-second sliding window. Timestamps of recent events are stored in a buffer, and a 1-second interval prunes old entries and divides the count by 5.
- **Alert Rate:** Simple percentage: `(totalAlerts / totalTransactions) * 100`.

## How SSE Works in the Browser

Server-Sent Events (SSE) is a simple, browser-native protocol for server-to-client streaming:

1. The browser opens a long-lived HTTP GET request to the SSE endpoint.
2. The server keeps the connection open and sends events in this format:
   ```
   data:{"transaction_id":"abc","amount":250.50,...}\n\n
   ```
3. The browser's `EventSource` API parses each `data:` line and fires an `onmessage` callback.
4. If the connection drops, `EventSource` automatically reconnects (we add our own exponential backoff on top).

**Why SSE instead of WebSocket?**
We only need server → client streaming (no data goes back to the server). SSE is simpler, works through HTTP proxies and load balancers without special configuration, and is natively supported by Spring WebFlux. WebSocket would be overkill.

## Design System

The CSS design system lives in `src/index.css` and uses CSS custom properties (variables) for theming:

| Token | Value | Usage |
|-------|-------|-------|
| `--bg-deepest` | `#0a0e1a` | Page background |
| `--bg-primary` | `#111827` | Panel backgrounds |
| `--accent-blue` | `#3b82f6` | Interactive elements |
| `--accent-emerald` | `#10b981` | Success states, low amounts |
| `--accent-amber` | `#f59e0b` | Warnings, medium severity |
| `--accent-red` | `#ef4444` | Danger, high severity |

Key visual features:
- **Glassmorphism panels:** `backdrop-filter: blur()` with subtle borders
- **Fade-in animations:** New table rows and alert cards slide in smoothly
- **Pulsing connection dot:** Green when connected, red when disconnected
- **Color-coded amounts:** Green (< $500), amber ($500-$5000), red (> $5000)

## Running Locally

### Prerequisites
- Node.js 18+ (for the Vite dev server)
- Docker Desktop (for the backend services)

### Development Mode (with hot reload)

```bash
# 1. Start the backend services
docker compose up -d

# 2. Stop the Docker version of sse-stream (to avoid port conflict)
docker compose stop sse-stream

# 3. Start the SSE stream locally (in a separate terminal)
cd SSE_stream && KAFKA_BOOTSTRAP_SERVERS="localhost:9094" ./gradlew bootRun

# 4. Start the React dev server (in another terminal)
cd frontend && npm run dev
```

Open http://localhost:5173 — you should see live transactions streaming in.

**How the proxy works in dev mode:**
Vite's dev server proxies `/api/*` requests to `http://localhost:8085` (configured in `vite.config.ts`). This avoids CORS errors since the browser thinks all requests go to `localhost:5173`.

### Production Mode (via Docker)

```bash
# Build and start everything
docker compose up -d --build

# The dashboard is available at:
# http://localhost:3001
```

In production, nginx inside the `dashboard-ui` container handles the proxying. Requests to `/api/*` are forwarded to the `sse-stream` container on the Docker network.

## Port Map

| Port | Service | Access |
|------|---------|--------|
| `5173` | Vite dev server | Dev only — `npm run dev` |
| `3001` | nginx (Docker) | Production — `docker compose up` |
| `8085` | SSE Stream API | Backend (proxied, not accessed directly by the UI) |

## Future: API Gateway Integration (Phase 2)

The `ClusterControls` component is currently disabled because the cluster controller only speaks gRPC, which browsers cannot call directly. When the API Gateway is built:

1. The Gateway will expose REST endpoints like `POST /api/cluster/scale`
2. It will translate REST calls into gRPC RPCs for the cluster controller
3. We will update `ClusterControls.tsx` to call these REST endpoints
4. The Vite proxy and nginx config will be updated to route `/api/cluster/*` to the Gateway

The UI layout is already finalized — enabling cluster controls will be a code change in one component, not a redesign.
