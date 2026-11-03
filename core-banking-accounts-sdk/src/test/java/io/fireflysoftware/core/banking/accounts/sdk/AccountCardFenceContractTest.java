/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.sdk;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.accounts.sdk.invoker.ApiClient;
import io.fireflysoftware.core.banking.accounts.sdk.invoker.auth.HttpBearerAuth;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

/** Reflection keeps the original producer bootstrap compilable; all final SDK assertions are mandatory. */
class AccountCardFenceContractTest {
    static Class<?> model(String name)throws Exception{return Class.forName("io.fireflysoftware.core.banking.accounts.sdk.model.AccountCardEnrollment"+name);}
    static Class<?> api()throws Exception{return Class.forName("io.fireflysoftware.core.banking.accounts.sdk.api.AccountCardEnrollmentApi");}
    @Test void fourOfficialOperationsHaveOnlyExplicitActionAndRequestInputs()throws Exception {
        for(var operation:List.of(new Object[]{"acquireAccountCardEnrollment",new Class<?>[]{UUID.class,model("Acquire")}},
                new Object[]{"claimAccountCardEnrollment",new Class<?>[]{UUID.class,UUID.class,model("Claim")}},
                new Object[]{"cancelAccountCardEnrollment",new Class<?>[]{UUID.class,UUID.class,model("Cancel")}},
                new Object[]{"recoverAccountCardEnrollment",new Class<?>[]{UUID.class,UUID.class,UUID.class}})) {
            var method=api().getMethod((String)operation[0],(Class<?>[])operation[1]);assertEquals(Mono.class,method.getReturnType());
            assertEquals(model("Observation"),((ParameterizedType)method.getGenericReturnType()).getActualTypeArguments()[0]);
        }
        String source=Files.readString(Path.of("target/generated-sources/src/gen/java/main/io/fireflysoftware/core/banking/accounts/sdk/api/AccountCardEnrollmentApi.java"));
        assertEquals(4,source.split("String\\[\\] localVarAuthNames = new String\\[\\] \\{ \\\"accountCardEnrollmentBearer\\\" \\};",-1).length-1);
        assertFalse(source.contains("X-Idempotency-Key"));
    }
    @Test void authoritativeReceiptAndCurrentObservationInstantsRequireAnExplicitOffset()throws Exception {
        var mapper=ApiClient.createDefaultMapper(null);Instant expected=Instant.parse("2026-11-02T23:00:00Z");
        for(var field:List.of(new String[]{"Fence","RecordedAt","recordedAt"},new String[]{"ActionReceipt","RecordedAt","recordedAt"},
                new String[]{"Observation","ObservedAt","observedAt"},new String[]{"Observation","EligibilityValidUntil","eligibilityValidUntil"})) {
            var type=model(field[0]);var getter=type.getMethod("get"+field[1]);assertEquals(Instant.class,getter.getReturnType());
            var value=mapper.readValue("{\""+field[2]+"\":\"2026-11-02T15:00:00-08:00\"}",type);assertEquals(expected,getter.invoke(value));
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->mapper.readValue("{\""+field[2]+"\":\"2026-11-02T15:00:00\"}",type));
        }
    }
    @Test void namedOperationBearerIsAppliedByTheActualGeneratedRequestBuilder()throws Exception {
        var captured=new AtomicReference<ClientRequest>();
        var web=WebClient.builder().exchangeFunction(request->{captured.set(request);return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE,"application/json").body("{}").build());}).build();
        var client=new ApiClient(web);((HttpBearerAuth)client.getAuthentication("accountCardEnrollmentBearer")).setBearerToken("controlled-sdk-credential");
        var instance=api().getConstructor(ApiClient.class).newInstance(client);var account=UUID.randomUUID();var action=UUID.randomUUID();
        ((Mono<?>)api().getMethod("recoverAccountCardEnrollment",UUID.class,UUID.class,UUID.class).invoke(instance,account,action,action)).block(Duration.ofSeconds(5));
        assertEquals("Bearer controlled-sdk-credential",captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION));assertEquals(HttpMethod.GET,captured.get().method());
        assertEquals("/api/v1/accounts/"+account+"/card-enrollments/"+action+"/operations/"+action,captured.get().url().getPath());
    }
}
