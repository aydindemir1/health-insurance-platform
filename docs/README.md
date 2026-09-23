# Mühendislik Dokümantasyonu

Dokümantasyon proje sonunda yeniden oluşturulmak yerine her milestone ile birlikte güncel tutulur.

## Mimari haritası

- [C4 sistem bağlamı](architecture/c4-context.md)
- [C4 container görünümü](architecture/c4-container.md)
- [Clean Architecture sınırları](architecture/clean-architecture.md)
- [Authorization bileşenleri](architecture/authorization-service.md)
- [Policy bileşenleri](architecture/policy-service.md)
- [Claims and Billing bileşenleri](architecture/claims-billing-service.md)
- [Notification Worker bileşenleri](architecture/notification-worker.md)
- [Veri sahipliği ve ER modeli](architecture/data-model.md)
- [Workflow sequence diyagramları](architecture/workflow-sequences.md)
- [Frontend mimarisi](architecture/frontend-architecture.md)
- [Lokal deployment](architecture/local-deployment.md)
- [Kubernetes deployment ve security](deployment/kubernetes.md)
- [CI/CD ve software supply chain](architecture/ci-cd-supply-chain.md)
- [GitHub Actions doğrulaması](development/github-actions-verification.md)
- [Jenkins ve SonarQube lokal doğrulaması](development/jenkins-sonarqube-local-verification.md)
- [Nexus lokal doğrulaması](development/nexus-local-verification.md)
- [Harbor lokal doğrulaması](development/harbor-local-verification.md)
- [Argo CD lokal doğrulaması](development/argocd-local-verification.md)
- [Lokal sorun giderme](development/troubleshooting.md)
- [Policy Service lokal öğrenme ve doğrulama](development/policy-service-local-verification.md)
- [Authorization Service lokal öğrenme ve doğrulama](development/authorization-service-local-verification.md)
- [Claims and Billing Service lokal öğrenme ve doğrulama](development/claims-billing-service-local-verification.md)
- [Backend uçtan uca lokal doğrulama](development/backend-end-to-end-local-verification.md)
- [Operations Portal lokal öğrenme ve doğrulama](development/operations-portal-local-verification.md)
- [Search ve messaging recovery runbook](operations/search-and-messaging-recovery.md)
- [Event-driven messaging](architecture/event-driven-messaging.md)
- [Search, cache ve observability](architecture/search-and-observability.md)
- [API gateway ve security boundary](architecture/api-gateway-and-security.md)
- [Veri yönetişimi, gizlilik ve KVKK threat model'i](security/data-governance-and-kvkk.md)

## Öğrenme ve gösterim

- [Policy Service iş analizi](business/policy-service-business-analysis.md)
- [Authorization Service iş analizi](business/authorization-service-business-analysis.md)
- [Claims and Billing Service iş analizi](business/claims-billing-service-business-analysis.md)
- [Operations Portal iş analizi](business/operations-portal-business-analysis.md)
- [Teknik walkthrough](project-technical-walkthrough.md)
- [Demo senaryosu](demo/demo-scenario.md)
- [Milestone 12 CI/CD demosu](demo/milestone-12-ci-cd-demo.md)
- [Ekran görüntüsü kataloğu](screenshots/README.md)

## Mimari kararlar

- [ADR-001: Clean Architecture servis sınırları](adr/001-hexagonal-architecture.md)
- [ADR-002: Authenticated identity üzerinden provider sahipliği](adr/002-provider-ownership-from-authenticated-identity.md)
- [ADR-003: Kararlar için optimistic concurrency](adr/003-optimistic-concurrency-for-decisions.md)
- [ADR-004: Feature-Sliced Operations Portal](adr/004-feature-sliced-operations-portal.md)
- [ADR-005: Senkron policy coverage değerlendirmesi](adr/005-synchronous-policy-coverage-evaluation.md)
- [ADR-006: Claims and Billing sahipliği ve entegrasyonu](adr/006-claims-billing-ownership-and-integration.md)
- [ADR-007: Transactional Outbox ve idempotent Kafka consumer](adr/007-transactional-outbox-and-idempotent-kafka-consumer.md)
- [ADR-008: RabbitMQ notification task delivery](adr/008-rabbitmq-notification-task-delivery.md)
- [ADR-009: Cache, search ve observability sınırları](adr/009-cache-search-and-observability.md)
- [ADR-010: APISIX external security boundary](adr/010-apisix-security-boundary.md)
- [ADR-011: Service-owned append-only audit journal'ları](adr/011-service-owned-append-only-audit.md)
- [ADR-012: Versioned search rebuild ve controlled message recovery](adr/012-versioned-search-rebuild-and-controlled-message-recovery.md)
- [ADR-013: Kustomize ve güvenli stateless workload'lar](adr/013-kustomize-and-secure-stateless-workloads.md)
- [ADR-014: Lokal CI/CD ve software supply chain](adr/014-local-ci-cd-software-supply-chain.md)

## Milestone dokümantasyonu tamamlanma kriterleri

Her milestone şu review ile kapanır:

- README durumu, capability'ler, command'lar, endpoint'ler ve roadmap günceldir.
- C4, component, data, sequence, frontend ve deployment diyagramları code'u yansıtır ve planlanmış component'leri uygulanmış gibi göstermez.
- ADR'ler her önemli boundary veya trade-off kararını kaydeder.
- Sentetik demo data, secret veya gerçek sağlık bilgisi kullanmadan yeni teslim edilen behavior'ı çalıştırır.
- Demo senaryosu happy path, negative path, prerequisite ve güvenli reset prosedürünü içerir.
- Screenshot'lar yalnızca uygulanmış UI ve sentetik data gösterir.
- Technical walkthrough, .NET eşlemeleri, interview pitch, muhtemel sorular, limitation'lar ve sonraki milestone güncellenir.
- Markdown link'leri, Mermaid syntax'ı, JSON, script'ler, test'ler ve production build'ler commit öncesinde doğrulanır.

Lokal link'leri, JSON ve demo-script syntax'ını, beklenen screenshot set'ini ve tüm Mermaid diyagramlarını kontrol etmek için PowerShell'den `./scripts/validate-documentation.ps1` çalıştırın. Hızlı syntax-and-file kontrolü için `-SkipMermaid` kullanılabilir.
