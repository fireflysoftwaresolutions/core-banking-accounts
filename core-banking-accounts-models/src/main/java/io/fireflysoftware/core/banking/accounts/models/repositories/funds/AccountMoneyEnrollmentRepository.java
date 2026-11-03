package io.fireflysoftware.core.banking.accounts.models.repositories.funds;

import io.fireflysoftware.core.banking.accounts.models.entities.funds.AccountMoneyEnrollment;

import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AccountMoneyEnrollmentRepository
        extends Repository<AccountMoneyEnrollment, UUID>, FundsPersistence {
    Mono<AccountMoneyEnrollment> findByTenantIdAndAccountId(String tenantId, UUID accountId);

    @Lock(LockMode.PESSIMISTIC_WRITE)
    Mono<AccountMoneyEnrollment> findLockedByTenantIdAndAccountId(String tenantId, UUID accountId);
}
