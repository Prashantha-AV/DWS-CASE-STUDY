# Bank Transfer Service — DB Challenge Solution

A high-performance, thread-safe financial transfer REST API built using **Java 17** and **Spring Boot 3.4.0**, designed to process concurrent account balance transfers efficiently, reliably, and without deadlocks.

---

## Changes Implemented in This Solution

* **Deterministic Deadlock Prevention**: Replaced ad-hoc locking and retry mechanisms with strict natural ordering (`accountId.compareTo(...)`), preventing circular wait conditions and guaranteeing deadlock-free execution under concurrent bidirectional transfers.
* **Encapsulated Account Concurrency**: Embedded fine-grained synchronization within the `Account` domain model using `ReentrantLock` to preserve state consistency during simultaneous debits and credits without blocking unrelated accounts.
* **Dual-Party Transfer Notifications**: Dispatched transactional notifications to **both** the sender and recipient via `NotificationService`, fulfilling the requirement while mocking the collaborator across all automated tests.
* **Defensive Domain Validation**: Enforced strict validation rules rejecting negative transfer amounts, zero amounts, overdraft conditions, non-existent account lookups, and self-transfers (`accountFrom == accountTo`).
* **Fixed Deserialization Constraints**: Corrected Jackson constructor binding on `Account` to ensure omitted balances remain `null` and reliably trip Spring's `@NotNull` Bean Validation.
* **Centralized Error Handling**: Standardized API error payloads across all endpoints using `@RestControllerAdvice` in `GlobalExceptionHandler`, cleanly mapping domain violations to descriptive `400 Bad Request` responses.
* **Comprehensive Automated Test Suite**:
    * **Unit Tests (`AccountsServiceTest`)**: Validates business boundary constraints, domain exceptions, and dual notification delivery via Mockito.
    * **Integration Tests (`AccountsControllerTest`)**: Verifies HTTP contract status codes (`200`, `201`, `400`) and request/response JSON serialization using `MockMvc`.
    * **Concurrency Stress Tests (`AccountsServiceConcurrentTest`)**: Simulates simultaneous bidirectional transfer contention across thread pools to verify strict ledger balance conservation and absence of deadlocks.
    * **Notification Validation & Edge Case Tests (`AccountsServiceNotificationTest`)**: Verifies that failed transfers (non-positive amounts, `null` inputs, self-transfers, missing accounts, and insufficient balance overdrafts) fail fast and cleanly dispatch **zero** spurious notifications.
    * **Concurrent Notification Contention Tests (`AccountsServiceNotificationConcurrentTest`)**: Evaluates multi-threaded transfer loads to guarantee that exactly two notifications are dispatched per successful transaction (one to the sender and one to the receiver), verifying correct counterparty message payloads without drop-offs or race condition leakage.

---

## Case Study Verification & Evaluation

| Category | Requirement / Scenario | Test & Edge Case Coverage | Status |
| :--- | :--- | :--- | :--- |
| **Core Functionality** | Transfer API Contract | Validates `accountFrom`, `accountTo`, and `amount` payload format via REST endpoint. | **PASSED** |
| **Core Functionality** | Positive Amount Validation | Rejects zero, negative values, and non-numeric amounts (`400 Bad Request`). | **PASSED** |
| **Core Functionality** | Zero Overdraft Enforcement | Rejects transfers exceeding source account balance; balances never fall below zero. | **PASSED** |
| **Core Functionality** | Dual Party Notifications | Notifies sender and receiver with counterparty ID and transfer amount via `NotificationService`. | **PASSED** |
| **Testing Standards** | Mocked Notification Service | `NotificationService` preserves its interface boundary and is mocked across all test suites. | **PASSED** |
| **Concurrency** | Atomic Balance Updates | Granular synchronization prevents race conditions and corrupted balances under concurrent load. | **PASSED** |
| **Concurrency** | Deadlock Prevention | Implements deterministic lock ordering (`accountId` sorting) to prevent circular wait on simultaneous reverse transfers (`A -> B` vs `B -> A`). | **PASSED** |
| **Edge Cases** | Self-Transfer | Rejects attempts to transfer funds where `accountFrom == accountTo`. | **PASSED** |
| **Edge Cases** | Non-Existent Account(s) | Verifies existence of both sender and recipient before acquiring locks or updating balances. | **PASSED** |
| **Edge Cases** | Exact Balance Transfer | Successfully transfers remaining balance down to exactly zero without triggering overdraft errors. | **PASSED** |
| **Edge Cases** | Competing Deductions | Concurrent transfers competing for limited funds correctly allow only one and reject the other. | **PASSED** |
| **Precision** | Currency Floating Precision | Uses `BigDecimal` representation to avoid IEEE 754 floating-point inaccuracies. | **PASSED** |
| **Production Readiness** | Production Recommendations | Outlines essential future production improvements (Observability, Auth, API Gateway, Messaging, Scalability). | **PASSED** |

---

## Future Scope & Enterprise Production Readiness

In this reference implementation, accounts and locks reside in-memory within a single JVM instance. Transitioning this architecture to a distributed, multi-region production banking environment involves the following production upgrades:

### 1. Asynchronous Messaging & Microservices Decoupling
* **Event-Driven Notification Microservice**: Extract `NotificationService` into an autonomous downstream microservice, decoupling it from the core transfer processing pipeline.
* **Transactional Outbox via Kafka / RabbitMQ / AMPS**: Persist transfer events into an immutable transactional outbox table within the ledger update transaction, streaming events asynchronously to message brokers (Apache Kafka, RabbitMQ, or AMPS) to prevent downstream network latency from holding client transactions open.
* **Resilient Service Communication**:
    * **Declarative Sync Client (Spring Cloud OpenFeign)**: Use OpenFeign interfaces with client-side load balancing, circuit breakers, rate limiters, and fallback handlers via **Resilience4j**.
    * **Reactive Non-Blocking Client (Spring WebFlux / WebClient over Netty)**: Transition external I/O integrations to a non-blocking reactive stack (`WebClient` backed by Netty) to maximize throughput without thread pool starvation.

### 2. Stateless Core, Data Persistence & Distributed Concurrency
* **Stateless Application Architecture**: Store all account balances, ledger entries, and audit data in an external database rather than JVM memory, allowing horizontal scaling across multiple pods behind a load balancer.
* **Spring Data JPA & Explicit REST APIs**: Implement standard Spring Data JPA repositories with dedicated `@RestController` endpoints and explicit DTO mappings to ensure strict business validation and maintain encapsulation.
* **Distributed Concurrency & Locking Across Clusters**:
    * **Pessimistic Locking (High Contention)**: Use database row-level locking (`SELECT ... FOR UPDATE` acquired in natural account ID order) within ACID transactions to prevent deadlocks and maintain consistency across independent pods.
    * **Optimistic Locking (Low Contention)**: Use version columns (`@Version`) with exponential backoff retries for low-contention scenarios.
    * **Distributed Sagas**: For cross-service ledger settlements across decoupled databases, apply an **Orchestrated Saga Pattern** with compensating actions instead of distributed two-phase commit (2PC) locks.
* **Dynamic Scaling & Service Discovery**: Register containerized services with **Eureka** or **Consul** alongside **Spring Cloud Gateway** for dynamic, health-aware traffic routing under Kubernetes Horizontal Pod Autoscaling (HPA).

### 3. API Gateway, Edge Routing & Enterprise Security
* **API Gateway Pattern**: Route incoming traffic through an enterprise API Gateway (Spring Cloud Gateway) to manage TLS termination, CORS policies, path-based routing, and Redis-backed rate limiting (Token Bucket).
* **OAuth2 / OIDC Authentication**: Protect financial endpoints using Spring Security as an OAuth2 / JWT Resource Server, validating cryptographically signed tokens against identity providers (Okta, Keycloak, Azure AD).
* **Role-Based Access Control (RBAC)**: Enforce granular scope authorization (`SCOPE_transfers:write`) to verify that the authenticated caller owns or is authorized on the debit account before executing transfers.
* **Dual-Layer Transfer Idempotency**:
    * **Cache Layer (Redis SETNX)**: Intercept duplicate client retries using an `Idempotency-Key` HTTP header cached in Redis with short TTLs.
    * **Database Layer (ACID Unique Constraint)**: Store processed idempotency keys with transaction references using a database `UNIQUE` constraint, protecting the ledger even during Redis failover.

### 4. Comprehensive Observability, Metrics & Centralized Logging
* **Spring Boot Actuator Health Indicators**: Expose health status and metrics via Actuator (`/actuator/health`, `/actuator/metrics`), adding custom indicators for connection pool saturation, cache hit ratios, and message broker health.
* **Transaction Performance Metrics**: Instrument transfers with Micrometer and Prometheus to monitor p95/p99 transaction execution latencies, throughput, lock-wait durations, and error rates via Grafana dashboards.
* **Centralized Logging & Distributed Tracing**:
    * Format application logs using JSON layout (`LogstashLogbackEncoder`) and ship to **Splunk** or **ELK** for audit trails and compliance tracking.
    * Propagate OpenTelemetry trace headers (`traceId` / `spanId`) across HTTP calls and Kafka topics for end-to-end distributed tracing.

### 5. API Documentation & Externalized Configuration
* **Interactive OpenAPI 3 / Swagger Documentation**: Provide interactive API exploration via Swagger UI (`/swagger-ui.html`) and OpenAPI specifications (`/api-docs`) with documented error catalogues and example payloads.
* **Externalized Configuration**: Externalize validation rules, business thresholds, and localized error messages into external configuration repositories (e.g., Spring Cloud Config, Consul KV, or Kubernetes ConfigMaps).

### 6. Containerization, CI/CD & Financial Auditability
* **Secure Containerization**: Package the service using multi-stage, rootless Docker containers running on minimal base images (e.g., Eclipse Temurin Alpine/Distroless).
* **Automated CI/CD Workflows**: Implement Jenkins or GitHub Actions pipelines running static code analysis (SonarQube), container security vulnerability scans (Trivy, RAVEN), automated unit and concurrency test execution, and deployment to Kubernetes or OpenShift.
* **Double-Entry Ledger & Audit Logging**: Implement an append-only transaction ledger (`transfer_audit_log`) capturing unique transfer IDs, pre- and post-transaction balance snapshots, actor metadata, and timestamps to satisfy regulatory and financial compliance requirements.