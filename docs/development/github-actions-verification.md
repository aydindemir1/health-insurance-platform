# GitHub and GitHub Actions verification

## Purpose

GitHub Actions is the repository-hosted verification boundary. Jenkins remains
the vacancy-aligned delivery orchestrator for SonarQube, Nexus, Harbor and Argo
CD; GitHub Actions does not duplicate those publication responsibilities.

## Verified on 15 September 2026

- Local `main` and `origin/main` both resolved to
  `fb84335ebf91ed856df0adbff33b0f24502ca8bc` before this CI hardening change.
- Gateway CI run `34901588813` succeeded for the latest Compose/gateway change.
- Frontend CI run `34896585451` succeeded for the latest portal change.
- Backend CI run `34890911677` succeeded for all five services at commit
  `fbadd47628b1eac821bcbcf5984caa5690a8e5ab`.
- The later Backend CI run `34892915944` failed only in the
  `claims-billing-service` matrix job while the other four services succeeded.
  That commit changed Search Service and documentation, not Claims/Billing.
  Public metadata identifies the failing `Verify application` step, but GitHub
  requires an authenticated repository administrator to download its log. The
  evidence therefore supports a transient/flaky-run hypothesis, not a proven
  code root cause; the run was deliberately not retried or described as green.

## Workflow controls

All three workflows now use read-only repository permissions, path filters,
bounded job timeouts and per-ref concurrency cancellation. Each run records the
full 40-character `GITHUB_SHA` in its run summary. This creates a source identity
that the Jenkins publication flow later carries into Maven artifacts, OCI image
labels/tags and the Kustomize deployment revision.

The backend matrix independently verifies all five Maven services on Java 21.
The frontend workflow uses `npm ci`, lint, unit tests and `build:budget`, so a
bundle regression fails CI rather than remaining a local convention. Gateway CI
validates the Compose model and exercises a real APISIX process; its third-party
image is pinned by the digest already verified by the Kubernetes runtime.

## Failure interpretation

A green historical run proves only its exact commit. A red matrix run must be
classified from its failing job and log before code is changed. Publication
stages must not be rerun merely because a downstream registry or deployment
boundary failed; the immutable Git SHA allows that boundary to resume safely.

## Production hardening not claimed

GitHub branch protection, required status checks and environment approvals are
repository/organization settings and are not asserted by committed YAML alone.
Pinning reusable GitHub Actions by immutable commit SHA is a valuable additional
supply-chain control, but is left as an explicit follow-up until those upstream
revisions are reviewed and governed.
