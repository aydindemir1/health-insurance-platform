# Nexus local verification

## Purpose

Nexus owns versioned Maven artifacts after the blocking Quality Gate. This
checkpoint verifies the existing local repository without rebuilding services,
republishing artifacts or pulling another Nexus image.

## Runtime verification — 15 September 2026

The persisted Nexus instance was started with the existing
`sonatype/nexus3:3.84.1` image:

```powershell
docker compose -f infra/cicd/compose.artifacts.yaml `
  up --detach --no-build --pull never
```

| Check | Verified result |
|---|---|
| Container health | Healthy on `127.0.0.1:8087` |
| Community Edition EULA | Accepted through the earlier explicit user-approved administration step |
| Hosted repositories | `maven-snapshots` and `maven-releases`, Maven 2 format |
| Publisher identity | `jenkins-publisher`, active, only role `health-publisher` |
| Publisher privileges | Eight add/browse/edit/read privileges limited to the two project Maven repositories |
| Unexpected privileges | Zero |
| Snapshot components | Six: two Authorization publications and one publication for each other Java service |
| Anonymous component API | `403 Forbidden` after hardening |
| Authenticated component API | `200 OK` with the publisher identity |

The publisher downloaded this real artifact successfully:

```text
com.aydindemir.health:authorization-service:0.0.1-20260910.194813-1
bytes: 93188778
sha256: 1a0bc4c9d1280a309432503e605c441d537a298ffb271cc78f490453ad41691c
```

The hash proves byte-level retrieval from Nexus; it is not presented as a
signature or provenance attestation.

## Security correction

The persisted Nexus default still allowed anonymous reads, so unauthenticated
component metadata returned `200`. Anonymous access was disabled at runtime and
the bootstrap script now enforces the same policy idempotently. An unauthenticated
request now returns `403`, while the least-privilege Jenkins publisher can still
browse and download artifacts.

Credentials remain only in ignored `infra/cicd/.env` and Jenkins credential
storage. Neither administrator nor publisher secrets are printed or committed.

## Artifact inventory

```text
authorization-service    0.0.1-20260910.194813-1
authorization-service    0.0.1-20260910.195719-2
policy-service           0.0.1-20260910.195726-1
claims-billing-service   0.0.1-20260910.195733-1
notification-worker      0.0.1-20260910.195739-1
search-service           0.0.1-20260910.195745-1
```

## Failure and ownership boundary

Nexus stores Maven packages; Harbor stores OCI images. A Nexus publication
failure is retried at the Maven publication boundary after the same successful
Quality Gate. It must not trigger an unrelated Harbor retry or repeat unchanged
test stages.

## Verified provenance attachments

Build #10 published a `build-provenance.json` classifier beside each of the five
Java service artifacts. All five records contain source revision
`6c07fa81df22330699c58574059b89e58777f0ed`, Jenkins Build #10 URL, and the exact
JAR SHA-256. This links the timestamped Maven coordinate to source without
encoding mutable CI state into the artifact version. Representative hashes are
authorization `f639dac1...`, policy `71c9e361...`, claims `33dbc1c0...`,
notification `c56336a6...`, and search `9b21f095...`.

## .NET mapping

Nexus hosted Maven repositories correspond to Azure Artifacts or a private
NuGet feed. Maven `deploy` corresponds to `dotnet nuget push`; repository roles
and credentials provide the same separation between package consumers,
publishers and administrators.
