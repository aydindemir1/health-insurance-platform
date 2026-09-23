# ADR-013: Kustomize ve güvenli stateless Kubernetes workload'ları

- Durum: Kabul edildi
- Tarih: 2026-09-10

## Bağlam

Platform bağımsız build edilen application container'larına ve local integration
için Compose environment'a zaten sahiptir. Kubernetes, bounded-context data
ownership'i değiştirmeden ve hand-written single-node database'leri production
data platform gibi göstermeden scheduling, rollout, isolation ve health semantics
eklemelidir.

## Karar

Production-oriented base ve local overlay ile Kubernetes-native Kustomize kullanılır.
Kustomize `kubectl` ile birlikte gelir, ikinci packaging runtime gerektirmez ve
Milestone 12'de sunulan Argo CD Application tarafından doğrudan tüketilir.

Kubernetes package yalnızca stateless platform workload'larının sahibidir: beş
Spring application, React/Nginx portal ve APISIX. PostgreSQL database'leri, Kafka,
RabbitMQ, Redis, Elasticsearch, Keycloak ve APM açık external service contract'tır.
Local overlay mevcut Compose dependency'lerine bağlanabilir ancak production
boundary'yi yeniden tanımlamaz.

Her pod API token veya RBAC grant içermeyen dedicated ServiceAccount kullanır.
Workload'lar fixed non-root numeric user olarak çalışır, tüm capability'leri drop
eder, privilege escalation'ı yasaklar, RuntimeDefault seccomp kullanır ve yalnızca
bounded writable temporary volume mount eder. Namespace Restricted Pod Security
Standard'ı uygular. Default-deny NetworkPolicy'ler yalnızca known caller ve
dependency port'ları için açılır. HTTP workload'larda startup, readiness ve
liveness probe vardır. Worker process-only probe yerine 8085 portunda Actuator
health sunar. Namespace ResourceQuota ve LimitRange guardrail'larına sahiptir.
Third-party workload image'ları registry digest ile pin edilir; application image
immutability CI/CD promotion flow tarafından sağlanır.

Credential'lar Kustomize tarafından asla render edilmez. Deployment'lar operator
veya external secret controller tarafından sağlanması gereken named Secret'lara
referans verir. Local helper ignore edilen `.env` value'larını okur, base64 data'yı
doğrudan Kubernetes API'ye submit eder ve generated Secret manifest'i diske yazmaz.

Local portfolio overlay self-signed TLS eklemez. Bu, hedeflenen production control
yerine local certificate-distribution mechanics'i test eder ve browser, JVM ile
APISIX trust store'larını gereksiz karmaşıklaştırır. Production, APISIX üzerinde
otomatik issued trusted certificate terminate etmeli ve private material'i
organizasyonun external secret/workload-identity yolundan almalıdır.

## Sonuçlar

- Application manifest'leri review edilebilir ve Helm olmadan render olur.
- Stateful platform lifecycle, encryption, backup ve high availability owner
  operator veya managed service sorumluluğunda kalır.
- CPU HPA Metrics Server gerektirir. Rabbit consumer scaling sonunda CPU yerine
  queue-depth metric kullanmalıdır; bu nedenle worker'a HPA verilmez.
- Local overlay HPA minimum ve maximum replica'yı bire sabitler ve `Recreate`
  kullanır; resource-constrained single-node cluster'da cold-start scale-out'u
  önler ancak production rollout semantics'i zayıflatmaz.
- Port-bounded IP egress useful defence in depth sağlar ancak identity-aware
  değildir. Production cluster workload identity, TLS ve egress gateway eklemelidir.
- Local overlay Kafka-driven application path'lerini kapatır; çünkü Compose broker
  host-only listener advertise eder ve bu Kind pod içinde geçerli değildir.
  Messaging behavior mevcut integration environment tarafından kapsanır.

## Reddedilen alternatifler

- Her dependency için hand-written Kubernetes StatefulSet: repository'nin sahip
  olmadığı production operation, backup ve quorum guarantee'lerini ima eder.
- Broad namespace permission'lı tek shared ServiceAccount: application'lar
  Kubernetes API çağırmadığı için RBAC permission'a ihtiyaç duymaz.
- Commit edilmiş development Secret'ları: reversible convenience credential
  publish etmeyi haklı çıkarmaz.
- Mandatory local prerequisite olarak Helm: bu ölçekte templating az değer ekler;
  Kustomize zaten `kubectl` ile kullanılabilir.
