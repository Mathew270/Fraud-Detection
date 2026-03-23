# 🛡️ Distributed Fraud Detection Pipeline

An enterprise-grade, real-time, event-driven fraud detection system designed to ingest, analyze, and flag anomalous financial transactions at scale.

## 🏗️ Architecture & Tech Stack

This project utilizes a **Polyglot Microservices** architecture to separate the data plane from the highly-concurrent management plane:

- **Ingestion & Simulation (Python):** Horizontally scalable producer scripts simulating massive bursts of realistic transaction traffic.
- **Message Broker (Apache Kafka):** Serves as the distributed event bus and synchronization lock, perfectly decoupling the producers from the processors.
- **State Management (Redis):** Provides high-speed, centralized, in-memory caching to track real-time user sliding windows (e.g., velocity checks) across all isolated detector nodes.
- **Inference & Detection (Python):** Stateless consumer pods that evaluate transactions against business rules (impossible travel speed, high-frequency bursts).
- **Monitoring (Prometheus & Grafana):** Automated metrics scraping and visualization to ensure robust throughput observability.
- **The Control Plane (Java Spring Boot):** *(In Development)* An enterprise dashboard utilizing Spring WebFlux and Spring Kafka to orchestrate Kubernetes worker scaling and push real-time Server-Sent Events (SSE) to the UI.

For an extensive deep dive into *why* these specific technologies and patterns were chosen (e.g., swapping Subprocesses for declarative Kubernetes), please read the [Architecture Decision Record (ADR)](ARCHITECTURE_DECISIONS.md).

## 📋 Prerequisites

To run this project locally, you will need:
- Docker & Docker Compose
- Python 3.12+ (for local worker testing)
- Minikube or Docker Desktop (for Kubernetes orchestration)
- JDK 17+ (for the upcoming Spring Boot Control Plane)

## 🚀 Quick Start (Local Prototype Mode)

Currently, the project is migrating towards a fully Kubernetes-native deployment. You can test the foundational Kafka/Redis pipeline locally:

1. **Spin up the Infrastructure (Kafka, Redis, Prometheus, Grafana)**
   ```bash
   docker-compose up -d
   ```
2. **Install Python Dependencies**
   ```bash
   python -m venv .venv
   source .venv/bin/activate  # Or .venv\Scripts\activate on Windows
   pip install -r app/requirements.txt
   ```
3. **Run the Simulators**
   Open separate terminals to run each component:
   ```bash
   # Terminal 1: Watch for triggered alerts
   python app/alert_consumer.py
   
   # Terminal 2: Run the fraud detector stream processor
   python app/fraud_detector.py
   
   # Terminal 3: Start the massive transaction generator
   python app/producer.py
   ```

## 📊 Observability

Once the pipeline is running, you can monitor the health and throughput using the local web UIs:
- **Kafka UI:** [http://localhost:8080](http://localhost:8080)
- **Redis Commander:** [http://localhost:8081](http://localhost:8081)
- **Prometheus:** [http://localhost:9090](http://localhost:9090)
- **Grafana Live Dashboards:** [http://localhost:3000](http://localhost:3000) (Login: `admin` / `admin`)

---
**Status:** Actively migrating from local scripts to Kubernetes Pod scheduling and developing the Java Spring Boot enterprise interface.
