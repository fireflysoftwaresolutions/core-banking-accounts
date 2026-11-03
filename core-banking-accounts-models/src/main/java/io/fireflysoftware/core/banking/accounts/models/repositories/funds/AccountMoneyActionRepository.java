package io.fireflysoftware.core.banking.accounts.models.repositories.funds;

import io.fireflysoftware.core.banking.accounts.models.entities.funds.AccountMoneyAction;
import io.fireflysoftware.core.banking.accounts.models.entities.funds.AccountMoneyActionId;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

public interface AccountMoneyActionRepository
        extends Repository<AccountMoneyAction, AccountMoneyActionId> {
    Mono<AccountMoneyAction> findByIdTenantIdAndIdActionId(
            String tenantId, java.util.UUID actionId);
}
