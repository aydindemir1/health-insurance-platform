# Sistem bağlamı

Health Insurance Platform healthcare provider'ları bir insurer ile bağlar.

## Aktörler

- **Healthcare provider user:** tedavi ön provizyon talepleri gönderir.
- **Insurance specialist:** pending talepleri inceler, onaylar veya reddeder.
- **Claim approver:** gönderilmiş insurance claim'leri adjudicate eder.
- **System administrator:** Keycloak içinde identity ve access policy'lerini yönetir.

## Mevcut container sınırları

Authorization Service bir ön provizyonun complete lifecycle'ının sahibidir.
Policy Service policy validity, coverage ve limit'lerin sahibidir. Her servis
private PostgreSQL database kullanır; hiçbiri diğerinin schema'sını okumaz.
Claims and Billing Service claims, invoices, payments ve reconciliation record'ların
sahibidir.

## Ana iş akışı

1. Provider insured member için request gönderir.
2. Authorization command ve provider identity'yi doğrular. Provider identity
   request body'den değil authenticated user'ın trusted token claim'inden gelir.
3. Authorization member, service, date, currency ve amount'un eligible olup
   olmadığını Policy'ye senkron olarak sorar.
4. Yalnızca eligible request pending pre-authorization olur.
5. Insurance specialist karar verir; aggregate yalnızca pending request'in
   karara bağlanabilmesini zorunlu kılar.
6. Provider approved pre-authorization'dan claim başlatır. Claims and Billing
   Authorization database'ini okumadan current Authorization snapshot'ı doğrular.
7. Claim approver claim'i adjudicate eder. Approval invoice'u reconcile eder;
   rejection unpaid invoice'u void eder.
8. Financial user'lar farkları çözer ve settlement'a kadar payment kaydeder.
9. Gelecekteki milestone lifecycle change'leri transactional Outbox üzerinden yayınlar.
