/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.banking.accounts.core.services.core;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountBalanceDTO;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AccountBalanceService {

    /**
     * Retrieve a paginated list of account balances for a specified account.
     * This returns all balances for the account, including both global and space-specific balances.
     */
    Mono<PaginationResponse<AccountBalanceDTO>> getAllBalances(UUID accountId, PaginationRequest paginationRequest);

    /**
     * Retrieve a paginated list of global account balances (not associated with any space).
     */
    Mono<PaginationResponse<AccountBalanceDTO>> getGlobalBalances(UUID accountId, PaginationRequest paginationRequest);

    /**
     * Retrieve a paginated list of balances for a specific account space.
     */
    Mono<PaginationResponse<AccountBalanceDTO>> getSpaceBalances(UUID accountId, UUID accountSpaceId, PaginationRequest paginationRequest);

    /**
     * Create a new balance record for a specific account.
     * If accountSpaceId is set in the DTO, this will create a space-specific balance.
     * If accountSpaceId is null, this will create a global account balance.
     */
    Mono<AccountBalanceDTO> createBalance(UUID accountId, AccountBalanceDTO balanceDTO);

    /**
     * Retrieve a specific account balance by its unique ID, ensuring it belongs to the specified account.
     */
    Mono<AccountBalanceDTO> getBalance(UUID accountId, UUID balanceId);

    /**
     * Update an existing account balance for a specified account.
     */
    Mono<AccountBalanceDTO> updateBalance(UUID accountId, UUID balanceId, AccountBalanceDTO balanceDTO);

    /**
     * Delete a specific account balance by its unique ID, ensuring it belongs to the specified account.
     */
    Mono<Void> deleteBalance(UUID accountId, UUID balanceId);
}
