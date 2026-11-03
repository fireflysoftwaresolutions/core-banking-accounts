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

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountDTO;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AccountService {
    /**
     * Retrieves a paginated list of accounts based on the provided filter criteria.
     *
     * @param filterRequest the filter and pagination criteria for retrieving accounts
     * @return a reactive stream emitting a PaginationResponse containing a list of AccountDTO objects
     */
    Mono<PaginationResponse<AccountDTO>> filterAccounts(FilterRequest<AccountDTO> filterRequest);
    /**
     * Creates a new account with the provided details.
     *
     * @param accountDTO The account data transfer object containing the account details to be created.
     * @return A Mono emitting the created AccountDTO object, or an error if the creation fails.
     */
    Mono<AccountDTO> createAccount(AccountDTO accountDTO);
    /**
     * Retrieves the account details for a given account ID.
     *
     * @param accountId the unique identifier of the account to retrieve
     * @return a Mono emitting the AccountDTO containing account details, or an empty Mono if the account is not found
     */
    Mono<AccountDTO> getAccount(UUID accountId);
    /**
     * Updates an existing account by its unique ID with the provided details.
     *
     * @param accountId the unique identifier of the account to be updated
     * @param accountDTO the updated account details
     * @return a Mono emitting the updated account details, or an error if the account does not exist
     */
    Mono<AccountDTO> updateAccount(UUID accountId, AccountDTO accountDTO);
    /**
     * Deletes an account based on the provided account ID.
     *
     * @param accountId The unique identifier of the account to be deleted.
     * @return A Mono that completes when the account deletion process is finished.
     */
    Mono<Void> deleteAccount(UUID accountId);
}
