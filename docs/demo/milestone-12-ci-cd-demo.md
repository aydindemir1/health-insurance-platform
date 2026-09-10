# Milestone 12 CI/CD demo

## Purpose

Demonstrate the vacancy-aligned delivery chain without rerunning expensive
stages unnecessarily or exposing credentials.

## Scenario

1. Show the `Jenkinsfile`: Java/React quality, SonarQube gate, Nexus, SBOM, and
   Harbor publication execute in dependency order.
2. Show Jenkins Build #7: quality, Nexus, and SBOM stages succeeded. Explain
   that its Harbor HTTPS/HTTP endpoint mismatch was resumed independently.
3. Query Nexus and show Maven components under `maven-snapshots`.
4. Open Harbor project `health-insurance` and show six repositories with the
   immutable tag `7fc3ea6b1086d3f5be2d7adeb9f43bda6bd6ad8d`.
5. Render `deploy/gitops/environments/staging` and point to the same tag.
6. Show Argo CD Application `health-insurance-staging`: `Synced`, operation
   `Succeeded`, revision `a56fff2a14405d3024b98f357b1c3b38edd8384b`.

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
