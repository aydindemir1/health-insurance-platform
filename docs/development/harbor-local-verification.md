# Harbor local verification

## Purpose

Harbor is the private OCI boundary between a successful quality gate and the
GitOps deployment revision. This checkpoint verifies real Harbor behavior while
reusing existing images; no application image is rebuilt.

## Runtime verification — 15 September 2026

Harbor 2.15.2 was started from the previously prepared runtime and local images.
The first start exposed two Docker Desktop bind-mount defects:

1. `/data/database` was empty but owned by `root:root`; Harbor PostgreSQL runs as
   UID/GID `999:999` and could not create its `pg18` directory.
2. A missing `/data/secret/registry/root.crt` had previously been materialized by
   Docker as an empty directory, so it could not be mounted as a certificate file.

No registry or database data was deleted. The empty mount point was removed,
database directory ownership was corrected, and the existing Harbor `prepare`
image regenerated runtime secrets/configuration. The start script now performs
the same bounded preflight before `prepare` so the setup is repeatable.

All ten Harbor containers subsequently reached healthy state.

## Security and repository evidence

| Check | Verified result |
|---|---|
| Project | `health-insurance`, private |
| Anonymous project API | `401 Unauthorized` |
| Automatic Trivy scanning | Disabled; not a required release gate |
| Robot | `robot$health-insurance+jenkins-publisher`, enabled |
| Credential lifetime | 7,776,000 seconds (90 days) |
| Robot permissions | repository pull/push and artifact read/create only |
| Robot push | Six existing images pushed successfully |
| Robot pull | Authorization manifest returned `200` with the expected digest |
| Tag policy | Every repository uses full SHA `7fc3ea6b1086d3f5be2d7adeb9f43bda6bd6ad8d`; no `latest` |

## Published OCI inventory

| Repository | Manifest digest |
|---|---|
| authorization-service | `sha256:e6811f895740ebf4733784007a40f5ebe9bdbea43a601183699926b87f2ba113` |
| policy-service | `sha256:78a0367d021b83d530baff0ca1eee8ea3cf61a6c0d1e8c40f2e9ba6fb088ad56` |
| claims-billing-service | `sha256:a52134509921e60a767c79ef0ad06530404dcb7a9d13adb7f40f010d9ffa78cf` |
| notification-worker | `sha256:67e9be922d651d113d8346026397db13ba5bc76ad148cae5ad7292781a1cd2a2` |
| search-service | `sha256:c65ddc9e0047cbabdc83e41d87a86fa378bfa535862fc2b95aeb27bbc566b34f` |
| operations-portal | `sha256:e94f34c3b047366267fb9e8e8cbb65e9c4ad690cb859086e82ae0e588405b708` |

These are registry manifest digests. The full Git SHA tag links the images to
the Jenkins source revision, while digest addressing protects exact content.
The later end-to-end traceability step will also verify OCI revision labels and
the Kubernetes desired state; this checkpoint does not overstate that evidence.

## Operational boundary

Trivy is installed with the official Harbor distribution but `auto_scan=false`.
This is deliberate: the vacancy-aligned portfolio demonstrates registry RBAC,
private repositories, immutable tags and GitOps promotion without making a
resource-heavy vulnerability database update a mandatory gate. Production would
define scanning/signing policy and an exception process at organization level.

Harbor credentials remain in ignored local runtime state and Jenkins credential
storage. The robot is time-bounded and can be rotated by rerunning the bootstrap;
the administrator credential is never used by the publication pipeline.

## .NET mapping

Harbor corresponds to a private Azure Container Registry. The project is the
registry namespace, the robot is a scoped service principal, the Git SHA is an
immutable deployment tag, and the manifest digest is the exact content identity.
