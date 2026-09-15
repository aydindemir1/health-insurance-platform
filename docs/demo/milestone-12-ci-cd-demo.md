# Milestone 12 CI/CD demo

## Purpose

Demonstrate the vacancy-aligned delivery chain without rerunning expensive
stages unnecessarily or exposing credentials.

## Scenario

1. Show the `Jenkinsfile`: Java/React quality, SonarQube gate, Nexus, SBOM, and
   Harbor publication execute in dependency order.
2. Show Jenkins Build #10: the complete pipeline succeeded at source revision
   `6c07fa81df22330699c58574059b89e58777f0ed`.
3. Query Nexus and show six Maven components under `maven-snapshots`. Demonstrate
   that anonymous metadata access returns `403` while `jenkins-publisher` can
   browse and download through its repository-scoped role.
4. Open Harbor project `health-insurance` and show six repositories with the
   immutable tag `6c07fa81df22330699c58574059b89e58777f0ed`. Show that the
   project is private and the 90-day robot has only push/pull/read/create access.
5. Render `deploy/gitops/environments/staging` and point to the same tag.
6. Show Argo CD Application `health-insurance-staging`: `Synced`, operation
   `Succeeded`, desired-state revision `c9c1baa496df1c0126648573c5f25a75f6967a5c`.
7. Show that AppProject has an explicit resource allowlist and cannot manage
   Secrets or namespace RBAC. Then show SHA-tagged pods with `harbor-registry`
   and compare their runtime image IDs with Harbor manifest digests.
8. Open a Nexus `build-provenance.json` attachment,
   inspect the OCI `revision` label, and compare both with the Kustomize
   source-revision annotation and the deployed image tag.

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
