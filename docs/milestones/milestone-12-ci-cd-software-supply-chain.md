# Milestone 12 — CI/CD and software supply chain

## Outcome

The repository demonstrates the advertised delivery chain: Git/GitHub,
Jenkins, SonarQube, Nexus, Harbor, Kubernetes, and Argo CD. Verification,
publication, and deployment are separate failure boundaries.

## Delivered flow

1. Jenkins checks out `main` and uses Java 21, Maven Wrapper, Node, and Docker.
2. Backend tests/architecture checks and frontend lint/tests/build run.
3. SonarQube analysis blocks publication until its Quality Gate passes.
4. Maven snapshots publish to Nexus Community Edition with least-privilege
   Jenkins credentials; EULA acceptance remains an explicit admin action.
5. CycloneDX JSON SBOMs are archived for five Java services and the portal.
6. Six OCI images publish to the private Harbor project using the full Git SHA.
7. Kustomize records that immutable image revision in Git.
8. Argo CD reads GitHub and performs a server-side Kubernetes sync.

## Verified evidence

- Jenkins Build #7: tests, frontend, SonarQube, Quality Gate, Nexus publication,
  and SBOM generation succeeded.
- Nexus smoke publication and Jenkins Maven publication succeeded after EULA
  acceptance.
- Harbor contains six private repositories tagged
  `7fc3ea6b1086d3f5be2d7adeb9f43bda6bd6ad8d`.
- Seven Argo CD control-plane pods reached Ready.
- `health-insurance-staging` reached `Synced`; operation `Succeeded`; Git
  revision `a56fff2a14405d3024b98f357b1c3b38edd8384b`.

Build #7's Harbor stage failed only because the containerized Docker CLI used
HTTPS against the local HTTP registry. The already-built images were reused and
the Harbor publication boundary was resumed through `localhost:8088`; no full
quality rerun was required. The committed local job now uses that corrected
registry address.

## Important files

- `Jenkinsfile`, `.jenkins/maven-settings.xml`.
- `infra/cicd/compose.quality.yaml`, `infra/cicd/compose.artifacts.yaml`.
- `infra/cicd/run-local-pipeline.ps1` and bootstrap helpers.
- `infra/cicd/harbor`, `deploy/gitops`, and the staging Kustomization.
- `scripts/validate-ci-pipeline.ps1`, `scripts/validate-supply-chain.ps1`.
- `docs/adr/014-local-ci-cd-software-supply-chain.md`.

## Demo data

No domain records are introduced. Safe evidence consists of repository names,
service names, build number, artifact coordinates, Git revisions, image tags,
pod readiness, and Argo sync status. Credentials and artifact contents are not
rendered in screenshots.

## Security and reliability decisions

- Secrets are ignored and injected through Jenkins credentials.
- Nexus publisher and Harbor robot accounts are least privilege.
- Publication is allowed only after the blocking Quality Gate.
- `latest` is forbidden; full Git SHA tags create an audit trail.
- Argo CD is pull-based and constrained by AppProject destinations/resources.
- Trivy is optional, not a required gate for this vacancy-aligned educational
  project. SonarQube remains the mandatory code-quality gate.
- A registry failure resumes at publication; it does not repeat successful tests.

## .NET/TFS mapping

| Implemented tool | Microsoft/.NET-oriented equivalent |
|---|---|
| Jenkinsfile | Azure Pipelines YAML / TFS build definition |
| Nexus Maven repository | Azure Artifacts/NuGet feed |
| Harbor | Private Azure Container Registry |
| SonarQube Quality Gate | Branch quality policy/static analysis gate |
| Argo CD | Pull-based GitOps deployment controller |
| CycloneDX BOM | NuGet/container dependency inventory |

TFS is documented as a transferable Git-based workflow rather than installed
only to add another product name. The same checkout, quality, artifact, and
promotion stages map directly to Azure DevOps Server/TFS build agents.

## Interview questions

- Why must publication wait for the SonarQube Quality Gate?
- Why use separate Nexus and Harbor repositories?
- How does a full Git SHA improve rollback and traceability?
- Why should registry retry not rerun an unchanged test suite?
- What is the difference between push-based deployment and Argo CD GitOps?
- Which controls would be required before this local flow became production?

## Remaining production gaps

Protected branches/environments, webhook credentials, trusted TLS, signing and
attestation, remote runners, backup/HA for CI services, external secret
management, policy-as-code enforcement, and production approval/audit controls
remain organizational work rather than claims of this laptop-based portfolio.
