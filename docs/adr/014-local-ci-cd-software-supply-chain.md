# ADR-014: Local CI/CD and software supply chain

## Status

Accepted

## Context

The portfolio must demonstrate the vacancy's Git, Jenkins, SonarQube, Nexus,
Harbor, Kubernetes, and Argo CD workflow without pretending that a developer
laptop is a production platform.

## Decision

Jenkins is the orchestration boundary. It checks out Git, runs backend and
frontend quality stages, waits for the SonarQube Quality Gate, publishes Maven
snapshots to Nexus, creates CycloneDX SBOMs, and publishes OCI images to a
private Harbor project using full Git SHA tags. A reviewed GitOps commit updates
the Kustomize image references; Argo CD reads `main` and performs the Kubernetes
sync. Credentials remain in ignored local environment/runtime stores and are
injected through Jenkins credentials.

Nexus Community Edition EULA acceptance is an explicit administrator action,
never an automatic default. Trivy is optional and is not a release gate for this
educational environment. SonarQube remains the mandatory static quality gate;
Harbor demonstrates private registry ownership and immutable delivery.

## Consequences

- A failed publication can be resumed independently; successful tests are not
  rerun merely because Nexus or Harbor was temporarily unavailable.
- Image promotion is auditable because Git, Harbor, and Argo CD share the Git
  SHA identity.
- Local HTTP endpoints and Minikube-specific registry DNS are development-only.
- Production still requires trusted TLS, external secret management, signed
  artifacts, protected environments, HA runners, and organizational approvals.

## Alternatives

GitHub Actions could be the primary orchestrator, but Jenkins was selected to
exercise the advertised stack. Helm was unnecessary because Kustomize already
provides environment overlays. Terraform and Ansible were not introduced: this
milestone provisions no cloud infrastructure or fleet configuration.
