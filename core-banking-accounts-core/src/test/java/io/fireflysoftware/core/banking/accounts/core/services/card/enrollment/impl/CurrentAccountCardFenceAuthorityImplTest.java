package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.impl;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardFenceAuthority.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Controlled native identity/PDP unit cases; signed receiver proof is qualified separately. */
class CurrentAccountCardFenceAuthorityImplTest {
    final Instant now=Instant.parse("2026-11-02T12:00:00Z");
    final UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),party=UUID.randomUUID(),account=UUID.randomUUID();
    final Map<Purpose,Set<String>> clients=Map.of(Purpose.ACQUIRE,Set.of("domain-cards"),Purpose.CANCEL,Set.of("domain-cards"),Purpose.READ,Set.of("domain-cards","core-cards"),Purpose.CLAIM,Set.of("core-cards"));
    final CurrentAccountCardFenceAuthorityImpl.Profile profile=new CurrentAccountCardFenceAuthorityImpl.Profile("https://identity.example.test/realm","customer-platform",tenant,"core-banking-accounts",clients,"client_id","actor_id","party_id","principal_kind");
    final AtomicReference<SecurityPrincipal> principal=new AtomicReference<>(principal(Map.of(),Set.of("accounts:card-enrollment:acquire","accounts:card-enrollment:claim","accounts:card-enrollment:cancel","accounts:card-enrollment:read")));
    final AtomicReference<PolicyEvaluation> evaluation=new AtomicReference<>(new PolicyEvaluation(Decision.permit(),Optional.of(new PolicyEvaluationProvenance("policy-r1","decision-1"))));
    final AtomicInteger evaluations=new AtomicInteger();
    final AtomicReference<Map<String,Object>> lastContext=new AtomicReference<>();
    final AtomicReference<String> lastAction=new AtomicReference<>();
    final PolicyEvaluationPort policies=(p,a,r,c)->{evaluations.incrementAndGet();lastAction.set(a);lastContext.set(c);assertEquals("accounts/account/"+account,r);return Mono.justOrEmpty(evaluation.get());};
    SecurityPrincipal principal(Map<String,Object> changes,Set<String> scopes){var claims=new HashMap<String,Object>(Map.of("exp",now.plusSeconds(60),"nbf",now.minusSeconds(1),"aud",List.of("core-banking-accounts"),"client_id","domain-cards","actor_id",actor.toString(),"party_id",party.toString(),"principal_kind","HUMAN"));claims.putAll(changes);
        return new SecurityPrincipal("opaque-customer-subject","https://identity.example.test/realm","customer-platform",Set.of(),scopes,claims,now.minusSeconds(60),null,Set.of(),Map.of());}
    CurrentAccountCardFenceAuthorityImpl service(){return new CurrentAccountCardFenceAuthorityImpl(()->Mono.justOrEmpty(principal.get()),policies,profile,Clock.fixed(now,ZoneOffset.UTC));}
    void refused(Mono<?> request){var error=assertThrows(ResponseStatusException.class,request::block);assertEquals(403,error.getStatusCode().value());}
    @Test void exactNativeIdentityAndCurrentPolicyRetainPurposeAndBound(){var permit=service().authorize(account,Purpose.ACQUIRE).block();assertEquals(tenant,permit.tenantId());assertEquals(actor,permit.actorId());assertEquals(party,permit.partyId());assertEquals(now.plusSeconds(5),permit.effectiveUntil());assertEquals("accounts.card-enrollment.acquire",lastAction.get());assertEquals(party.toString(),lastContext.get().get("partyId"));assertNull(permit.auditActor().staffId());assertNull(permit.auditActor().staffRealm());}
    @Test void callerExpiryIsNeverExtended(){principal.set(principal(Map.of("exp",now.plusSeconds(2)),principal.get().scopes()));assertEquals(now.plusSeconds(2),service().authorize(account,Purpose.ACQUIRE).block().effectiveUntil());}
    @Test void claimRequiresTheDesignatedCardsClient(){refused(service().authorize(account,Purpose.CLAIM));assertEquals(0,evaluations.get());principal.set(principal(Map.of("client_id","core-cards"),principal.get().scopes()));assertEquals(Purpose.CLAIM,service().authorize(account,Purpose.CLAIM).block().purpose());}
    @Test void purposeSpecificScopeCannotBeSubstituted(){principal.set(principal(Map.of(),Set.of("accounts:card-enrollment:read")));refused(service().authorize(account,Purpose.ACQUIRE));refused(service().authorize(account,Purpose.CANCEL));assertEquals(0,evaluations.get());}
    @Test void wrongAudienceOrExpiredOrFutureTokenCannotReachPolicy(){for(var change:List.of(Map.<String,Object>of("aud",List.of("domain-cards")),Map.<String,Object>of("exp",now),Map.<String,Object>of("nbf",now.plusSeconds(1)))){principal.set(principal(change,Set.of("accounts:card-enrollment:acquire")));refused(service().authorize(account,Purpose.ACQUIRE));}assertEquals(0,evaluations.get());}
    @Test void absentOrUnusableActorAndPartyClaimsCannotBeInferred(){for(String claim:List.of("actor_id","party_id")){principal.set(principal(Map.of(claim,"00000000-0000-0000-0000-000000000000"),Set.of("accounts:card-enrollment:acquire")));refused(service().authorize(account,Purpose.ACQUIRE));}assertEquals(0,evaluations.get());}
    @Test void nonHumanOrUnknownClientCannotBorrowCustomerAuthority(){for(var change:List.of(Map.<String,Object>of("principal_kind","SERVICE"),Map.<String,Object>of("client_id","untrusted-client"))){principal.set(principal(change,Set.of("accounts:card-enrollment:acquire")));refused(service().authorize(account,Purpose.ACQUIRE));}assertEquals(0,evaluations.get());}
    @Test void permitWithoutSameResponsePolicyProvenanceCannotAdmit(){evaluation.set(new PolicyEvaluation(Decision.permit(),Optional.empty()));refused(service().authorize(account,Purpose.ACQUIRE));}
    @Test void changedPrincipalDuringPolicyEvaluationCannotProducePermit(){var first=principal.get();var calls=new AtomicInteger();SecurityContextPort current=()->Mono.just(calls.getAndIncrement()==0?first:principal(Map.of("actor_id",UUID.randomUUID().toString()),first.scopes()));var service=new CurrentAccountCardFenceAuthorityImpl(current,policies,profile,Clock.fixed(now,ZoneOffset.UTC));refused(service.authorize(account,Purpose.ACQUIRE));}
    @Test void revalidationCannotRenewOrSubstitutePolicyRevision(){var service=service();var permit=service.authorize(account,Purpose.ACQUIRE).block();evaluation.set(new PolicyEvaluation(Decision.permit(),Optional.of(new PolicyEvaluationProvenance("policy-r2","decision-2"))));refused(service.requireCurrent(permit));assertEquals(now.plusSeconds(5),permit.effectiveUntil());}
    @Test void lostPolicyResponseIsUnavailableAndNeverReturnsPermit(){var failed=new CurrentAccountCardFenceAuthorityImpl(()->Mono.just(principal.get()),(p,a,r,c)->Mono.error(new java.util.concurrent.TimeoutException()),profile,Clock.fixed(now,ZoneOffset.UTC));var error=assertThrows(ResponseStatusException.class,()->failed.authorize(account,Purpose.ACQUIRE).block());assertEquals(503,error.getStatusCode().value());}
    @Test void missingNativePrincipalCannotReachPolicy(){principal.set(null);refused(service().authorize(account,Purpose.READ));assertEquals(0,evaluations.get());}
    @Test void indeterminatePolicyIsUnavailableInsteadOfBusinessRefusal(){evaluation.set(new PolicyEvaluation(Decision.indeterminate("Unavailable"),Optional.empty()));var error=assertThrows(ResponseStatusException.class,()->service().authorize(account,Purpose.ACQUIRE).block());assertEquals(503,error.getStatusCode().value());}
}
