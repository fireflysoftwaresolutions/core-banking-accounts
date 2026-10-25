package io.fireflysoftware.core.banking.accounts.models.config;

import io.fireflysoftware.core.banking.accounts.models.entities.funds.FundsAccountStatus;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.util.List;

/** Native parameters for managed-funds projections; public API enum conversion is unchanged. */
public final class AccountsStatusR2dbcConverters {
    private AccountsStatusR2dbcConverters() {}

    public static List<Object> converters() {
        return List.of(new FundsStatusWriter());
    }

    @WritingConverter
    private static class FundsStatusWriter implements Converter<FundsAccountStatus, Parameter> {
        public Parameter convert(FundsAccountStatus source) {
            return Parameters.in(PostgresqlObjectId.UNSPECIFIED, source.name());
        }
    }
}
