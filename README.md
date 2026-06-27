# 🛡️ Distributed Fraud Detection Pipeline

An enterprise-grade, real-time, event-driven fraud detection system designed to ingest, analyze, and flag anomalous financial transactions at scale.

## 🏗️ Architecture & Tech Stack

This project utilizes a **Polyglot Microservices** architecture to separate the data-plane workers from the control-plane management API:

- **Ingestion & Simulation (Python):** Horizontally scalable producer scripts in the `/workers` directory that simulate realistic transaction traffic.
- **Inference & Detection (Python):** Stateless consumer nodes that evaluate transactions against Redis-backed business rules (impossible travel, burst frequency).
- **Message Broker (Apache Kafka):** The distributed event bus that decouples producers from processors.
- **State Management (Redis):** High-speed, centralized store tracking user sliding windows across isolated detector nodes.
- **API Gateway (Java Spring Cloud Gateway):** The single entry point in `/api_gateway` (port `8090`) that routes requests to appropriate backends and translates REST to gRPC for the control plane.
- **The Control Plane (Java Spring Boot):** A reactive backend in the `/sse_stream` directory that streams real-time Kafka events to the frontend via Server-Sent Events (SSE).
- **Cluster Controller (Java Spring Boot + gRPC):** A control plane service in `/cluster_controller` (port `9095`) that orchestrates running container scales.
- **Dashboard UI (React + TypeScript):** A sleek, real-time control and visualization dashboard in `/frontend` served by nginx on port `3001`.
- **Monitoring (Prometheus & Grafana):** Automated metrics collection and visualization for end-to-end throughput observability.

For an extensive deep dive into *why* these specific technologies were chosen, please read the [Architecture Decision Record (ADR)](ARCHITECTURE_DECISIONS.md).

For detailed technical documentation on every service and component, see the [📖 `/documentation` directory](documentation/README.md).


## 📋 Prerequisites

To run the entire pipeline locally, you only need:
- **Docker Desktop** (with at least 4GB of RAM allocated for Kafka/Java)
- **Git**

## 🚀 Quick Start (Full Stack)

The entire pipeline—including the infrastructure, workers, gateway, and the UI—is fully containerized. You can boot the complete system with a single command:

1. **Clone the repository**
2. **Start the containers**
   ```bash
   docker compose up --build
   ```
3. **Open the Dashboard UI**
   Once the containers are healthy, open the dashboard in your browser:
   - Dashboard UI: [http://localhost:3001](http://localhost:3001)
   - Live Transactions Stream (via Gateway): [http://localhost:8090/api/sse/transactions](http://localhost:8090/api/sse/transactions)
   - Live Fraud Alerts Stream (via Gateway): [http://localhost:8090/api/sse/alerts](http://localhost:8090/api/sse/alerts)

## 📊 Observability & Debugging

Monitor the health and data flow using these local web UIs:
- **Dashboard UI:** [http://localhost:3001](http://localhost:3001)
- **API Gateway:** [http://localhost:8090](http://localhost:8090)
- **Kafka UI:** [http://localhost:8080](http://localhost:8080)
- **Redis Insight:** [http://localhost:8001](http://localhost:8001)
- **Prometheus:** [http://localhost:9090](http://localhost:9090)
- **Grafana Dashboards:** [http://localhost:3000](http://localhost:3000) (Login: `admin` / `admin`)

---
**Status:** **Core Pipeline & Gateway Integration Complete (Phase 2).** Currently preparing final Kubernetes deployment manifests (Phase 3).
