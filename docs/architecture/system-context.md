# System context

The Health Insurance Platform connects healthcare providers with an insurer.

## Actors

- **Healthcare provider user:** submits treatment pre-authorization requests.
- **Insurance specialist:** reviews, approves, or rejects pending requests.
- **Claim approver:** adjudicates submitted insurance claims.
- **System administrator:** manages identities and access policies in Keycloak.

## Current container boundaries

The Authorization Service owns the complete lifecycle of a pre-authorization.
The Policy Service owns policy validity, coverages, and limits. Each service has
a private PostgreSQL database; neither reads the other's schema. The Claims and
Billing Service owns claims, invoices, payments, and reconciliation records.

## Primary workflow

1. A provider submits a request for an insured member.
2. Authorization validates the command and provider identity. The
   provider identity comes from the authenticated user's trusted token claim
   rather than the request body.
3. Authorization asks Policy synchronously whether the member, service, date,
   currency, and amount are eligible.
4. Only an eligible request becomes a pending pre-authorization.
5. An insurance specialist makes a decision; the aggregate enforces that only
   a pending request can be decided.
6. A provider starts a claim from an approved pre-authorization. Claims and
   Billing verifies the current Authorization snapshot without reading its database.
7. A claim approver adjudicates the claim. Approval reconciles the invoice;
   rejection voids an unpaid invoice.
8. Financial users resolve differences and record payments until settlement.
9. A future milestone publishes lifecycle changes through transactional Outbox.
