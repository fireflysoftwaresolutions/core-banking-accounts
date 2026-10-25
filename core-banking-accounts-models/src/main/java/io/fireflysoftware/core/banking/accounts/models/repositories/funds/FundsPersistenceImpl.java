package io.fireflysoftware.core.banking.accounts.models.repositories.funds;

import io.fireflysoftware.core.banking.accounts.models.entities.funds.*;

import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;

import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public class FundsPersistenceImpl implements FundsPersistence {
    private final R2dbcEntityOperations operations;

    public FundsPersistenceImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    private Criteria enrollment(AccountMoneyEnrollment expected) {
        var criteria =
                Criteria.where("tenantId")
                        .is(expected.tenantId())
                        .and("accountId")
                        .is(expected.accountId())
                        .and("enrollmentId")
                        .is(expected.enrollmentId())
                        .and("mode")
                        .is(expected.mode())
                        .and("control")
                        .is(expected.control())
                        .and("version")
                        .is(expected.version());
        return expected.pendingActionId() == null
                ? criteria.and("pendingActionId").isNull()
                : criteria.and("pendingActionId").is(expected.pendingActionId());
    }

    public Mono<Long> linkEnrollment(AccountMoneyEnrollment expected) {
        return operations.update(
                Query.query(enrollment(expected)),
                Update.update("mode", "MANAGED")
                        .set("control", "ACTIVE")
                        .set("version", Math.addExact(expected.version(), 1)),
                AccountMoneyEnrollment.class);
    }

    public Mono<Long> stageControl(AccountMoneyEnrollment expected, UUID actionId, String stage) {
        return operations.update(
                Query.query(enrollment(expected)),
                Update.update("control", stage)
                        .set("pendingActionId", actionId)
                        .set("version", Math.addExact(expected.version(), 1)),
                AccountMoneyEnrollment.class);
    }

    public Mono<Long> finishControl(
            AccountMoneyEnrollment expected, UUID actionId, String finalState) {
        if (!actionId.equals(expected.pendingActionId())) return Mono.just(0L);
        return operations.update(
                Query.query(enrollment(expected)),
                Update.update("control", finalState)
                        .set("pendingActionId", null)
                        .set("version", Math.addExact(expected.version(), 1)),
                AccountMoneyEnrollment.class);
    }

    public Mono<Long> finishAction(AccountMoneyAction expected, String finalState) {
        return operations.update(
                Query.query(
                        Criteria.where("tenant_id")
                                .is(expected.id().tenantId())
                                .and("action_id")
                                .is(expected.id().actionId())
                                .and("accountId")
                                .is(expected.accountId())
                                .and("target")
                                .is(expected.target())
                                .and("expectedVersion")
                                .is(expected.expectedVersion())
                                .and("state")
                                .is("PENDING")),
                Update.update("state", finalState),
                AccountMoneyAction.class);
    }

    public Mono<Long> updateAccountStatus(
            FundsAccountSnapshot expected, String status, LocalDate closeDate) {
        var update = Update.update("accountStatus", FundsAccountStatus.valueOf(status));
        if (closeDate != null) update = update.set("closeDate", closeDate);
        return operations.update(
                Query.query(
                        Criteria.where("accountId")
                                .is(expected.accountId())
                                .and("accountStatus")
                                .is(expected.accountStatus())),
                update,
                FundsAccountSnapshot.class);
    }

    public Mono<FundsStatusHistoryInsert> insertStatusHistory(FundsStatusHistoryInsert history) {
        if (history.accountStatusHistoryId() == null)
            return Mono.error(new IllegalArgumentException("Assigned history ID required"));
        return operations.insert(history);
    }
}
