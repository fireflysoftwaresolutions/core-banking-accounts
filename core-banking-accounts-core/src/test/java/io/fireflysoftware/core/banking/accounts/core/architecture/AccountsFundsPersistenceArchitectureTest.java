package io.fireflysoftware.core.banking.accounts.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class AccountsFundsPersistenceArchitectureTest {
    @Test
    void fundsServicesUseSpringDataBoundariesWithoutAdHocSql() throws Exception {
        Path root =
                Path.of(
                        "src/main/java/io/fireflysoftware/core/banking/accounts/core/services/funds/impl");
        for (String file :
                new String[] {"AccountFundsServiceImpl.java", "FundsDatabaseContext.java"}) {
            assertThat(Files.readString(root.resolve(file)))
                    .as(file)
                    .doesNotContain("DatabaseClient", ".sql(");
        }
    }
}
