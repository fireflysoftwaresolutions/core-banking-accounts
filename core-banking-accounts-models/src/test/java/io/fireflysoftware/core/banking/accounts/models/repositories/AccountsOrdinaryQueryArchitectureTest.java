package io.fireflysoftware.core.banking.accounts.models.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import io.fireflysoftware.core.banking.accounts.models.repositories.crypto.AssetPriceRepository;
import io.fireflysoftware.core.banking.accounts.models.repositories.space.AccountSpaceRepository;
import io.fireflysoftware.core.banking.accounts.models.repositories.space.SpaceTransactionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.repository.Query;

import java.util.Map;
import java.util.Set;

class AccountsOrdinaryQueryArchitectureTest {
    @Test
    void ordinaryQueriesUseDerivedMethodsOrTypedSpringDataFragments() {
        var methods =
                Map.of(
                        AssetPriceRepository.class,
                        Set.of(
                                "findAllAssetPrices",
                                "countAllAssetPrices",
                                "findLatestPrice",
                                "deleteByAsOfDatetimeBefore"),
                        AccountSpaceRepository.class,
                        Set.of("calculateTotalBalance"),
                        SpaceTransactionRepository.class,
                        Set.of("calculateTotalDeposits", "calculateTotalWithdrawals"));
        int checked = 0;
        for (var entry : methods.entrySet()) {
            for (var method : entry.getKey().getMethods()) {
                if (entry.getValue().contains(method.getName())) {
                    checked++;
                    assertThat(method.getAnnotation(Query.class))
                            .as(entry.getKey().getSimpleName() + "." + method.getName())
                            .isNull();
                }
            }
        }
        assertThat(checked).isEqualTo(7);
    }
}
