# Milestone 11 — Kubernetes and deployment security

## Outcome

The seven stateless repository-owned workloads are packaged with Kustomize:
five Spring applications, the React/Nginx portal, and APISIX. PostgreSQL,
Kafka, RabbitMQ, Redis, Elasticsearch, Keycloak, and APM remain explicit
operator-owned dependencies.

## Delivered controls

- Restricted Pod Security namespace labels.
- Fixed non-root users, read-only root filesystems, RuntimeDefault seccomp,
  dropped capabilities, and disabled privilege escalation.
- Dedicated ServiceAccounts without API-token automount and no RBAC grants.
- Default-deny NetworkPolicies with bounded ingress and egress exceptions.
- Startup, readiness, and liveness probes; resource requests/limits; graceful
  shutdown; rolling updates; topology spread; PDBs; and conservative HPAs.
- Secret references only. The local helper sends ignored runtime values directly
  to the API and never generates a committed Secret manifest.
- Production base and one-replica Compose-backed local overlay.

## Important files

- `deploy/kubernetes/base` — production-oriented workload contracts.
- `deploy/kubernetes/overlays/local` — local dependency routing and replica patch.
- `deploy/kubernetes/scripts/apply-local.ps1` — guarded, secret-safe local apply.
- `scripts/validate-kubernetes.ps1` — render and policy assertions.
- `docs/adr/013-kustomize-and-secure-stateless-workloads.md` — decision record.
- `docs/deployment/kubernetes.md` — runbook and architecture diagram.

## Why this design

Kustomize preserves plain Kubernetes resources and is directly consumable by
Argo CD. Helm would add a second templating model without a current reuse need.
Stateful platforms are not represented by simplistic single-node StatefulSets,
because that would imply storage, quorum, backup, and recovery guarantees the
portfolio does not provide.

## Verification and demo

The base and local overlay render successfully. Milestone 12 additionally
proved server-side application by Argo CD on a disposable Minikube cluster.
Use the Kubernetes guide to inspect a Deployment, NetworkPolicy, PDB, HPA,
ServiceAccount, and namespace security labels. Screenshot
`14-argocd-gitops-sync.png` records the resulting GitOps deployment state.

## Demo data

No new patient, policy, claim, or payment data is required. This milestone
demonstrates deployment metadata only: synthetic namespace, workload, service,
probe, policy, and resource definitions.

## .NET mapping

| Kubernetes concept | Familiar Microsoft ecosystem analogy |
|---|---|
| Deployment/Service | Container Apps or AKS workload/service |
| ConfigMap/Secret reference | Externalized `appsettings` and Key Vault reference |
| Readiness/liveness probe | ASP.NET Core health checks used by the orchestrator |
| NetworkPolicy | Namespace/workload network ACL |
| HPA/PDB | Autoscale policy and availability budget |

## Interview questions

- Why are databases not deployed by these manifests?
- What is the difference between readiness, liveness, and startup probes?
- Why disable ServiceAccount token automount?
- Why can an HPA and PDB conflict during constrained capacity?
- How would production secrets and workload identity replace the local helper?

## Remaining production gaps

External Secrets/workload identity, trusted ingress and registry TLS, managed
stateful platforms, metrics adapters, multi-zone capacity, admission policy,
and production operations are intentionally outside this local portfolio proof.
