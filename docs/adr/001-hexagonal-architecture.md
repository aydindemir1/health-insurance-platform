# ADR-001: Servislerin içinde Clean Architecture sınırlarının kullanılması

- Durum: Kabul edildi
- Tarih: 2026-09-03

## Bağlam

Sistem PostgreSQL, Kafka, RabbitMQ, Redis, Keycloak ve harici sağlık sistemleriyle
entegrasyon kuracaktır. Business rule'lar, bu teknolojileri başlatmaya gerek
kalmadan test edilebilir durumda kalmalıdır.

## Karar

Domain modeli plain Java olarak tutulur. Application use case'leri de framework
bağımsızdır; input port'ları açar ve output port'lara bağımlıdır. Spring MVC
presentation sınırıdır. JPA, security configuration, transaction management ve
harici entegrasyonlar infrastructure concern'leridir. Persistence entity'leri
domain object'lerinden ayrıdır.

Bağımlılık yönü şöyledir:

```text
Presentation -> Application -> Domain
Infrastructure -> Application / Domain
```

ArchUnit kuralları, domain ve application kodunun Spring, JPA, presentation veya
infrastructure package'larına bağımlı olmadığını doğrular.

## Sonuçlar

- Business rule'lar Spring veya veritabanı olmadan hızlı biçimde test edilebilir.
- Use case'ler Spring application context olmadan test edilebilir.
- Infrastructure, domain yeniden yazılmadan değiştirilebilir.
- Açık mapping küçük miktarda ek kod oluşturur.
- Transaction annotation'ları application service yerine infrastructure
  decorator içinde bulunur.
- Ayrım pragmatik kalmalıdır; basit davranışlar yalnızca katman sayısını artırmak
  için interface gerektirmez.
