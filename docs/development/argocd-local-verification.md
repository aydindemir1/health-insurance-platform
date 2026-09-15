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
Git desired-state revision:  c9c1baa496df1c0126648573c5f25a75f6967a5c
Image/source revision:       6c07fa81df22330699c58574059b89e58777f0ed
Private registry credential: runtime-only harbor-registry Secret
Rendered private images:     6/6 use the same full Git SHA
```

The Application reached `Synced`; its operation reached `Succeeded`. Kubernetes
Deployment specs expose the six promoted `6c07fa8...` tags. Application health
remained `Degraded` because the independently managed databases, brokers, IAM,
and search runtime were intentionally stopped during resource-isolated CI.

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

GitHub fetches took about 30 seconds on the local Docker Desktop network while
Argo CD's repository request timeout was 15 seconds. The installer now sets
`reposerver.git.request.timeout=60s`; this changes only the bounded local fetch
window. A hard refresh then resolved desired revision `c9c1baa...`, and the
explicit sync applied image revision `6c07fa8...` successfully.

The Search HPA subsequently changed its Deployment replica count, which briefly
reported `OutOfSync`. The Application now ignores only `/spec/replicas` for
Deployments and enables `RespectIgnoreDifferences`; HPA owns scaling while GitOps
continues to detect drift in images, configuration, security, and resources.

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
