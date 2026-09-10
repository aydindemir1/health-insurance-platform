# Jenkins quality-gate baseline

The declarative pipeline expects a disposable Linux agent labelled
`java21-node24-docker` with Java 21, Node.js 24, npm, Docker CLI and Git. Jenkins
must define:

- a SonarQube server named `health-sonarqube`;
- a SonarScanner installation named `sonar-scanner`;
- the SonarQube webhook endpoint at `<jenkins>/sonarqube-webhook/` so
  `waitForQualityGate` can complete.

Tokens belong in Jenkins Credentials/SonarQube configuration and must never be
passed as build parameters or committed files. This first slice performs tests,
builds, analysis and a blocking Quality Gate. Artifact publication and GitOps
promotion are intentionally added in later Milestone 12 slices.
