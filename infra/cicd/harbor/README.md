# Harbor contract

Jenkins publishes only after the Quality Gate, only on `main`, and only when
`PUBLISH_ARTIFACTS=true`. Configure the target with `HARBOR_REGISTRY` and
`HARBOR_PROJECT`; store the robot-account credentials under the Jenkins ID
`harbor-publisher`. Grant that robot account push/pull on one project only.

For a local Harbor proof, install the official Harbor distribution or Helm
chart in a separate session and create private project `health-insurance`.
Trivy remains available but automatic scanning is disabled for this bounded
portfolio workflow; it is not a release gate. Do not run Harbor with
the application, SonarQube and Nexus simultaneously on a 16 GiB workstation.
The pipeline deliberately contains no `latest` tag: every image is addressed by
the complete Git commit SHA. A lightweight Docker Registry is not described as
Harbor evidence because it cannot prove Harbor RBAC, retention or scanning.

On Windows with Docker Desktop, `start-local-harbor.ps1` invokes Harbor's pinned
official `prepare:v2.15.2` image directly, enables Trivy and starts the generated
Compose model on `http://host.docker.internal:8088`. Runtime configuration and
data are outside Git; generated credentials remain in the ignored CI `.env`.

After Harbor becomes healthy, create the private project and inject a
90-day, least-privilege robot credential into Jenkins:

```powershell
.\infra\cicd\harbor\bootstrap-local-harbor.ps1
```

The pipeline archives CycloneDX SBOMs for all six deployable components. Trivy
may be run explicitly as a separate diagnostic, but its availability is not
presented as a mandatory gate for this educational repository.
