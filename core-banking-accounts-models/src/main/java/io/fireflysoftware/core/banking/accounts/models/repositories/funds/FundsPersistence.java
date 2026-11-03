package io.fireflysoftware.core.banking.accounts.models.repositories.funds;

import io.fireflysoftware.core.banking.accounts.models.entities.funds.*;

import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface FundsPersistence {
    Mono<Long> linkEnrollment(AccountMoneyEnrollment expected);

    Mono<Long> stageControl(AccountMoneyEnrollment expected, UUID actionId, String stage);

    Mono<Long> finishControl(AccountMoneyEnrollment expected, UUID actionId, String finalState);

    Mono<Long> finishAction(AccountMoneyAction expected, String finalState);

    Mono<Long> updateAccountStatus(
            FundsAccountSnapshot expected, String status, LocalDate closeDate);

    Mono<FundsStatusHistoryInsert> insertStatusHistory(FundsStatusHistoryInsert history);
}
