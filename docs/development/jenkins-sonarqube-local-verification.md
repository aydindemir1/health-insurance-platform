# Jenkins and SonarQube local verification

## Purpose

This checkpoint verifies the existing local quality stack without rebuilding
images or starting another expensive pipeline. Jenkins orchestrates the delivery
flow; SonarQube is its blocking static-analysis gate. A healthy UI alone is not
sufficient evidence, so the checks cover authenticated APIs, job stages, source
revision, webhook configuration and container-to-container connectivity.

## Runtime verification — 15 September 2026

The existing local images and persistent volumes were started with:

```powershell
docker compose --env-file infra/cicd/.env `
  -f infra/cicd/compose.quality.yaml up --detach --no-build
```

No image was built or pulled. Runtime secrets remained in the ignored
`infra/cicd/.env`; only variable names were validated and no value was printed.

| Check | Verified result |
|---|---|
| Jenkins container | Healthy; authenticated API available on `127.0.0.1:8086` |
| SonarQube container | Healthy; `/api/system/status` returned `UP` on `127.0.0.1:9000` |
| Sonar PostgreSQL | Healthy; persisted database reused |
| Internal connectivity | Jenkins resolved `sonarqube` and received status `UP` over the Compose network |
| Jenkins job | `health-insurance-platform`, GitHub `main`, repository `Jenkinsfile`, lightweight checkout |
| Sonar authentication | Runtime administrator credential accepted |
| Quality Gate | `OK`; `new_violations=0`, error threshold `0` |
| Webhook | `jenkins-local` targets `http://jenkins:8080/sonarqube-webhook/` |

## Build #7 evidence and failure boundary

Jenkins Build #7 analyzed immutable revision
`7fc3ea6b1086d3f5be2d7adeb9f43bda6bd6ad8d`. Its overall result is correctly
`FAILURE`, but the stage API proves the following sequence:

```text
Checkout -> five backend services -> frontend -> Sonar analysis -> Quality Gate
         -> Nexus publication -> CycloneDX SBOMs -> Harbor publication
              SUCCESS              SUCCESS             FAILURE
```

More precisely, checkout, all five Java service stages, frontend quality,
SonarQube analysis, the blocking Quality Gate, Nexus publication and SBOM
generation succeeded. Only `Publish OCI images to Harbor` failed due to the
already documented local HTTPS/HTTP registry mismatch. The latest SonarQube
analysis revision is the same full Git SHA and its current gate remains `OK`.

This distinction matters operationally: a downstream registry failure must not
erase upstream evidence or force unchanged tests to run again. The Harbor
boundary was corrected and resumed independently using the already-built images.
No new Jenkins build was started for this verification.

## How to inspect safely

```powershell
docker compose --env-file infra/cicd/.env `
  -f infra/cicd/compose.quality.yaml ps

./scripts/validate-ci-pipeline.ps1
```

Open `http://localhost:8086/job/health-insurance-platform/7/pipeline-overview/`
for the stage graph and `http://localhost:9000/dashboard?id=health-insurance-platform`
for the analysis. Credentials come from the ignored runtime environment and must
never be copied into screenshots, commands, Git history or documentation.

## Architecture and .NET mapping

- Jenkins Pipeline corresponds to a multi-stage Azure DevOps/TFS or GitLab CI
  pipeline; the `Jenkinsfile` is the versioned pipeline definition.
- SonarQube Quality Gate corresponds to a blocking code-quality policy before a
  NuGet/Maven or container publication stage.
- The Sonar webhook is asynchronous completion notification. Jenkins waits for
  the authoritative gate result instead of guessing from scanner exit status.
- Nexus is the Maven artifact boundary; Harbor is the OCI image boundary. Their
  independent retry semantics are intentional.

## Honest portfolio boundary

This local evidence proves configuration, authentication, network integration,
analysis gating and stage-level failure isolation. It does not claim Jenkins or
SonarQube high availability, enterprise backup, trusted TLS, external identity,
or production runner isolation.
