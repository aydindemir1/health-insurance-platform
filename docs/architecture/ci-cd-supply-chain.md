# CI/CD and software supply chain

```mermaid
flowchart LR
    Dev[Developer] -->|commit and push| Git[GitHub main]
    Git --> Jenkins[Jenkins]
    Jenkins --> Tests[Java 21 tests and React quality]
    Tests --> Sonar[SonarQube Quality Gate]
    Sonar -->|pass| Nexus[Nexus Maven snapshots]
    Sonar -->|pass| SBOM[CycloneDX SBOM archive]
    Sonar -->|pass| Harbor[Private Harbor OCI registry]
    Harbor -->|full Git SHA tag| GitOps[Kustomize staging revision]
    GitOps --> Git
    Git --> Argo[Argo CD Application]
    Argo -->|server-side sync| K8s[Kubernetes / Minikube]
```

## Verified checkpoint

| Boundary | Evidence |
|---|---|
| Git | `main` contains the pipeline and immutable GitOps revision |
| Jenkins | Build #7 reached successful tests, frontend, Sonar, Nexus and SBOM stages |
| SonarQube | Blocking Quality Gate succeeded |
| Nexus | Authorization smoke upload and all Jenkins Maven publications succeeded |
| Harbor | Six existing service images were pushed with tag `7fc3ea6b1086d3f5be2d7adeb9f43bda6bd6ad8d` |
| Argo CD | Seven control-plane pods became Ready; Application sync operation succeeded |
| Kubernetes | Git revision `a56fff2a14405d3024b98f357b1c3b38edd8384b` rendered and synced |

`Progressing` or `Degraded` after sync is expected locally because production-owned databases,
brokers, IAM, TLS, and external secrets are not fabricated inside the GitOps
repository. This is a deployment dependency boundary, not a failed sync.

## Failure boundaries

- Quality failure: stop before Nexus/Harbor.
- Nexus failure: retry Maven publication only.
- Harbor failure: reuse existing images and retry tag/push only.
- GitOps failure: do not rebuild; correct the manifest or cluster dependency and
  resync the same immutable image revision.
