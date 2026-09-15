# Argo CD local verification

## Scope

This record covers only the local GitOps boundary verified on 15 September
2026. It does not claim production availability for stateful dependencies.

## Security model

- `health-insurance` AppProject accepts only this repository and the
  `health-insurance` namespace.
- Cluster scope is limited to `Namespace`; namespace scope is an explicit list
  of ConfigMap, Service, ServiceAccount, Deployment, HPA, NetworkPolicy,
  PodDisruptionBudget, ResourceQuota, and LimitRange.
- Secret, Role, and RoleBinding are intentionally excluded. The Harbor pull
  credential is created at runtime from ignored local configuration.
- Automatic sync is disabled. A reviewed immutable revision is promoted with
  one explicit sync operation.
- ServiceAccount token automount remains disabled for application workloads.

## Executed proof

```text
Argo CD version:             v3.5.2
Argo control-plane pods:     7/7 Ready
Application:                 health-insurance-staging
Sync:                        Synced
Operation:                   Succeeded
Git revision:                3ce1d4a93e94b670b237a4a387d5be7028696be0
Image revision:              7fc3ea6b1086d3f5be2d7adeb9f43bda6bd6ad8d
Private registry credential: runtime-only harbor-registry Secret
Private images pulled:       6/6 repositories
Runtime digest match:        confirmed for all six images
```

Observed runtime digests included authorization `e6811f...`, policy `78a036...`,
claims/billing `a52134...`, notification `67e9be...`, search `c65ddc...`, and
portal `e94f34...`; these matched Harbor's manifests for the same full Git SHA
tag.

## Failure found and corrected

The original 4 GiB Minikube container reached 99.7% memory while Argo CD and the
platform shared one node. Kubelet reported `container runtime is down`, making
the node `NotReady`. The live container limit was increased to 8 GiB; the node
returned to `Ready` without data loss or cluster recreation.

Argo's default one-second probe timing also caused false local restarts during
Docker Desktop I/O pressure. The local installer now uses `IfNotPresent` for
already cached pinned images and bounded, more tolerant server/repository probe
settings. These are local runtime accommodations, not weakened production
health semantics.

Pods created before the ServiceAccount registry reference existed did not gain
that reference retroactively. Only the failed, replaceable pods were deleted;
their controllers recreated them with `harbor-registry`, after which every
private image pull succeeded.

## Honest health boundary

`Synced` and `Succeeded` prove Git-to-cluster desired-state delivery and private
registry access. Application health can remain `Degraded` until operator-owned
PostgreSQL, Kafka, RabbitMQ, Redis, Elasticsearch, Keycloak, APM endpoints, and
runtime Secrets are supplied to the staging topology. This distinction avoids
presenting deployment reconciliation as full production readiness.
