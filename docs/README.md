# Engineering Documentation

Documentation is maintained with every milestone rather than reconstructed at
the end of the project.

## Architecture map

- [C4 system context](architecture/c4-context.md)
- [C4 container view](architecture/c4-container.md)
- [Clean Architecture boundaries](architecture/clean-architecture.md)
- [Authorization components](architecture/authorization-service.md)
- [Policy components](architecture/policy-service.md)
- [Claims and Billing components](architecture/claims-billing-service.md)
- [Data ownership and ER model](architecture/data-model.md)
- [Workflow sequence diagrams](architecture/workflow-sequences.md)
- [Frontend architecture](architecture/frontend-architecture.md)
- [Local deployment](architecture/local-deployment.md)

## Learning and demonstration

- [Technical walkthrough](project-technical-walkthrough.md)
- [Demo scenario](demo/demo-scenario.md)
- [Screenshot catalogue](screenshots/README.md)

## Architecture decisions

- [ADR-001: Clean Architecture service boundaries](adr/001-hexagonal-architecture.md)
- [ADR-002: Provider ownership from authenticated identity](adr/002-provider-ownership-from-authenticated-identity.md)
- [ADR-003: Optimistic concurrency for decisions](adr/003-optimistic-concurrency-for-decisions.md)
- [ADR-004: Feature-Sliced operations portal](adr/004-feature-sliced-operations-portal.md)
- [ADR-005: Synchronous policy coverage evaluation](adr/005-synchronous-policy-coverage-evaluation.md)
- [ADR-006: Claims and Billing ownership and integration](adr/006-claims-billing-ownership-and-integration.md)

## Milestone documentation definition of done

Every milestone closes with this review:

- README status, capabilities, commands, endpoints, and roadmap are current.
- C4, component, data, sequence, frontend, and deployment diagrams reflect the
  code and do not show planned components as implemented.
- ADRs capture every material boundary or trade-off decision.
- Synthetic demo data exercises the newly delivered behavior without secrets or
  real health information.
- The demo scenario includes happy paths, negative paths, prerequisites, and a
  safe reset procedure.
- Screenshots show only implemented UI and synthetic data.
- The technical walkthrough, .NET mappings, interview pitch, likely questions,
  limitations, and next milestone are updated.
- Markdown links, Mermaid syntax, JSON, scripts, tests, and production builds are
  verified before commit.

Run `./scripts/validate-documentation.ps1` from PowerShell to check local links,
JSON and demo-script syntax, the expected screenshot set, and every Mermaid
diagram. `-SkipMermaid` is available for a quick syntax-and-file pass.
