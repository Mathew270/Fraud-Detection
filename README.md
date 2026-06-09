# 🛡️ Distributed Fraud Detection Pipeline

An enterprise-grade, real-time, event-driven fraud detection system designed to ingest, analyze, and flag anomalous financial transactions at scale.

## 🏗️ Architecture & Tech Stack

This project utilizes a **Polyglot Microservices** architecture to separate the data-plane workers from the control-plane management API:

- **Ingestion & Simulation (Python):** Horizontally scalable producer scripts in the `/workers` directory that simulate realistic transaction traffic.
- **Inference & Detection (Python):** Stateless consumer nodes that evaluate transactions against Redis-backed business rules (impossible travel, burst frequency).
- **Message Broker (Apache Kafka):** The distributed event bus that decouples producers from processors.
- **State Management (Redis):** High-speed, centralized store tracking user sliding windows across isolated detector nodes.
- **The Control Plane (Java Spring Boot):** A reactive backend in the `/dashboard_api` directory using Spring WebFlux to stream real-time Kafka events to the frontend via Server-Sent Events (SSE).
- **Monitoring (Prometheus & Grafana):** Automated metrics collection and visualization for end-to-end throughput observability.

For an extensive deep dive into *why* these specific technologies were chosen, please read the [Architecture Decision Record (ADR)](ARCHITECTURE_DECISIONS.md).

For detailed technical documentation on every service and component, see the [📖 `/documentation` directory](documentation/README.md).


## 📋 Prerequisites

To run the entire pipeline locally, you only need:
- **Docker Desktop** (with at least 4GB of RAM allocated for Kafka/Java)
- **Git**

## 🚀 Quick Start (Full Stack)

The entire pipeline—including the infrastructure, workers, and the API—is fully containerized. You can boot the complete system with a single command:

1. **Clone the repository**
2. **Start the containers**
   ```bash
   docker compose up --build
   ```
3. **Verify the Stream**
   Once the containers are healthy, you can view the live SSE stream directly in your browser:
   - Transactions: [http://localhost:8085/api/stream/transactions](http://localhost:8085/api/stream/transactions)
   - Fraud Alerts: [http://localhost:8085/api/stream/alerts](http://localhost:8085/api/stream/alerts)

## 📊 Observability & Debugging

Monitor the health and data flow using these local web UIs:
- **Dashboard API (SSE):** [http://localhost:8085](http://localhost:8085)
- **Kafka UI:** [http://localhost:8080](http://localhost:8080)
- **Redis Insight:** [http://localhost:8001](http://localhost:8001)
- **Prometheus:** [http://localhost:9090](http://localhost:9090)
- **Grafana Dashboards:** [http://localhost:3000](http://localhost:3000) (Login: `admin` / `admin`)

---
**Status:** **Core Pipeline Complete.** Currently expanding the React frontend in `/dashboard-ui` and implementing final Kubernetes deployment manifests.
