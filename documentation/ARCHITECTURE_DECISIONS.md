# Architecture Decision Log: Distributed Fraud Detection Pipeline

## 🚀 Project Overview

The **Distributed Fraud Detection Pipeline** is a real-time, event-driven system designed to simulate, ingest, and analyze massive volumes of financial transactions to detect anomalous behavior (like impossible travel speed or burst frequencies).

Instead of a single server trying to do everything, the workload is distributed across specialized, isolated microservices. 

> **Analogy:** Think of this system like a global airport security network. 
> - **The Producer:** The ticket counter generating passenger data.
> - **Apache Kafka:** The massive, high-speed conveyor belt moving the luggage.
> - **The Fraud Detector:** The X-ray scanner analyzing each bag in real-time as it passes by.
> - **Redis:** The centralized security database instantly remembering if this exact passenger just checked another bag in a different country 10 minutes ago.
> - **The Control Plane UI:** The air traffic control tower monitoring and scaling the entire operation.

---

## 🏗️ Core Design Decisions

### Decision 1: Separating Data from Logic (Single Responsibility)
**Context:** Initially, all the simulation data and transaction generation logic were hardcoded inside the `producer.py` script.

**The Decision:** Extract all static testing data into an external JSON file (`app/data/users.json`) and move the data-generation math into a helper module. 

**Justification:** The core responsibility of a Kafka Producer script should strictly be connecting to the broker, ensuring message delivery, and reporting metrics. By hardcoding business logic into the streaming worker, we violated the Single Responsibility Principle. Separating data generation from transmission made `producer.py` over 100 lines shorter, universally reusable, and much easier to unit test.

---

### Decision 2: Declarative Kubernetes vs. Imperative Subprocesses
**Context:** To scale the pipeline, the initial prototype used a local Python Dashboard that executed `subprocess.Popen()` side-by-side on the host machine.

**The Decision:** Abandon local subprocesses and re-architect the infrastructure to run entirely within a localized Kubernetes cluster (Minikube).

**Justification:** Spawning host-OS subprocesses is a brittle framework that fails to replicate a real production environment. It lacks native load balancing, automatic networking, and resilience. Moving to Kubernetes allows the system to utilize **Declarative Infrastructure**. Instead of writing complex loops to track Process IDs and handle crashes, the dashboard simply tells the Kubernetes API, *"I want 10 replicas,"* and the cluster handles the lifecycle.

---

### Decision 3: Polyglot Microservices (Python + Java Spring Boot)
**Context:** The project requires heavy data simulation/processing ("Workers") and a highly concurrent Enterprise Dashboard ("Control Plane").

**The Decision:** Adopt a **Polyglot Architecture**. The Workers remain strictly in Python, while the Control Plane Dashboard is built in Java Spring Boot.

**Justification:** Utilizing a single programming language across an entire stack often leads to compromising on the "right tool for the job." 
- **The Data Plane (Python):** Python is the industry standard for Data Science and Machine Learning. Building the Fraud Detector model and Kafka consumer/producer workers in Python perfectly mirrors how an enterprise integrates ML.
- **The Control Plane (Java):** Java Spring WebFlux is inherently designed for massive concurrency, non-blocking I/O, and enterprise networking. Furthermore, `Spring Kafka` provides incredibly resilient native handling for concurrent consumer groups. By using Java to orchestrate Kubernetes calls and serve Reactive HTML streams, the system achieves a textbook separation of concerns identical to massive tech enterprises (e.g., Netflix, Uber).

---

### Decision 4: Event-Driven UI (Observability Plane)
**Context:** The initial Dashboard UI streamed real-time logs to the user by attaching Background Daemon Threads directly to the terminal output (`stdout`) of the running Python scripts.

**The Decision:** Transition the UI to act as an asynchronous Kafka Consumer, entirely abandoning terminal log scraping.

**Justification:** While scraping terminal logs works fine for debugging, it is considered a massive anti-pattern in distributed systems because it fundamentally cannot scale. By treating the UI as a native event consumer—subscribing directly to the `transactions` and `fraud-alerts` Kafka topics—the dashboard flawlessly displays true, multiplexed data regardless of how many worker nodes exist.

---

## 🔒 Security & Orchestration Decisions

### Decision 5: The "Secure Bridge" (Backend as Kafka Proxy)
**Context:** A common architectural question is whether the React UI should connect directly to Kafka to display transactions.

**The Decision:** The React UI connects strictly to the **Spring Boot Backend (via SSE)**, which acts as a secure translator for the Kafka Broker.

**Justification:** 
- **Protocol Mismatch:** Browsers use HTTP/WebSockets; Kafka uses a custom TCP-based protocol.
- **Security:** Connecting from the UI would expose Kafka credentials to the public internet.
- **Scaling:** If 1,000 users open the UI, 1,000 Kafka connections would overwhelm the broker. The backend acts as a single multi-casting consumer, reducing load and improving reliability.

---

### Decision 6: Secure Orchestration (K8s RBAC & Least Privilege)
**Context:** The Spring Boot backend runs as a Pod inside the cluster with "power" to trigger orchestration (scaling pods).

**The Decision:** Implement **Role-Based Access Control (RBAC)** to strictly limit the Backend's power.

**Justification:** To avoid the security risk of a "God-mode" pod, the backend is assigned a specific **ServiceAccount** and a **Role**. This Role only grants permission to `patch` the `replicas` of specific worker deployments. It cannot delete databases, change networking, or touch system-critical components, successfully mirroring the **Principle of Least Privilege**.

---

### Decision 9: Full Stack Containerization (Environment Parity)
**Context:** Initially, the system relied on the user manually installing Python dependencies and running scripts in separate terminals, which led to "it works on my machine" inconsistencies.

**The Decision:** Fully containerize every component—including the producers, detectors, and the Java API—using **Docker Compose**.

**Justification:** Containerization ensures that the exact same versions of Python, Java, Kafka, and Redis are used across every environment (local dev, CI/CD, and production). By mapping the internal Kafka listener to `kafka:9092`, we eliminated networking bugs caused by mismatched host/container port mappings. The entire pipeline can now be cold-booted with a single command: `docker compose up`.

---

### Decision 10: Reactive SSE Proxy (Project Reactor)
**Context:** Delivering real-time Kafka events to a web browser requires a bridge that can handle high-frequency updates without blocking the server.

**The Decision:** Implement the `sse-stream` using **Spring WebFlux** and **Project Reactor Sinks**.

**Justification:** 
- **Efficiency:** Unlike traditional REST which is "pull-based," Server-Sent Events (SSE) allows the server to "push" data. 
- **Non-blocking:** WebFlux handles thousands of connections on a small thread pool. 
- **Multicasting:** By using `Sinks.many().multicast()`, the backend consumes a Kafka message *once* and broadcasts it to *all* connected browsers, significantly reducing the load on the Kafka broker compared to each browser being its own consumer.

---

### Decision 11: Gradle as Build Orchestrator
**Context:** Choosing between Maven and Gradle for managing the Java Spring Boot ecosystem.

**The Decision:** Adopt **Gradle** (Groovy DSL) as the standard build tool for the project.

**Justification:** Gradle offers superior performance through incremental builds and build caching, which is critical for rapid iteration in a microservices environment. Its flexible DSL makes it easier to manage polyglot deployments where different services might require custom build-stage logic (e.g., multi-stage Docker builds).

---

## 📈 Performance & Scaling Decisions

### Decision 12: Scaling Strategy (Single Redis Instance)
**Context:** Determining if a single Redis service is sufficient for multiple horizontal producers and detectors.

**The Decision:** Utilize a single Redis instance as the centralized state manager for this scale.

**Justification:** A single Redis instance can handle 100,000+ operations per second. At our planned scale (max 5-10 producers), the internal load is negligible. A single instance provides a "Single Source of Truth" without the complexity and networking overhead of a Redis Cluster.

---

### Decision 13: Distributed State vs. Shared Memory (No Local Locks)
**Context:** Managing high-speed counters and windowed transaction histories across multiple concurrent worker pods.

**The Decision:** Adopt a "Share-Nothing" architecture at the application level, delegating all state synchronization to Redis and Kafka.

**Justification:** Traditional multi-threading requires complex Mutex locks and semaphores to prevent data corruption in shared RAM. In a distributed system, this doesn't work across multiple servers. By moving all state to Redis (which is atomic) and using Kafka for ordering, we eliminated the need for any local locks or complex threading logic. This makes the system "Horizontally Scalable" by design—we can add 100 more workers without any risk of race conditions.

---

### Decision 14: DNS Service Discovery vs. Static Configuration (The Counter Reset Phenomenon)
**Context:** When scaling microservice replicas (such as the Python `producer` or `fraud-detector` workers) in a Docker Compose network, Prometheus initially relied on `static_configs` targeting `producer:8000`. This introduced a hidden observability anomaly: Docker's internal DNS load balancer round-robinned the scrape requests, serving a different replica IP on each cycle. The PromQL `rate()` function interpreted the wildly fluctuating counter values between scrapes as application crashes/restarts, mathematically stitching the deltas together to produce an artificial, misleading throughput spike.

**The Decision:** Transition the Prometheus scraping protocol for horizontally scaled services from `static_configs` to **DNS Service Discovery (`dns_sd_configs`)** querying "A" records.

**Justification:** 
- **True Observability:** Relying on static DNS round-robin scraping creates "blind spots," as only one random replica is monitored at any given scrape interval. 
- **Math Parity:** DNS service discovery dynamically queries the internal Docker DNS server to extract the unique IP addresses of *all* active container replicas, instructing Prometheus to spawn dedicated, concurrent scrape targets for each container. This completely eliminates the PromQL "Counter Reset" illusion and provides true, granular metrics reporting across the entire horizontally scaled cluster.
