# ADR-010: Harici API security boundary olarak APISIX

- Durum: Kabul edildi
- Tarih: 2026-09-09

## Bağlam

Browser daha önce dört Spring servisini ayrı ayrı publish edilmiş host port'ları
üzerinden çağırıyordu. Bu public routing bilgisini çoğaltıyor, attack surface'i
genişletiyor ve traffic limit'leri her application'a bırakıyordu. Platform,
provider ownership veya aggregate authorization'ı owner servislerden taşımadan
tek açık external boundary'ye ihtiyaç duyar.

## Karar

Apache APISIX 3.18 file-driven standalone modda çalıştırılır. Declarative
configuration Git'te versionlanır, local portfolio topology için etcd gerekli
değildir ve Admin API kapatılmıştır. Business API'ler için yalnızca APISIX
`9080` portu publish edilir; dört Spring API portu yalnızca Compose network
içinde expose edilir.

Her external business route şu policy'leri paylaşır:

- OIDC discovery ve JWKS üzerinden Keycloak bearer-token signature, expiry,
  issuer ve dedicated `health-insurance-api` audience validation;
- UUID `X-Correlation-ID` generation veya preservation;
- açık local-development CORS origin, method ve header'ları;
- source address başına 60 saniyelik local counter içinde 120 request;
- 1 MiB request-body limit ve bounded upstream timeout'lar;
- defensive response header'ları ve no-store caching;
- upstream çağrılmadan önce oluşan failure'lar için RFC 9457 JSON adaptation.

APISIX authentication ve traffic governance uygular. Spring Security token'ı,
endpoint role'lerini ve provider ownership'i doğrulamaya devam eder. Gateway
domain authorization'ın sahibi olmamalıdır; internal service-to-service call'lar
external gateway üzerinden loop etmek yerine Compose network içinde kalır.

OIDC plugin schema `client_secret` gerektirir; bearer-only `use_jwks`
introspection'ı atlar ve bu compatibility value'yu Keycloak'a göndermez. Değer
yine environment variable üzerinden sağlanır ve hiçbir credential commit edilmez.

API bearer-only Keycloak client ile temsil edilir. Browser client ve local demo
client bu client'ı access token'ın `aud` claim'ine map eder. APISIX claim'i
zorunlu kılar ve kendi `client_id` değeriyle eşleştirir. Böylece aynı realm
tarafından ilgisiz client için üretilmiş valid token gateway boundary'yi geçemez.

Executable gateway demo, Keycloak'ın ilgisiz `admin-cli` client'ından ikinci bir
token alır ve APISIX'in mismatched audience için `403` döndürdüğünü, purpose-built
API token'ını ise kabul ettiğini doğrular.

## Sonuçlar

- Portal ve demo tek stable API origin kullanır.
- Gateway compromise veya misconfiguration service-level authorization check'leri
  ortadan kaldırmaz.
- Standalone local rate counter'lar APISIX instance başınadır. Multi-replica
  deployment shared Redis policy kullanmalı veya instance başına quota'yı kabul etmelidir.
- Local Keycloak discovery HTTP kullanır ve bilinçli olarak APISIX warning üretir.
  Production gateway ile identity provider arasında trusted TLS gerektirir.
- Küçük custom response adapter gateway infrastructure'dır, business logic
  değildir ve izole CI startup testine sahiptir.

## Değerlendirilen alternatifler

- **Her Spring servisini expose etmek:** en basit çözümdür ancak tek traffic/security
  edge sağlamaz.
- **Tüm authorization'ı APISIX'e taşımak:** provider ownership ve state-dependent
  permission'lar application use case'lerine ait olduğu için reddedildi.
- **etcd/Admin API kullanan geleneksel APISIX:** dynamic control plane için
  yararlıdır ancak local, Git-versioned topology için gereksiz operational state ekler.
- **Spring Cloud Gateway:** Java-only estate için uygundur ancak APISIX hedef
  gateway'i doğrudan gösterir ve ek JVM runtime gerektirmez.
