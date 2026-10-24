package io.fireflysoftware.core.banking.accounts.models.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

class AssetPriceOwnerMappingTest {
    @Test
    void ownerPropertiesMapOnlyTheActualTenV10Columns() throws Exception {
        var strategy = new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE);
        assertThat(strategy.getAllColumns(AssetPrice.class).stream().map(p -> p.getReference()))
                .containsExactlyInAnyOrder(
                        "asset_price_id",
                        "asset_symbol",
                        "quote_currency",
                        "price",
                        "as_of_datetime",
                        "price_source",
                        "created_by",
                        "created_date",
                        "last_modified_by",
                        "last_modified_date");
        assertThat(
                        AssetPrice.class.getConstructor(
                                UUID.class,
                                String.class,
                                String.class,
                                BigDecimal.class,
                                LocalDateTime.class,
                                String.class))
                .isNotNull();
        assertThat(AssetPrice.class.getConstructor()).isNotNull();
    }
}
