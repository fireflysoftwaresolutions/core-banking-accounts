package io.fireflysoftware.core.banking.accounts.models.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountStatusEnum;
import io.fireflysoftware.core.banking.accounts.models.config.AccountsStatusR2dbcConverters;
import io.fireflysoftware.core.banking.accounts.models.entities.funds.FundsAccountSnapshot;
import io.fireflysoftware.core.banking.accounts.models.entities.funds.FundsAccountStatus;
import io.fireflysoftware.core.banking.accounts.models.entities.funds.FundsStatusHistoryInsert;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.spi.Parameter;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Update;
import org.springframework.r2dbc.core.binding.BindTarget;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class AccountsStatusR2dbcConvertersTest {
    private final DefaultReactiveDataAccessStrategy strategy =
            new DefaultReactiveDataAccessStrategy(
                    PostgresDialect.INSTANCE, AccountsStatusR2dbcConverters.converters());

    @Test
    void mappedEnumPredicateAndUpdateRetainTypedNativeParameters() {
        var mapper = strategy.getStatementMapper().forType(FundsAccountSnapshot.class);
        var prepared =
                mapper.getMappedObject(
                        mapper.createUpdate(
                                        "account",
                                        Update.update(
                                                "accountStatus", FundsAccountStatus.SUSPENDED))
                                .withCriteria(
                                        Criteria.where("accountStatus")
                                                .is(FundsAccountStatus.OPEN)));
        List<Object> binds = new ArrayList<>();
        prepared.bindTo(
                new BindTarget() {
                    public void bind(String identifier, Object value) {
                        binds.add(value);
                    }

                    public void bind(int index, Object value) {
                        binds.add(value);
                    }

                    public void bindNull(String identifier, Class<?> type) {
                        throw new AssertionError("No null status");
                    }

                    public void bindNull(int index, Class<?> type) {
                        throw new AssertionError("No null status");
                    }
                });
        assertThat(binds)
                .hasSize(2)
                .allSatisfy(
                        value -> {
                            assertThat(value).isInstanceOf(Parameter.class);
                            assertThat(((Parameter) value).getType())
                                    .isEqualTo(PostgresqlObjectId.UNSPECIFIED);
                        });
        assertThat(binds.stream().map(value -> ((Parameter) value).getValue()))
                .containsExactly("SUSPENDED", "OPEN");
    }

    @Test
    void statusHistoryUsesOwnerLabelsWithoutAddingAGlobalStringConverter() {
        var row =
                strategy.getOutboundRow(
                        new FundsStatusHistoryInsert(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                FundsAccountStatus.CLOSED,
                                LocalDateTime.of(2026, 1, 1, 0, 0),
                                "owned converter control"));
        var value =
                row.entrySet().stream()
                        .filter(entry -> entry.getKey().getReference().equals("status_code"))
                        .findFirst()
                        .orElseThrow()
                        .getValue()
                        .getValue();
        assertThat(value).isInstanceOf(Parameter.class);
        assertThat(((Parameter) value).getType()).isEqualTo(PostgresqlObjectId.UNSPECIFIED);
        assertThat(((Parameter) value).getValue()).isEqualTo("CLOSED");
        assertThat(
                        strategy.getConverter()
                                .getConversionService()
                                .canConvert(String.class, Parameter.class))
                .isFalse();
    }

    private record ExistingAccountFilter(AccountStatusEnum accountStatus) {}

    @Test
    void existingPublicAccountEnumFiltersRetainTheirBaselineMapping() {
        org.fireflyframework.core.filters.FilterUtils.initializeTemplate(
                org.mockito.Mockito.mock(
                        org.springframework.data.r2dbc.core.R2dbcEntityTemplate.class));
        var filter =
                org.fireflyframework.core.filters.FilterRequest.builder()
                        .filters(new ExistingAccountFilter(AccountStatusEnum.OPEN))
                        .build();
        var criteria =
                org.fireflyframework.core.filters.FilterUtils.createFilter(
                                io.fireflysoftware.core.banking.accounts.models.entities.core
                                        .Account.class,
                                java.util.function.Function.identity())
                        .getCriteria(filter);
        var mapper =
                strategy.getStatementMapper()
                        .forType(
                                io.fireflysoftware.core.banking.accounts.models.entities.core
                                        .Account.class);
        org.assertj.core.api.Assertions.assertThatCode(
                        () ->
                                mapper.getMappedObject(
                                        mapper.createSelect("account")
                                                .withProjection("account_id")
                                                .withCriteria(criteria)))
                .doesNotThrowAnyException();
    }
}
