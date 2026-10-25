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


package io.fireflysoftware.core.banking.accounts.core.services.restriction;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.restriction.AccountRestrictionDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AccountRestrictionService {
    
    /**
     * Create a new account restriction
     * @param accountRestrictionDTO The account restriction DTO
     * @return Mono of AccountRestrictionDTO
     */
    Mono<AccountRestrictionDTO> createAccountRestriction(AccountRestrictionDTO accountRestrictionDTO);
    
    /**
     * Get an account restriction by ID
     * @param accountRestrictionId The account restriction ID
     * @return Mono of AccountRestrictionDTO
     */
    Mono<AccountRestrictionDTO> getAccountRestriction(UUID accountRestrictionId);
    
    /**
     * Update an account restriction
     * @param accountRestrictionId The account restriction ID
     * @param accountRestrictionDTO The account restriction DTO
     * @return Mono of AccountRestrictionDTO
     */
    Mono<AccountRestrictionDTO> updateAccountRestriction(UUID accountRestrictionId, AccountRestrictionDTO accountRestrictionDTO);
    
    /**
     * Delete an account restriction
     * @param accountRestrictionId The account restriction ID
     * @return Mono of Void
     */
    Mono<Void> deleteAccountRestriction(UUID accountRestrictionId);
    
    /**
     * Get all account restrictions for an account
     * @param accountId The account ID
     * @return Flux of AccountRestrictionDTO
     */
    Flux<AccountRestrictionDTO> getAccountRestrictionsByAccountId(UUID accountId);
    
    /**
     * Get all active account restrictions for an account
     * @param accountId The account ID
     * @return Flux of AccountRestrictionDTO
     */
    Flux<AccountRestrictionDTO> getActiveAccountRestrictionsByAccountId(UUID accountId);
    
    /**
     * Remove a restriction (mark as inactive)
     * @param accountRestrictionId The account restriction ID
     * @param removedBy The user or system that removed the restriction
     * @return Mono of AccountRestrictionDTO
     */
    Mono<AccountRestrictionDTO> removeRestriction(UUID accountRestrictionId, String removedBy);
    
    /**
     * List account restrictions with pagination and filtering
     * @param filterRequest The filter request
     * @return Mono of PaginationResponse containing AccountRestrictionDTO
     */
    Mono<PaginationResponse<AccountRestrictionDTO>> listAccountRestrictions(FilterRequest filterRequest);
}
