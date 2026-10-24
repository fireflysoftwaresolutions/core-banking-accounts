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


package io.fireflysoftware.core.banking.accounts.models.repositories.space;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.space.AccountSpace;
import io.fireflysoftware.core.banking.accounts.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AccountSpaceRepository extends BaseRepository<AccountSpace, UUID>, AccountSpaceAggregates {
    /**
     * Find all spaces for a specific account
     * @param accountId the account ID
     * @return a Flux of AccountSpace entities
     */
    Flux<AccountSpace> findByAccountId(UUID accountId);

    /**
     * Find all spaces for a specific account with pagination
     * @param accountId the account ID
     * @param pageable pagination information
     * @return a Flux of AccountSpace entities
     */
    Flux<AccountSpace> findByAccountId(UUID accountId, Pageable pageable);

    /**
     * Count the number of spaces for a specific account
     * @param accountId the account ID
     * @return a Mono with the count
     */
    Mono<Long> countByAccountId(UUID accountId);

    /**
     * Find all spaces of a specific type for an account
     * @param accountId the account ID
     * @param spaceType the space type
     * @return a Flux of AccountSpace entities
     */
    Flux<AccountSpace> findByAccountIdAndSpaceType(UUID accountId, AccountSpaceTypeEnum spaceType);

    /**
     * Find all spaces with goals (target amount is not null)
     * @param accountId the account ID
     * @return a Flux of AccountSpace entities
     */
    Flux<AccountSpace> findByAccountIdAndTargetAmountIsNotNull(UUID accountId);

    /**
     * Find all spaces with upcoming target dates
     * @param accountId the account ID
     * @param currentDate the current date
     * @return a Flux of AccountSpace entities with target dates after the current date
     */
    Flux<AccountSpace> findByAccountIdAndTargetDateGreaterThanEqual(UUID accountId, LocalDateTime currentDate);

    /**
     * Find all spaces with target dates that have passed
     * @param accountId the account ID
     * @param currentDate the current date
     * @return a Flux of AccountSpace entities with target dates before the current date
     */
    Flux<AccountSpace> findByAccountIdAndTargetDateLessThan(UUID accountId, LocalDateTime currentDate);

    /**
     * Calculate the total balance across all spaces for an account
     * @param accountId the account ID
     * @return a Mono with the total balance
     */
    Mono<java.math.BigDecimal> calculateTotalBalance(UUID accountId);

    /**
     * Find all frozen spaces for an account
     * @param accountId the account ID
     * @return a Flux of AccountSpace entities that are frozen
     */
    Flux<AccountSpace> findByAccountIdAndIsFrozenTrue(UUID accountId);

    /**
     * Find all unfrozen spaces for an account
     * @param accountId the account ID
     * @return a Flux of AccountSpace entities that are not frozen
     */
    Flux<AccountSpace> findByAccountIdAndIsFrozenFalse(UUID accountId);

    /**
     * Count the number of frozen spaces for an account
     * @param accountId the account ID
     * @return a Mono with the count of frozen spaces
     */
    Mono<Long> countByAccountIdAndIsFrozenTrue(UUID accountId);

    /**
     * Find spaces that were frozen after a specific date
     * @param accountId the account ID
     * @param dateTime the date/time threshold
     * @return a Flux of AccountSpace entities frozen after the specified date/time
     */
    Flux<AccountSpace> findByAccountIdAndFrozenDateTimeGreaterThanEqual(UUID accountId, LocalDateTime dateTime);
}
