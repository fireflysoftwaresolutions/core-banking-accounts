/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.models.repositories.card.enrollment;

import io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment.AccountCardFence;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

/** Technical scope, locks, clock and completed-audit pairing only; business CRUD stays mapped. */
public interface AccountCardFenceNativeRepository extends Repository<AccountCardFence, UUID> {
    record Stamp(UUID contextNonce, Instant databaseTime, Instant commandUntil) {}

    @Query("SELECT * FROM public.accounts_card_fence_enter(:nonce,:tenant,:actor,:party,:account,:fence,:action,:purpose,:receipt,:version,:reason,:deadline,:issuer,:subject,:client,:platform,:policy,:authorityUntil,:commandUntil)")
    Mono<Stamp> enter(@Param("nonce") UUID nonce, @Param("tenant") UUID tenant,
            @Param("actor") UUID actor, @Param("party") UUID party, @Param("account") UUID account,
            @Param("fence") UUID fence, @Param("action") UUID action, @Param("purpose") String purpose,
            @Param("receipt") UUID receipt, @Param("version") Long version, @Param("reason") String reason,
            @Param("deadline") Long deadline, @Param("issuer") String issuer, @Param("subject") String subject,
            @Param("client") String client, @Param("platform") String platform, @Param("policy") String policy,
            @Param("authorityUntil") OffsetDateTime authorityUntil, @Param("commandUntil") OffsetDateTime commandUntil);

    @Query("SELECT public.accounts_card_fence_pair_audit(:nonce,:auditNonce,:event,:sequence,:hash,:replay)")
    Mono<UUID> pairAudit(@Param("nonce") UUID nonce, @Param("auditNonce") UUID auditNonce,
            @Param("event") UUID event, @Param("sequence") long sequence,
            @Param("hash") String hash, @Param("replay") boolean replay);

    @Query("SELECT public.accounts_card_fence_complete(:nonce)")
    Mono<UUID> complete(@Param("nonce") UUID nonce);
}
