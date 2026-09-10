# Kubernetes deployment and security

## Deployment boundary

`deploy/kubernetes/base` contains the seven stateless workloads owned by this
repository. Databases, brokers, IAM, search storage and observability backends
are external contracts. This preserves database-per-service ownership and keeps
stateful operational promises out of application manifests.

```mermaid
flowchart TB
    User[Operations user] --> Gateway[APISIX]
    Gateway --> Portal[Operations portal]
    Gateway --> Auth[Authorization Service]
    Gateway --> Policy[Policy Service]
    Gateway --> Claims[Claims and Billing]
    Gateway --> Search[Search Service]
    Worker[Notification Worker] --> External[(Operator-owned dependencies)]
    Auth --> External
    Policy --> External
    Claims --> External
    Search --> External
    subgraph Namespace[health-insurance namespace]
      Gateway
      Portal
      Auth
      Policy
      Claims
      Search
      Worker
    end
```

```text
deploy/kubernetes/
  base/                    production-oriented resources
  overlays/local/          one-replica Compose-backed developer overlay
  scripts/apply-local.ps1  context-guarded, no-secret-file apply helper
  secrets.example.env      key names only
```

## Security and availability controls

- Restricted Pod Security admission labels on `health-insurance`.
- Fixed UID/GID `10001` for Java and `101` for Nginx; APISIX uses its verified
  image UID/GID `636`.
- RuntimeDefault seccomp, all Linux capabilities dropped, no privilege
  escalation and read-only root filesystems.
- Dedicated ServiceAccounts with token automount disabled and no RBAC grants.
- Default-deny ingress/egress plus caller- and port-specific NetworkPolicies.
- Startup, readiness and liveness probes on all seven workloads.
- Requests/limits, graceful Spring shutdown, rolling updates, topology spread,
  disruption budgets and conservative CPU HPAs.
- No HPA for Notification Worker: queue-depth scaling requires an external
  metric adapter and is safer than CPU-based consumer scaling.

## Render and policy validation

```powershell
.\scripts\validate-kubernetes.ps1
kubectl kustomize deploy/kubernetes/base
kubectl kustomize deploy/kubernetes/overlays/local
```

For a live cluster API validation:

```powershell
.\scripts\validate-kubernetes.ps1 -ServerDryRun -Context kind-health-insurance
```

## Local overlay

The local overlay expects the existing Compose databases, Redis, RabbitMQ,
Elasticsearch, Keycloak and APM ports on `host.docker.internal`. Load the six
locally built application images into Kind before applying:

```powershell
kind create cluster --name health-insurance

$images = @(
  'health-insurance/authorization-service:local',
  'health-insurance/policy-service:local',
  'health-insurance/claims-billing-service:local',
  'health-insurance/notification-worker:local',
  'health-insurance/search-service:local',
  'health-insurance/operations-portal:local'
)
foreach ($image in $images) { kind load docker-image $image --name health-insurance }

.\deploy\kubernetes\scripts\apply-local.ps1
kubectl --context kind-health-insurance get pods -n health-insurance
```

The helper refuses a non-local context unless `-AllowNonLocalContext` is
explicitly provided. It reads ignored `.env` values and does not print or write
Secret payloads. APISIX's bearer-only compatibility value is generated in
memory when it is absent.

Local Kafka consumers and outbox relays are disabled because Compose advertises
`localhost:9092` to clients. This avoids claiming a working cross-runtime Kafka
path. Kafka/RabbitMQ behavior remains verified in the dedicated integration
environment.

Use port-forwarding instead of publishing every service:

```powershell
kubectl --context kind-health-insurance port-forward -n health-insurance service/apisix 9080:9080
kubectl --context kind-health-insurance port-forward -n health-insurance service/operations-portal 8088:8080
```

## Production integration

Replace example DNS names and local image tags through an environment overlay.
Create Secrets through the organization's approved external secret controller;
do not commit generated Secret YAML. Configure Metrics Server before expecting
HPA decisions, use trusted TLS for Keycloak and dependencies, and use immutable
registry digests supplied by the CI/CD milestone.

## GitOps checkpoint

Milestone 12 adds `deploy/gitops/environments/staging` and an Argo CD
`AppProject`/`Application`. The overlay maps all six application images to the
private Harbor project and one immutable full Git SHA.

```text
Application: health-insurance-staging
Sync: Synced
Operation: Succeeded
Git revision: a56fff2a14405d3024b98f357b1c3b38edd8384b
Image revision: 7fc3ea6b1086d3f5be2d7adeb9f43bda6bd6ad8d
```

`Progressing` or `Degraded` workload health is expected until operator-owned Secrets and
external PostgreSQL, Kafka, RabbitMQ, Redis, Elasticsearch, Keycloak, and APM
endpoints exist. Argo sync success proves desired-state delivery; it does not
misrepresent absent stateful production dependencies as healthy.
