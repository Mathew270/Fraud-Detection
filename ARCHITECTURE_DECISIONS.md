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

## Decision 1: Separating Data from Logic (Single Responsibility)

**Context:**  
Initially, all the simulation data (the list of users, merchant categories, and anomaly locations) was hardcoded directly inside the `producer.py` script. 

**The Decision:**  
Extract all static testing data into an external JSON file (`app/data/users.json`) and move the data-generation math into a helper module. 

**Justification:**  
The core responsibility of a Kafka Producer script should strictly be connecting to the broker, ensuring message delivery, and reporting metrics. By hardcoding business logic into the streaming worker, we violated the Single Responsibility Principle. Separating data generation from transmission made `producer.py` over 100 lines shorter, universally reusable, and much easier to unit test.



---

## Decision 2: Declarative Kubernetes vs. Imperative Subprocesses

**Context:**  
To scale the pipeline and simulate heavy traffic, the initial prototype used a local Python Dashboard that executed `subprocess.Popen()` in a loop to spin up multiple instances of the producer script side-by-side on the host machine.

**The Decision:**  
Abandon local subprocesses and re-architect the infrastructure to run entirely within a localized Kubernetes cluster (Minikube).

**Justification:**  
Spawning host-OS subprocesses is a brittle framework that fails to replicate a real production environment. It lacks native load balancing, automatic networking, and resilience (if a script crashes, it stays dead). 

Moving to Kubernetes allows the system to utilize **Declarative Infrastructure**. Instead of writing complex loops to track Process IDs and handle crashes, the dashboard simply tells the Kubernetes API, *"I want 10 replicas,"* and the cluster automatically provisions, network-isolates, and sustains the Docker containers.



---

## Decision 3: Event-Driven UI (Observability Plane)

**Context:**  
The initial Dashboard UI streamed real-time logs to the user by attaching Background Daemon Threads directly to the terminal output (`stdout`) of the running Python scripts.

**The Decision:**  
Transition the UI to act as an asynchronous Kafka Consumer, entirely abandoning terminal log scraping.

**Justification:**  
While scraping terminal logs works fine for debugging a single script on a laptop, it is considered a massive anti-pattern in distributed systems because it fundamentally cannot scale. If Kubernetes spins up 5 Producer Pods, multiplexing 5 terminal output streams across a network to a web UI is chaotic and prone to dropped data. 

By treating the UI as a native event consumer—subscribing directly to the `transactions` and `fraud-alerts` Kafka topics—the dashboard flawlessly displays true, multiplexed data regardless of how many worker nodes exist.



---

## Decision 4: Polyglot Microservices (Python + Java Spring Boot)

**Context:**  
The project requires two functionally distinct components: The heavy data simulation and processing (the "Workers"), and a highly concurrent Enterprise Dashboard that exposes REST APIs and streams Server-Sent Events (the "Control Plane").

**The Decision:**  
Adopt a **Polyglot Architecture**. The Workers remain strictly in Python, while the Control Plane Dashboard is built in Java Spring Boot.

**Justification:**  
Utilizing a single programming language across an entire stack often leads to compromising on the "right tool for the job." 

- **The Data Plane (Python):** Python is the undisputed industry standard for Data Science and Machine Learning. Building the Fraud Detector model and Kafka consumer/producer workers in Python perfectly mirrors how an enterprise integrates ML.
- **The Control Plane (Java):** Java Spring WebFlux is inherently designed for massive concurrency, non-blocking I/O, and enterprise networking. Furthermore, `Spring Kafka` provides incredibly resilient native handling for concurrent consumer groups. By using Java to orchestrate Kubernetes calls and serve Reactive HTML streams, the system achieves a textbook separation of concerns identical to massive tech enterprises (e.g., Netflix, Uber).


