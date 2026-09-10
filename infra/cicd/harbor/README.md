# Harbor contract

Jenkins publishes only after the Quality Gate, only on `main`, and only when
`PUBLISH_ARTIFACTS=true`. Configure the target with `HARBOR_REGISTRY` and
`HARBOR_PROJECT`; store the robot-account credentials under the Jenkins ID
`harbor-publisher`. Grant that robot account push/pull on one project only.

For a local Harbor proof, install the official Harbor distribution or Helm
chart in a separate session, create project `health-insurance`, enable Trivy
scanning and block vulnerable artifacts from deployment. Do not run Harbor with
the application, SonarQube and Nexus simultaneously on a 16 GiB workstation.
The pipeline deliberately contains no `latest` tag: every image is addressed by
the complete Git commit SHA. A lightweight Docker Registry is not described as
Harbor evidence because it cannot prove Harbor RBAC, retention or scanning.
