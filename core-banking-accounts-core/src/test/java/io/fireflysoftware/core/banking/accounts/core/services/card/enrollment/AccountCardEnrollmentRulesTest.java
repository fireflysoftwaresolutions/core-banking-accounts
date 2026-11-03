package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment;

import static org.assertj.core.api.Assertions.*;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment.AccountCardEnrollmentContracts.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountCardEnrollmentRulesTest {
    private final UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),party=UUID.randomUUID(),account=UUID.randomUUID(),contract=UUID.randomUUID(),mandate=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-11-02T12:00:00Z");
    private final Acquire request=new Acquire(UUID.randomUUID(),7,now.plusSeconds(20).toEpochMilli(),"CUSTOMER_REQUEST");
    private AccountCardEnrollmentRules.Owner current(long version,String control,String status,boolean active,String role){
        return new AccountCardEnrollmentRules.Owner(tenant.toString(),account,contract,"USD","CUSTOMER","MANAGED",control,version,null,status,null,mandate,party,active,role);
    }
    private Fence held(){return new Fence(request.actionId(),UUID.randomUUID(),tenant,actor,party,account,contract,mandate,7,"USD",request.reasonCode(),request.acceptNotAfterEpochMillis(),now,State.HELD,0,null,null);}
    @Test void admitsOnlyTheActualActiveOwnerOfAnOpenManagedCustomerAccount(){
        assertThatCode(()->AccountCardEnrollmentRules.admit(current(7,"ACTIVE","OPEN",true,"OWNER"),tenant,party,account,request,now)).doesNotThrowAnyException();
        for(var invalid:java.util.List.of(current(7,"BLOCKED","OPEN",true,"OWNER"),current(7,"ACTIVE","CLOSED",true,"OWNER"),current(7,"ACTIVE","OPEN",false,"OWNER"),current(7,"ACTIVE","OPEN",true,"OPERATOR"),current(7,"ACTIVE","OPEN",true,"VIEWER")))
            assertThatThrownBy(()->AccountCardEnrollmentRules.admit(invalid,tenant,party,account,request,now)).isInstanceOf(AccountCardEnrollmentRules.Refused.class);
    }
    @Test void refusesWrongTenantPartyAndAccountWithoutInferringOwnership(){
        var owner=current(7,"ACTIVE","OPEN",true,"OWNER");
        assertThatThrownBy(()->AccountCardEnrollmentRules.admit(owner,UUID.randomUUID(),party,account,request,now)).isInstanceOf(AccountCardEnrollmentRules.Refused.class);
        assertThatThrownBy(()->AccountCardEnrollmentRules.admit(owner,tenant,UUID.randomUUID(),account,request,now)).isInstanceOf(AccountCardEnrollmentRules.Refused.class);
        assertThatThrownBy(()->AccountCardEnrollmentRules.admit(owner,tenant,party,UUID.randomUUID(),request,now)).isInstanceOf(AccountCardEnrollmentRules.Refused.class);
    }
    @Test void staleAccountVersionAndExpiredDeadlineCannotAcquire(){
        assertThatThrownBy(()->AccountCardEnrollmentRules.admit(current(8,"ACTIVE","OPEN",true,"OWNER"),tenant,party,account,request,now)).isInstanceOf(AccountCardEnrollmentRules.Conflict.class);
        assertThatThrownBy(()->AccountCardEnrollmentRules.admit(current(7,"ACTIVE","OPEN",true,"OWNER"),tenant,party,account,request,Instant.ofEpochMilli(request.acceptNotAfterEpochMillis()))).isInstanceOf(AccountCardEnrollmentRules.Conflict.class);
    }
    @Test void claimRechecksTheActualVersionStateAndOriginalDeadline(){
        var fence=held();var claim=new Claim(UUID.randomUUID(),fence.receiptId(),0);
        assertThat(AccountCardEnrollmentRules.claim(fence,claim,current(7,"ACTIVE","OPEN",true,"OWNER"),now).state()).isEqualTo(State.CLAIMED);
        for(var owner:java.util.List.of(current(8,"ACTIVE","OPEN",true,"OWNER"),current(7,"BLOCKING","OPEN",true,"OWNER"),current(7,"ACTIVE","OPEN",false,"OWNER")))
            assertThatThrownBy(()->AccountCardEnrollmentRules.claim(fence,claim,owner,now)).isInstanceOf(AccountCardEnrollmentRules.Refused.class);
        assertThatThrownBy(()->AccountCardEnrollmentRules.claim(fence,claim,current(7,"ACTIVE","OPEN",true,"OWNER"),Instant.ofEpochMilli(request.acceptNotAfterEpochMillis()))).isInstanceOf(AccountCardEnrollmentRules.Refused.class);
    }
    @Test void expiredHeldFenceCanBeCancelledWithoutRenewingItsDeadline(){
        var fence=held();var cancel=new Cancel(UUID.randomUUID(),fence.receiptId(),0,"CUSTOMER_CANCELLED");
        var result=AccountCardEnrollmentRules.cancel(fence,cancel);
        assertThat(result.state()).isEqualTo(State.CANCELLED);assertThat(result.version()).isEqualTo(1);
        assertThat(result.acceptNotAfterEpochMillis()).isEqualTo(fence.acceptNotAfterEpochMillis());
        assertThat(result.actionId()).isEqualTo(fence.actionId());assertThat(result.receiptId()).isEqualTo(fence.receiptId());
    }
    @Test void cancellationWinningPreventsClaimAndClaimWinningPreventsCancellation(){
        var fence=held();var claim=new Claim(UUID.randomUUID(),fence.receiptId(),0);var cancel=new Cancel(UUID.randomUUID(),fence.receiptId(),0,"CUSTOMER_CANCELLED");
        var cancelled=AccountCardEnrollmentRules.cancel(fence,cancel);
        assertThatThrownBy(()->AccountCardEnrollmentRules.claim(cancelled,claim,current(7,"ACTIVE","OPEN",true,"OWNER"),now)).isInstanceOf(AccountCardEnrollmentRules.Conflict.class);
        var claimed=AccountCardEnrollmentRules.claim(fence,claim,current(7,"ACTIVE","OPEN",true,"OWNER"),now);
        assertThatThrownBy(()->AccountCardEnrollmentRules.cancel(claimed,cancel)).isInstanceOf(AccountCardEnrollmentRules.Conflict.class);
    }
    @Test void transitionRequiresOriginalReceiptAndVersion(){
        var fence=held();
        assertThatThrownBy(()->AccountCardEnrollmentRules.cancel(fence,new Cancel(UUID.randomUUID(),UUID.randomUUID(),0,"CUSTOMER_CANCELLED"))).isInstanceOf(AccountCardEnrollmentRules.Conflict.class);
        assertThatThrownBy(()->AccountCardEnrollmentRules.claim(fence,new Claim(UUID.randomUUID(),fence.receiptId(),1),current(7,"ACTIVE","OPEN",true,"OWNER"),now)).isInstanceOf(AccountCardEnrollmentRules.Conflict.class);
    }
    @Test void getReportsExpiredOrRevokedEligibilityWithoutReleasingPendingFence(){
        var fence=held();assertThat(AccountCardEnrollmentRules.eligible(fence,current(7,"ACTIVE","OPEN",true,"OWNER"),now)).isTrue();
        assertThat(AccountCardEnrollmentRules.eligible(fence,current(8,"ACTIVE","OPEN",true,"OWNER"),now)).isFalse();
        assertThat(AccountCardEnrollmentRules.eligible(fence,current(7,"ACTIVE","OPEN",true,"OWNER"),now.plusSeconds(60))).isFalse();
        assertThat(AccountCardEnrollmentRules.pending(fence)).isTrue();
    }
    @Test void exactAcquireReplayRetainsTheOriginalBoundButChangedIntentConflicts(){
        var fence=held();assertThatCode(()->AccountCardEnrollmentRules.requireAcquireReplay(fence,request,tenant,actor,party,account)).doesNotThrowAnyException();
        assertThatThrownBy(()->AccountCardEnrollmentRules.requireAcquireReplay(fence,new Acquire(request.actionId(),7,request.acceptNotAfterEpochMillis()+1,request.reasonCode()),tenant,actor,party,account)).isInstanceOf(AccountCardEnrollmentRules.Conflict.class);
        assertThatThrownBy(()->AccountCardEnrollmentRules.requireAcquireReplay(fence,request,tenant,UUID.randomUUID(),party,account)).isInstanceOf(AccountCardEnrollmentRules.Conflict.class);
    }
    @Test void pendingFenceBlocksCloseAndReenableButNeverSecurityBlocking(){
        for(State state:java.util.List.of(State.HELD,State.CLAIMED)){
            assertThat(AccountCardEnrollmentRules.controlAllowed(state,"CLOSED")).isFalse();
            assertThat(AccountCardEnrollmentRules.controlAllowed(state,"ACTIVE")).isFalse();
            assertThat(AccountCardEnrollmentRules.controlAllowed(state,"BLOCKED")).isTrue();
        }
        assertThat(AccountCardEnrollmentRules.controlAllowed(State.CANCELLED,"CLOSED")).isTrue();
    }
    @Test void requestRejectsMissingStableActionAndUnboundedOrMalformedIntent(){
        assertThatThrownBy(()->AccountCardEnrollmentRules.validate(new Acquire(null,7,request.acceptNotAfterEpochMillis(),"CUSTOMER_REQUEST"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->AccountCardEnrollmentRules.validate(new Acquire(UUID.randomUUID(),-1,request.acceptNotAfterEpochMillis(),"CUSTOMER_REQUEST"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->AccountCardEnrollmentRules.validate(new Acquire(UUID.randomUUID(),7,request.acceptNotAfterEpochMillis(),"\n"))).isInstanceOf(IllegalArgumentException.class);
    }
}
