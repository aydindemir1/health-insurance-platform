# Milestone 12 CI/CD demo

## Purpose

Demonstrate the vacancy-aligned delivery chain without rerunning expensive
stages unnecessarily or exposing credentials.

## Scenario

1. Show the `Jenkinsfile`: Java/React quality, SonarQube gate, Nexus, SBOM, and
   Harbor publication execute in dependency order.
2. Show Jenkins Build #7: quality, Nexus, and SBOM stages succeeded. Explain
   that its Harbor HTTPS/HTTP endpoint mismatch was resumed independently.
3. Query Nexus and show six Maven components under `maven-snapshots`. Demonstrate
   that anonymous metadata access returns `403` while `jenkins-publisher` can
   browse and download through its repository-scoped role.
4. Open Harbor project `health-insurance` and show six repositories with the
   immutable tag `7fc3ea6b1086d3f5be2d7adeb9f43bda6bd6ad8d`. Show that the
   project is private and the 90-day robot has only push/pull/read/create access.
5. Render `deploy/gitops/environments/staging` and point to the same tag.
6. Show Argo CD Application `health-insurance-staging`: `Synced`, operation
   `Succeeded`, revision `3ce1d4a93e94b670b237a4a387d5be7028696be0`.
7. Show that AppProject has an explicit resource allowlist and cannot manage
   Secrets or namespace RBAC. Then show SHA-tagged pods with `harbor-registry`
   and compare their runtime image IDs with Harbor manifest digests.

## Interview explanation

“The pipeline fails closed before publication. Maven artifacts go to Nexus and
OCI images go to Harbor. Images are never promoted by `latest`; the full Git
SHA is committed to Kustomize. Argo CD pulls desired state from Git, so a
deployment is reproducible and auditable. Publication retries do not repeat an
already-successful test suite.”

## Safe reset

Stop the disposable cluster with `minikube stop -p portfolio-ci`. Nexus, Harbor,
Jenkins, and SonarQube data live in local Docker volumes. Do not commit
`infra/cicd/.env`, generated Harbor runtime files, tokens, or passwords.
Automatic Trivy scanning is intentionally disabled for this portfolio flow and
must not be presented as a completed mandatory gate.
