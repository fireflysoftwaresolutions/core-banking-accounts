package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;

class AssetPriceActorTest {
    private static final String ISSUER = "https://owned-assets.example.test";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private SecurityPrincipal principal(
            String subject, String issuer, Instant expiry, boolean scope) {
        var p = mock(SecurityPrincipal.class);
        when(p.subject()).thenReturn(subject);
        when(p.issuer()).thenReturn(issuer);
        when(p.claims()).thenReturn(expiry == null ? Map.of() : Map.of("exp", expiry));
        when(p.hasScope(AssetPriceActorRules.WRITE_SCOPE)).thenReturn(scope);
        return p;
    }

    @Test
    void preservesExactIdentityAndCountsUnicodeCodePoints() {
        var actor = "😀".repeat(50);
        assertThat(actor.length()).isEqualTo(100);
        assertThat(
                        AssetPriceActorRules.require(
                                principal(actor, ISSUER, NOW.plusSeconds(1), true), ISSUER, CLOCK))
                .isEqualTo(actor);
        assertThatThrownBy(
                        () ->
                                AssetPriceActorRules.require(
                                        principal(actor + "x", ISSUER, NOW.plusSeconds(1), true),
                                        ISSUER,
                                        CLOCK))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void currentExactIssuerScopeAndFiniteExpiryAreRequired() {
        for (var p :
                List.of(
                        principal("actor", "https://other.example.test", NOW.plusSeconds(1), true),
                        principal("actor", ISSUER, NOW.plusSeconds(1), false),
                        principal("actor", ISSUER, null, true),
                        principal("actor", ISSUER, NOW, true),
                        principal("actor", ISSUER, Instant.MAX, true),
                        principal("bad\nactor", ISSUER, NOW.plusSeconds(1), true)))
            assertThatThrownBy(() -> AssetPriceActorRules.require(p, ISSUER, CLOCK))
                    .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void typedCallbackStampsNewIdentityButPreservesLoadedCreationIdentityAndTime() {
        var contexts = mock(SecurityContextPort.class);
        var verified = principal("verified-writer", ISSUER, NOW.plusSeconds(1), true);
        when(contexts.currentPrincipal()).thenReturn(Mono.just(verified));
        var callback = new AssetPriceActorCallback(contexts, ISSUER, CLOCK);
        var fresh = new AssetPrice();
        fresh.setCreatedBy("ignored-client");
        fresh.setDateCreated(LocalDateTime.of(2000, 1, 1, 0, 0));
        assertThat(callback.onBeforeConvert(fresh, SqlIdentifier.unquoted("asset_price")).block())
                .isSameAs(fresh);
        assertThat(fresh.getCreatedBy()).isEqualTo("verified-writer");
        assertThat(fresh.getLastModifiedBy()).isEqualTo("verified-writer");
        assertThat(fresh.getDateCreated()).isNull();
        var loaded = new AssetPrice();
        loaded.setAssetPriceId(UUID.randomUUID());
        loaded.setCreatedBy("original-writer");
        var created = LocalDateTime.of(2020, 1, 1, 0, 0);
        loaded.setDateCreated(created);
        callback.onBeforeConvert(loaded, SqlIdentifier.unquoted("asset_price")).block();
        assertThat(loaded.getCreatedBy()).isEqualTo("original-writer");
        assertThat(loaded.getDateCreated()).isEqualTo(created);
        assertThat(loaded.getLastModifiedBy()).isEqualTo("verified-writer");
    }

    @Test
    void missingTrustedContextCannotConvertAnAssetWrite() {
        var contexts = mock(SecurityContextPort.class);
        when(contexts.currentPrincipal()).thenReturn(Mono.empty());
        assertThatThrownBy(
                        () ->
                                new AssetPriceActorCallback(contexts, ISSUER, CLOCK)
                                        .onBeforeConvert(
                                                new AssetPrice(),
                                                SqlIdentifier.unquoted("asset_price"))
                                        .block())
                .isInstanceOf(ResponseStatusException.class);
    }
}
