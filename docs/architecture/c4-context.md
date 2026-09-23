# C4 Level 1 — Sistem Bağlamı

Bu diyagram Milestone 10'a kadar uygulanan kullanıcı ve trust boundary'yi açıklar.
Runtime container'lar ve broker'lar Level 2 görünümünde ayrıntılandırılır.

```mermaid
flowchart LR
    HU["Healthcare Provider User<br/>Submits and follows pre-authorizations<br/>Starts claims for the provider"]
    IS["Insurance Specialist<br/>Creates policies<br/>Decides pre-authorizations<br/>Handles financial reconciliation"]
    CA["Claim Approver<br/>Reviews and adjudicates claims"]
    SA["System Administrator<br/>Manages identities and roles<br/>Reviews audit evidence and runs bounded recovery"]

    HIP["Health Insurance Platform<br/>Pre-authorization, policy coverage,<br/>claims, invoices and settlement"]
    KC["Keycloak<br/>External identity and access management"]

    HU -->|"Uses browser over HTTPS"| HIP
    IS -->|"Uses browser over HTTPS"| HIP
    CA -->|"Uses secured REST API"| HIP
    SA -->|"Configures realm, users and roles"| KC
    SA -->|"Uses privileged audit and recovery APIs"| HIP
    HIP -->|"OIDC Authorization Code + PKCE<br/>JWT validation"| KC

    classDef person fill:#e8f1ff,stroke:#245ea8,color:#102a43
    classDef system fill:#dff7ed,stroke:#13795b,color:#103f32
    classDef external fill:#fff3cd,stroke:#9a6700,color:#4d3600
    class HU,IS,CA,SA person
    class HIP system
    class KC external
```

## Trust boundary'leri

- Browser input trusted değildir. Provider ownership request body'den değil,
  signed `provider_id` token claim'inden türetilir.
- Keycloak kullanıcıları authenticate eder; her servis JWT'leri bağımsız olarak
  doğrular ve application-level authorization uygular.
- Hiçbir servis başka bir servisin database'ini okumaz.
- Public repository ve demo asset'lerinde yalnızca sentetik veri kullanılabilir.
