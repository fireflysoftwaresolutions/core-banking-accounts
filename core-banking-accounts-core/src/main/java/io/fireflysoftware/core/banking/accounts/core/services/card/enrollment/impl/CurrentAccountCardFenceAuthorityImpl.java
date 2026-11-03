/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.impl;

import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardFenceAuthority;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Current signed customer identity and same-response PDP provenance; stored ownership is checked under the account lock. */
public final class CurrentAccountCardFenceAuthorityImpl implements AccountCardFenceAuthority {
    public record Profile(String issuer, String platformTenant, UUID resourceTenant, String audience,
            Map<Purpose, Set<String>> clientIds, String clientClaim, String actorClaim, String partyClaim, String kindClaim) {
        public Profile {
            URI trusted = URI.create(Objects.requireNonNull(issuer));
            if (trusted.getHost() == null || trusted.getRawUserInfo() != null || trusted.getRawQuery() != null || trusted.getRawFragment() != null
                    || !("https".equals(trusted.getScheme()) || "http".equals(trusted.getScheme())
                        && Set.of("127.0.0.1", "localhost", "::1", "[::1]").contains(trusted.getHost())))
                throw new IllegalArgumentException("Explicit trusted account fence identity issuer required");
            for (String value : List.of(platformTenant, audience, clientClaim, actorClaim, partyClaim, kindClaim)) text(value);
            id(resourceTenant);
            var copy = new EnumMap<Purpose, Set<String>>(Purpose.class);
            for (Purpose purpose : Purpose.values()) {
                var clients = Set.copyOf(Objects.requireNonNull(clientIds.get(purpose)));
                if (clients.isEmpty()) throw new IllegalArgumentException("Explicit purpose-specific delegating clients required");
                clients.forEach(Profile::text);copy.put(purpose, clients);
            }
            clientIds = Map.copyOf(copy);
        }
        private static void text(String text) {
            if (text == null || text.isBlank() || text.length() > 255 || text.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Explicit account fence identity profile required");
        }
    }

    private final SecurityContextPort principals;
    private final PolicyEvaluationPort policies;
    private final Profile profile;
    private final Clock clock;

    public CurrentAccountCardFenceAuthorityImpl(SecurityContextPort principals, PolicyEvaluationPort policies, Profile profile, Clock clock) {
        this.principals = Objects.requireNonNull(principals);this.policies = Objects.requireNonNull(policies);
        this.profile = Objects.requireNonNull(profile);this.clock = Objects.requireNonNull(clock);
    }

    public Mono<Permit> authorize(UUID accountId, Purpose purpose) {
        return Mono.defer(() -> {
            id(accountId);Objects.requireNonNull(purpose);Instant observed = clock.instant();
            return principals.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(principal -> {
                var identity = identity(principal, purpose);
                Instant until = identity.expires().isBefore(observed.plusSeconds(5)) ? identity.expires() : observed.plusSeconds(5);
                return policy(principal, identity, accountId, purpose).flatMap(revision -> principals.currentPrincipal()
                        .filter(principal::equals).switchIfEmpty(Mono.error(denied())).map(current -> {
                            if (!identity.equals(identity(current, purpose)) || clock.instant().isBefore(observed) || !clock.instant().isBefore(until)) throw denied();
                            return new Permit(current, profile.resourceTenant(), identity.actor(), identity.party(), accountId, purpose,
                                    new AuditActor(current.issuer(), current.subject(), identity.client(), current.tenantId(), null, null), revision, observed, until);
                        }));
            });
        }).timeout(Duration.ofSeconds(5)).onErrorMap(error -> error instanceof ResponseStatusException ? error : unavailable());
    }

    public Mono<Void> requireCurrent(Permit permit) {
        return Mono.defer(() -> {
            if (permit == null || !profile.resourceTenant().equals(permit.tenantId())
                    || clock.instant().isBefore(permit.observedAt()) || !clock.instant().isBefore(permit.effectiveUntil())) return Mono.error(denied());
            return principals.currentPrincipal().filter(permit.principal()::equals).switchIfEmpty(Mono.error(denied()))
                    .flatMap(principal -> {
                        var identity = identity(principal, permit.purpose());
                        if (!identity.actor().equals(permit.actorId()) || !identity.party().equals(permit.partyId())
                                || identity.expires().isBefore(permit.effectiveUntil())) return Mono.error(denied());
                        return policy(principal, identity, permit.accountId(), permit.purpose()).filter(permit.policyRevision()::equals)
                                .switchIfEmpty(Mono.error(denied())).then(principals.currentPrincipal()
                                    .filter(principal::equals).switchIfEmpty(Mono.error(denied())).doOnNext(current -> {
                                        if (!identity.equals(identity(current, permit.purpose())) || !clock.instant().isBefore(permit.effectiveUntil())) throw denied();
                                    })).then();
                    });
        }).timeout(Duration.ofSeconds(5)).onErrorMap(error -> error instanceof ResponseStatusException ? error : unavailable());
    }

    private Mono<String> policy(SecurityPrincipal principal, Identity identity, UUID accountId, Purpose purpose) {
        return policies.evaluate(principal, "accounts.card-enrollment." + purpose.name().toLowerCase(Locale.ROOT),
                "accounts/account/" + accountId, Map.of("tenantId", profile.resourceTenant().toString(), "platformTenantId", principal.tenantId(),
                        "accountId", accountId.toString(), "partyId", identity.party().toString(), "actorId", identity.actor().toString(),
                        "purpose", purpose.name(), "clientId", identity.client()))
                .switchIfEmpty(Mono.error(unavailable())).flatMap(evaluation -> {
                    if (evaluation.decision().effect() == org.fireflyframework.security.api.domain.Decision.Effect.INDETERMINATE)
                        return Mono.error(unavailable());
                    if (!evaluation.decision().granted() || !evaluation.decision().obligations().isEmpty() || evaluation.provenance().isEmpty())
                        return Mono.error(denied());
                    return Mono.just(evaluation.provenance().orElseThrow().policyRevision());
                });
    }

    private record Identity(UUID actor, UUID party, String client, Instant expires) {}
    private Identity identity(SecurityPrincipal principal, Purpose purpose) {
        Instant now = clock.instant();Object expiry = principal.claims().get("exp"), starts = principal.claims().get("nbf");
        String client = claim(principal, profile.clientClaim());
        if (!profile.issuer().equals(principal.issuer()) || principal.subject() == null || principal.subject().isBlank()
                || !profile.platformTenant().equals(principal.tenantId()) || !"HUMAN".equals(principal.claims().get(profile.kindClaim()))
                || !profile.clientIds().get(purpose).contains(client) || !List.of(profile.audience()).equals(principal.claims().get("aud"))
                || !principal.scopes().contains("accounts:card-enrollment:" + purpose.name().toLowerCase(Locale.ROOT))
                || !(expiry instanceof Instant expires) || !(starts instanceof Instant notBefore)
                || !now.isBefore(expires) || now.isBefore(notBefore) || !notBefore.isBefore(expires)) throw denied();
        return new Identity(uuid(claim(principal, profile.actorClaim())), uuid(claim(principal, profile.partyClaim())), client, expires);
    }
    private static String claim(SecurityPrincipal principal, String key) {
        Object value = principal.claims().get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > 255 || text.codePoints().anyMatch(Character::isISOControl)) throw denied();
        return text;
    }
    private static UUID uuid(String value) {
        if (!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw denied();
        UUID id = UUID.fromString(value);id(id);return id;
    }
    private static void id(UUID value) { if (value == null || value.equals(new UUID(0, 0))) throw denied(); }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "Current purpose-specific account card enrollment authority required"); }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Current account card enrollment policy observation unavailable"); }
}
