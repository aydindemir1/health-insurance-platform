# Operations Portal lokal doğrulaması

Bu rehber backend domain behavior'ını değiştirmeden React/TypeScript client'ını doğrular ve açıklar.

## Okuma sırası

1. `src/app/router/AppRouter.tsx` — lazy route'lar ve page authorization.
2. `src/features/authentication/model/AuthProvider.tsx` — Keycloak session ve HTTP token integration.
3. `src/shared/api/http-client.ts` — timeout, bearer token, correlation ID, RFC 9457 parsing ve response schema validation.
4. `src/entities/*/api` — server-state contract'ları ve query key'leri.
5. `src/features/submit-pre-authorization` ve `review-pre-authorization` — command-oriented UI behavior.
6. `src/pages` — URL-backed filter'lar, pagination ve composed screen'ler.
7. `src/app/architecture.test.ts` — enforce edilen FSD dependency direction.

## Otomatik checkpoint

`apps/operations-portal` dizininden:

```powershell
npm run lint
npm test
npm run build:budget
```

2026-09-14 tarihinde doğrulandı:

```text
Oxlint:          passed
Vitest:          15 files, 27 tests passed
TypeScript/Vite: production build passed, 258 modules transformed
Bundle budget:   20 JavaScript chunks; largest 70.54 KiB gzip (limit 100 KiB)
Playwright:      4 real Chrome scenarios passed across focused runs
```

Browser workflow gerçek Keycloak Authorization Code + PKCE ve live Authorization/Policy service'lerini kullandı. Hospital user covered request submit etti, `PENDING` gördü, sign out oldu ve insurance specialist aynı request'i `APPROVED` durumuna getirdi. Password ve token'lar yalnızca process memory içinde bulundu.

`e2e/operations-portal-access.spec.ts` ayrıca şunları doğrular:

- provider-scoped, policy-filtered pre-authorization listing;
- exact Elasticsearch-backed policy search;
- no-result query için explicit empty state;
- hospital user için Audit navigation item'ının olmaması ve `/forbidden` routing;
- `SYSTEM_ADMIN` için visible, populated Authorization audit evidence.

Failure-path checkpoint; generated ve caller-supplied correlation ID'leri, RFC 9457 detail preservation, runtime response-schema rejection, caller cancellation, bounded timeout cancellation, `401` session recovery invocation, retryable error UI ve render-level Error Boundary davranışını doğrular. Fatal render logging error class ve component stack'in varlığıyla minimize edilir; error message, component detail ve healthcare data browser console'a yazılmaz.

Responsive/accessibility checkpoint dördüncü gerçek Chrome scenario ekler. 390 × 844 çözünürlükte sıfır axe violation (color contrast dahil), document overflow olmaması ve primary navigation boyunca doğru keyboard focus progression raporlanır. Work-queue action column explicit header'a sahiptir ve secondary table text WCAG AA contrast'i karşılar.

Performance checkpoint route-level lazy loading ve stable vendor chunk separation'ı doğrular. `npm run build:budget`, production TypeScript/Vite build'i çalıştırır ve herhangi bir JavaScript chunk 100 KiB gzip sınırını aşarsa fail eder; böylece bundle growth local development ve CI'da görünür hale gelir.

Final portal smoke checkpoint mobile scenario'yu default API origin (`http://localhost:9080/api/v1`) ile çalıştırdı. Gerçek Keycloak login tamamlandı, APISIX üzerinden 59 provider-owned pre-authorization yüklendi, pagination render edildi, API error raporlanmadı ve aynı axe, overflow ve keyboard check'leri geçti. `27-operations-portal-apisix-mobile-smoke.png` bu durumu kaydeder.

APISIX isolated frontend learning sırasında bilinçli olarak durdurulmuşsa `VITE_API_BASE_URL=http://localhost:8081/api/v1` ve `VITE_SEARCH_API_BASE_URL=http://localhost:8084/api/v1` ayarlayın. Portfolio demo default APISIX origin'i kullanır ve gateway policy'yi ayrıca test eder.

## Bulunan contract defect'i

Zod 4 `z.uuid()`, RFC version/variant bit'lerini zorunlu kılar; Java `java.util.UUID` ise bu restriction olmadan canonical UUID text kabul eder. Sentetik member identifier bu nedenle API çağrısı yapılmadan browser'da reddedildi. `shared/lib/validation.ts` artık form, URL filter ve response schema'larında kullanılan ortak `javaUuid()` schema'sını expose eder. Focused unit test hem Java-compatible form'un kabulünü hem malformed input rejection'ı korur.

## Architecture yorumu

TanStack Query remote state ve mutation lifecycle'ın sahibidir. React Hook Form short-lived form state'in sahibidir. Zod hem user input hem untrusted API payload validate eder. Keycloak authentication merkezi olsa da navigation visibility yalnızca UX control'dür; backend authorization authoritative kalır. Feature ve entity layer `shared`'e bağımlı olabilir; architecture test upward import'ları reddeder.

## .NET karşılaştırması

| React implementation | ASP.NET odaklı karşılık |
| --- | --- |
| TanStack Query | typed API client + server-state cache |
| React Hook Form + Zod | EditForm/Formik + FluentValidation-style schema |
| Keycloak JS + PKCE | MSAL/OIDC browser client |
| Error boundary | Blazor error boundary veya top-level UI exception boundary |
| `ApiError`/Problem Details | `ProblemDetails` client mapping |
| FSD architecture test | project-reference/dependency architecture test |
