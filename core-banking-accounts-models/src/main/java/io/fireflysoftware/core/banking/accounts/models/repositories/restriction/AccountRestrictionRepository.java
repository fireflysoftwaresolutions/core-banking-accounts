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


package io.fireflysoftware.core.banking.accounts.models.repositories.restriction;

import io.fireflysoftware.core.banking.accounts.models.entities.restriction.AccountRestriction;
import io.fireflysoftware.core.banking.accounts.models.repositories.BaseRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AccountRestrictionRepository extends BaseRepository<AccountRestriction, UUID> {
    
    /**
     * Find all restrictions for a specific account
     * @param accountId The account ID
     * @return Flux of AccountRestriction
     */
    Flux<AccountRestriction> findByAccountId(UUID accountId);
    
    /**
     * Find all active restrictions for a specific account
     * @param accountId The account ID
     * @param isActive Whether the restriction is active
     * @return Flux of AccountRestriction
     */
    Flux<AccountRestriction> findByAccountIdAndIsActive(UUID accountId, Boolean isActive);
    
    /**
     * Count active restrictions for a specific account
     * @param accountId The account ID
     * @param isActive Whether the restriction is active
     * @return Mono of Long representing the count
     */
    Mono<Long> countByAccountIdAndIsActive(UUID accountId, Boolean isActive);
    
    /**
     * Find restrictions by reference number
     * @param referenceNumber The reference number
     * @return Flux of AccountRestriction
     */
    Flux<AccountRestriction> findByReferenceNumber(String referenceNumber);
}
