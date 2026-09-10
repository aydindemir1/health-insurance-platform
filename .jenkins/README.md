# Jenkins quality-gate baseline

The declarative pipeline expects a disposable Linux agent labelled
`java21-node24-docker` with Java 21, Node.js 24, npm, Git and Docker CLI. Jenkins
must define:

- a SonarQube server named `health-sonarqube`;
- a SonarScanner installation named `sonar-scanner`;
- the SonarQube webhook endpoint at `<jenkins>/sonarqube-webhook/` so
  `waitForQualityGate` can complete.
- username/password credentials named `nexus-publisher` and `harbor-publisher`
  before enabling the opt-in publication stages.

Tokens belong in Jenkins Credentials/SonarQube configuration and must never be
passed as build parameters or committed files. This first slice performs tests,
builds, analysis and a blocking Quality Gate. Artifact publication and GitOps
promotion is intentionally added in a later Milestone 12 slice.

`PUBLISH_ARTIFACTS` defaults to false. On `main`, explicitly enabling it after a
successful Quality Gate publishes executable Maven artifacts to the Nexus
release repository and six OCI images to Harbor. Images use the complete Git
commit SHA; the pipeline never publishes `latest`.

Start the resource-limited local controller and SonarQube server independently
from the application stack:

```powershell
.\infra\cicd\start-quality-stack.ps1
```

The script creates an ignored `.env` with random local-only credentials on its
first run. Use `stop-quality-stack.ps1` to preserve volumes while releasing CPU
and memory. The three-service profile is capped at 4.25 CPUs and approximately
4.5 GiB RAM; it is not a production topology.
