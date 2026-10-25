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

import io.fireflysoftware.core.banking.accounts.models.entities.space.SpaceTransaction;
import io.fireflysoftware.core.banking.accounts.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for managing space transactions.
 */
public interface SpaceTransactionRepository extends BaseRepository<SpaceTransaction, UUID>, SpaceTransactionAggregates {
    
    /**
     * Find all transactions for a specific account space
     * @param accountSpaceId the account space ID
     * @param pageable pagination information
     * @return a Flux of SpaceTransaction entities
     */
    Flux<SpaceTransaction> findByAccountSpaceId(UUID accountSpaceId, Pageable pageable);
    
    /**
     * Count the number of transactions for a specific account space
     * @param accountSpaceId the account space ID
     * @return a Mono with the count
     */
    Mono<Long> countByAccountSpaceId(UUID accountSpaceId);
    
    /**
     * Find transactions for a specific account space within a date range
     * @param accountSpaceId the account space ID
     * @param startDate the start date
     * @param endDate the end date
     * @param pageable pagination information
     * @return a Flux of SpaceTransaction entities
     */
    Flux<SpaceTransaction> findByAccountSpaceIdAndTransactionDateTimeBetween(
            UUID accountSpaceId, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);
    
    /**
     * Count the number of transactions for a specific account space within a date range
     * @param accountSpaceId the account space ID
     * @param startDate the start date
     * @param endDate the end date
     * @return a Mono with the count
     */
    Mono<Long> countByAccountSpaceIdAndTransactionDateTimeBetween(
            UUID accountSpaceId, 
            LocalDateTime startDate, 
            LocalDateTime endDate);
    
    /**
     * Calculate the sum of all deposits (positive amounts) for a specific account space within a date range
     * @param accountSpaceId the account space ID
     * @param startDate the start date
     * @param endDate the end date
     * @return a Mono with the sum
     */
    Mono<BigDecimal> calculateTotalDeposits(UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Calculate the sum of all withdrawals (negative amounts) for a specific account space within a date range
     * @param accountSpaceId the account space ID
     * @param startDate the start date
     * @param endDate the end date
     * @return a Mono with the sum (as a positive number)
     */
    Mono<BigDecimal> calculateTotalWithdrawals(UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find the most recent transaction before a specific date and time
     * @param accountSpaceId the account space ID
     * @param dateTime the date and time
     * @return a Mono with the transaction
     */
    Mono<SpaceTransaction> findFirstByAccountSpaceIdAndTransactionDateTimeLessThanEqualOrderByTransactionDateTimeDesc(
            UUID accountSpaceId, 
            LocalDateTime dateTime);
}
