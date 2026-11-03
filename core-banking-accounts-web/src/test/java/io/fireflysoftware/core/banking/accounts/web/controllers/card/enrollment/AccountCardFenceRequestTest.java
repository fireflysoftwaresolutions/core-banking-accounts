package io.fireflysoftware.core.banking.accounts.web.controllers.card.enrollment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.*;
import java.util.*;
import org.fireflyframework.core.transaction.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Mono;

/** Real request binding/error projection only; the owning service is deliberately not executed. */
class AccountCardFenceRequestTest {
    @Configuration(proxyBeanMethods=false) @EnableWebFlux @Profile("account-card-fence-wire-fixture") static class Web {}
    final UUID account=UUID.randomUUID(),action=UUID.randomUUID(),receipt=UUID.randomUUID();
    AnnotationConfigApplicationContext context;AccountCardFenceService service;WebTestClient client;
    @BeforeEach void setup(){service=mock(AccountCardFenceService.class);context=new AnnotationConfigApplicationContext();context.getEnvironment().setActiveProfiles("account-card-fence-wire-fixture");context.registerBean(AccountCardFenceService.class,()->service);context.register(AccountCardFenceController.class,Web.class);context.refresh();client=WebTestClient.bindToApplicationContext(context).build();}
    @AfterEach void close(){context.close();}
    String base(){return "/api/v1/accounts/"+account+"/card-enrollments";}
    void incomplete(String path,Map<String,Object> valid){for(String key:valid.keySet())for(boolean explicitNull:List.of(false,true)){
        var body=new LinkedHashMap<>(valid);if(explicitNull)body.put(key,null);else body.remove(key);
        client.post().uri(path).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isBadRequest();
    }verifyNoInteractions(service);}
    @Test void acquisitionRequiresEveryExplicitFieldBeforeCallingOwner(){incomplete(base(),Map.of("actionId",action,"expectedAccountVersion",0,"acceptNotAfterEpochMillis",System.currentTimeMillis()+10000,"reasonCode","CUSTOMER_REQUEST"));}
    @Test void claimRequiresOriginalReceiptActionAndVersionBeforeCallingOwner(){incomplete(base()+"/"+action+"/claim",Map.of("claimActionId",UUID.randomUUID(),"receiptId",receipt,"expectedFenceVersion",0));}
    @Test void cancellationRequiresOriginalReceiptActionVersionAndReasonBeforeCallingOwner(){incomplete(base()+"/"+action+"/cancel",Map.of("cancellationActionId",UUID.randomUUID(),"receiptId",receipt,"expectedFenceVersion",0,"reasonCode","CUSTOMER_CANCELLED"));}
    @Test void unknownOutcomePreservesOriginalActionAndNeverSerializesInternalCause(){
        when(service.acquire(eq(account),any())).thenAnswer(call->Mono.error(new AccountCardFenceException(action,AccountCardFenceException.Reason.UNKNOWN,new TransactionFacts(true,false,false,false),CleanupFact.COMPLETE,new IllegalStateException("private database diagnostic"))));
        client.post().uri(base()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("actionId",action,"expectedAccountVersion",0,"acceptNotAfterEpochMillis",System.currentTimeMillis()+10000,"reasonCode","CUSTOMER_REQUEST"))
                .exchange().expectStatus().isEqualTo(503).expectHeader().valueEquals("Cache-Control","no-store").expectBody().jsonPath("$.operationActionId").isEqualTo(action.toString()).jsonPath("$.reason").isEqualTo("UNKNOWN").jsonPath("$.cause").doesNotExist();
        verify(service).acquire(eq(account),argThat(input->input.actionId().equals(action)&&input.expectedAccountVersion()==0));verifyNoMoreInteractions(service);
    }
    @Test void recoveryOnlyForwardsTheOriginalFenceAndOperationIdentity(){UUID originalOperation=UUID.randomUUID();when(service.recover(account,action,originalOperation)).thenReturn(Mono.error(new AccountCardFenceException(originalOperation,AccountCardFenceException.Reason.WITHHELD,new TransactionFacts(true,true,true,false),CleanupFact.COMPLETE)));
        client.get().uri(base()+"/"+action+"/operations/"+originalOperation).exchange().expectStatus().isEqualTo(503).expectHeader().valueEquals("Cache-Control","no-store");
        verify(service).recover(account,action,originalOperation);verifyNoMoreInteractions(service);
    }
}
