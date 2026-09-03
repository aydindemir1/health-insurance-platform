package com.aydindemir.health.claims.application.query;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.util.Objects;
import java.util.UUID;

public record GetInvoiceQuery(ActorContext actor, UUID invoiceId) {
    public GetInvoiceQuery { Objects.requireNonNull(actor); Objects.requireNonNull(invoiceId); }
}
