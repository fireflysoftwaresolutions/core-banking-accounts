/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.openapi;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@ActiveProfiles("openapi-gen")
@SpringBootTest(classes=OpenApiGenApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties={"spring.main.web-application-type=reactive","springdoc.api-docs.version=OPENAPI_3_0"})
class AccountCardFenceProducerContractTest {
    @Autowired ApplicationContext context;
    JsonNode document()throws Exception {byte[] raw=WebTestClient.bindToApplicationContext(context).configureClient()
            .codecs(c->c.defaultCodecs().maxInMemorySize(4194304)).responseTimeout(Duration.ofSeconds(30)).build()
            .get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();return new ObjectMapper().readTree(raw);}
    @Test void originalControllerEmitsFourTypedPurposeSpecificOperationsAndNamedBearer()throws Exception {
        var paths=document().path("paths");String base="/api/v1/accounts/{accountId}/card-enrollments";
        for(var operation:List.of(new String[]{base,"post","acquireAccountCardEnrollment","AccountCardEnrollmentAcquire"},
                new String[]{base+"/{actionId}/claim","post","claimAccountCardEnrollment","AccountCardEnrollmentClaim"},
                new String[]{base+"/{actionId}/cancel","post","cancelAccountCardEnrollment","AccountCardEnrollmentCancel"},
                new String[]{base+"/{actionId}/operations/{operationActionId}","get","recoverAccountCardEnrollment",""})) {
            var actual=paths.path(operation[0]).path(operation[1]);assertEquals(operation[2],actual.path("operationId").asText());
            assertTrue(actual.path("security").findValues("accountCardEnrollmentBearer").size()>0);
            assertEquals("#/components/schemas/AccountCardEnrollmentObservation",actual.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText());
            if(operation[1].equals("get"))assertTrue(actual.path("requestBody").isMissingNode());
            else assertEquals("#/components/schemas/"+operation[3],actual.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText());
            for(JsonNode parameter:actual.path("parameters")){assertEquals("path",parameter.path("in").asText());assertFalse(Set.of("tenantId","actorId","partyId").contains(parameter.path("name").asText()));}
        }
    }
    @Test void historicalAndCurrentAuthorityTimesAreAbsoluteInstants()throws Exception {
        var schemas=document().path("components").path("schemas");
        for(var field:List.of(new String[]{"AccountCardEnrollmentFence","recordedAt"},new String[]{"AccountCardEnrollmentActionReceipt","recordedAt"},new String[]{"AccountCardEnrollmentObservation","observedAt"},new String[]{"AccountCardEnrollmentObservation","eligibilityValidUntil"}))
            assertEquals("#/components/schemas/FireflyInstant",schemas.path(field[0]).path("properties").path(field[1]).path("$ref").asText());
        assertEquals("string",schemas.path("FireflyInstant").path("type").asText());assertEquals("date-time",schemas.path("FireflyInstant").path("format").asText());
    }
}
