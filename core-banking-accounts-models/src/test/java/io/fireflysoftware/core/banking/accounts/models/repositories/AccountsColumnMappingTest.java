package io.fireflysoftware.core.banking.accounts.models.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import io.fireflysoftware.core.banking.accounts.models.entities.space.SpaceTransaction;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;

class AccountsColumnMappingTest {
    @Test
    void transactionTimestampUsesItsActualFlywayColumn() {
        var context = new R2dbcMappingContext();
        var property =
                context.getRequiredPersistentEntity(SpaceTransaction.class)
                        .getRequiredPersistentProperty("transactionDateTime");
        assertThat(property.getColumnName().getReference()).isEqualTo("transaction_datetime");
    }
}
