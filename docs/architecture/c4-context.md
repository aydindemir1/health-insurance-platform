# C4 Level 1 — System Context

This diagram describes the system and people implemented through Milestone 4.
Dashed elements are external dependencies; future messaging/search components
are deliberately excluded because they are not implemented yet.

```mermaid
flowchart LR
    HU["Healthcare Provider User<br/>Submits and follows pre-authorizations<br/>Starts claims for the provider"]
    IS["Insurance Specialist<br/>Creates policies<br/>Decides pre-authorizations<br/>Handles financial reconciliation"]
    CA["Claim Approver<br/>Reviews and adjudicates claims"]
    SA["System Administrator<br/>Manages identities and roles"]

    HIP["Health Insurance Platform<br/>Pre-authorization, policy coverage,<br/>claims, invoices and settlement"]
    KC["Keycloak<br/>External identity and access management"]

    HU -->|"Uses browser over HTTPS"| HIP
    IS -->|"Uses browser over HTTPS"| HIP
    CA -->|"Uses secured REST API"| HIP
    SA -->|"Configures realm, users and roles"| KC
    HIP -->|"OIDC Authorization Code + PKCE<br/>JWT validation"| KC

    classDef person fill:#e8f1ff,stroke:#245ea8,color:#102a43
    classDef system fill:#dff7ed,stroke:#13795b,color:#103f32
    classDef external fill:#fff3cd,stroke:#9a6700,color:#4d3600
    class HU,IS,CA,SA person
    class HIP system
    class KC external
```

## Trust boundaries

- Browser input is untrusted. Provider ownership is derived from the signed
  `provider_id` token claim, never from a request body.
- Keycloak authenticates users; every service independently validates JWTs and
  enforces application-level authorization.
- No service reads another service's database.
- Only synthetic data may be used in the public repository and demo assets.
