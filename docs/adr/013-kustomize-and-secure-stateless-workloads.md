# ADR-013: Kustomize and secure stateless Kubernetes workloads

- Status: Accepted
- Date: 2026-09-10

## Context

The platform already has independently built application containers and a
Compose environment for local integration. Kubernetes must add scheduling,
rollout, isolation and health semantics without changing bounded-context data
ownership or pretending that hand-written single-node databases are a
production data platform.

## Decision

Use Kubernetes-native Kustomize with a production-oriented base and a local
overlay. Kustomize is shipped with `kubectl`, avoids a second packaging runtime,
and is consumed directly by the Argo CD Application delivered in Milestone 12.

The Kubernetes package owns only stateless platform workloads: five Spring
applications, the React/Nginx portal and APISIX. PostgreSQL databases, Kafka,
RabbitMQ, Redis, Elasticsearch, Keycloak and APM are explicit external service
contracts. A local overlay may connect to the existing Compose dependencies,
but it does not redefine that production boundary.

Every pod uses a dedicated ServiceAccount without an API token or RBAC grant.
Workloads run as fixed non-root numeric users, drop all capabilities, prohibit
privilege escalation, use RuntimeDefault seccomp and mount only bounded writable
temporary volumes. The namespace enforces the Restricted Pod Security Standard.
Default-deny NetworkPolicies are opened only for known callers and dependency
ports. HTTP workloads have startup, readiness and liveness probes. The worker
exposes Actuator health on port 8085 rather than using a process-only probe.

Credentials are never rendered by Kustomize. Deployments reference named
Secrets that must be supplied by an operator or an external secret controller.
The local helper reads ignored `.env` values, submits base64 data directly to
the Kubernetes API and never writes a generated Secret manifest to disk.

## Consequences

- Application manifests are reviewable and render without Helm.
- Stateful platform lifecycle, encryption, backup and high availability remain
  responsibilities of their owning operators or managed services.
- CPU HPAs require Metrics Server. Rabbit consumer scaling should eventually
  use queue-depth metrics rather than CPU, so the worker is not given an HPA.
- Port-bounded IP egress is useful defence in depth but is not identity-aware.
  Production clusters should add workload identity, TLS and an egress gateway.
- The local overlay disables Kafka-driven application paths because the Compose
  broker advertises a host-only listener that is not valid inside a Kind pod.
  Messaging behavior remains covered by its existing integration environment.

## Rejected alternatives

- Hand-written Kubernetes StatefulSets for every dependency: this would imply
  production operation, backup and quorum guarantees the repository does not own.
- One shared ServiceAccount with broad namespace permissions: applications do
  not call the Kubernetes API and therefore require no RBAC permissions.
- Committed development Secrets: reversible convenience does not justify
  publishing credentials.
- Helm as a mandatory local prerequisite: templating adds little value at this
  scale, while Kustomize is already available with `kubectl`.
