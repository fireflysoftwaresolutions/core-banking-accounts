package io.fireflysoftware.core.banking.accounts.sdk;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class ManagedFundsContractTest {
    @Test
    void producerGeneratedSdkNeverExposesAuthenticationPrincipalAsClientInput() throws Exception {
        String contract=Files.readString(Path.of("src/main/resources/openapi.yml"));
        assertThat(contract).contains("operationId: verifyFundsEnrollment", "operationId: beginFundsEnrollment", "operationId: linkFundsEnrollment", "operationId: controlAccountFunds")
                .doesNotContain("name: jwt", "    Jwt:");
    }
}
