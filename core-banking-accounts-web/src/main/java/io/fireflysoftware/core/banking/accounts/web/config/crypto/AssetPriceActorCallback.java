package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;

import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.core.Ordered;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.Clock;

public final class AssetPriceActorCallback implements BeforeConvertCallback<AssetPrice>, Ordered {
    private final SecurityContextPort contexts;
    private final String issuer;
    private final Clock clock;

    public AssetPriceActorCallback(SecurityContextPort contexts, String issuer, Clock clock) {
        this.contexts = contexts;
        this.issuer = issuer;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return 90;
    }

    @Override
    public Mono<AssetPrice> onBeforeConvert(AssetPrice asset, SqlIdentifier table) {
        return contexts.currentPrincipal()
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Trusted asset price actor required")))
                .map(
                        principal -> {
                            var actor = AssetPriceActorRules.require(principal, issuer, clock);
                            if (asset.getAssetPriceId() == null) {
                                asset.setCreatedBy(actor);
                                asset.setDateCreated(null);
                                asset.setDateUpdated(null);
                            } else if (asset.getCreatedBy() == null
                                    || asset.getDateCreated() == null) {
                                throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Loaded asset creation identity required");
                            }
                            asset.setLastModifiedBy(actor);
                            return asset;
                        });
    }
}
