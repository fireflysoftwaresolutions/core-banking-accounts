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


package io.fireflysoftware.core.banking.accounts.core.services.space;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.AccountSpaceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.SpaceAnalyticsDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.TransferFrequencyEnum;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public interface AccountSpaceService {
    /**
     * Retrieves a paginated list of account spaces based on the provided filter criteria.
     *
     * @param filterRequest the filter and pagination criteria for retrieving account spaces
     * @return a reactive stream emitting a PaginationResponse containing a list of AccountSpaceDTO objects
     */
    Mono<PaginationResponse<AccountSpaceDTO>> filterAccountSpaces(FilterRequest<AccountSpaceDTO> filterRequest);

    /**
     * Creates a new account space with the provided details.
     *
     * @param accountSpaceDTO The account space data transfer object containing the details to be created.
     * @return A Mono emitting the created AccountSpaceDTO object, or an error if the creation fails.
     */
    Mono<AccountSpaceDTO> createAccountSpace(AccountSpaceDTO accountSpaceDTO);

    /**
     * Retrieves the account space details for a given account space ID.
     *
     * @param accountSpaceId the unique identifier of the account space to retrieve
     * @return a Mono emitting the AccountSpaceDTO containing details, or an empty Mono if not found
     */
    Mono<AccountSpaceDTO> getAccountSpace(UUID accountSpaceId);

    /**
     * Updates an existing account space by its unique ID with the provided details.
     *
     * @param accountSpaceId the unique identifier of the account space to be updated
     * @param accountSpaceDTO the updated account space details
     * @return a Mono emitting the updated account space details, or an error if it does not exist
     */
    Mono<AccountSpaceDTO> updateAccountSpace(UUID accountSpaceId, AccountSpaceDTO accountSpaceDTO);

    /**
     * Deletes an account space based on the provided account space ID.
     *
     * @param accountSpaceId The unique identifier of the account space to be deleted.
     * @return A Mono that completes when the account space deletion process is finished.
     */
    Mono<Void> deleteAccountSpace(UUID accountSpaceId);

    /**
     * Retrieves all account spaces for a specific account.
     *
     * @param accountId the unique identifier of the account
     * @return a Flux emitting AccountSpaceDTO objects for the specified account
     */
    Flux<AccountSpaceDTO> getAccountSpacesByAccountId(UUID accountId);

    /**
     * Retrieves a paginated list of account spaces for a specific account.
     *
     * @param accountId the unique identifier of the account
     * @param page the page number (0-based)
     * @param size the page size
     * @return a Mono emitting a PaginationResponse containing a list of AccountSpaceDTO objects
     */
    Mono<PaginationResponse<AccountSpaceDTO>> getAccountSpacesByAccountId(UUID accountId, int page, int size);

    /**
     * Transfers funds between account spaces within the same account.
     *
     * @param fromAccountSpaceId the source account space ID
     * @param toAccountSpaceId the destination account space ID
     * @param amount the amount to transfer
     * @return a Mono emitting a Boolean indicating success or failure
     */
    Mono<Boolean> transferBetweenSpaces(UUID fromAccountSpaceId, UUID toAccountSpaceId, BigDecimal amount);

    // ===== Goal Tracking Methods =====

    /**
     * Calculates and returns the goal progress for a space.
     * This includes progress percentage, remaining amount, and estimated completion date.
     *
     * @param accountSpaceId the space ID
     * @return a Mono emitting the space with goal progress information
     */
    Mono<AccountSpaceDTO> calculateGoalProgress(UUID accountSpaceId);

    /**
     * Retrieves all spaces with goals for an account.
     *
     * @param accountId the account ID
     * @return a Flux of spaces with goals
     */
    Flux<AccountSpaceDTO> getSpacesWithGoals(UUID accountId);

    /**
     * Retrieves all spaces with upcoming goal target dates.
     *
     * @param accountId the account ID
     * @param daysThreshold number of days in the future to consider "upcoming"
     * @return a Flux of spaces with upcoming target dates
     */
    Flux<AccountSpaceDTO> getSpacesWithUpcomingTargetDates(UUID accountId, int daysThreshold);

    // ===== Automatic Transfer Methods =====

    /**
     * Configures automatic transfers for a space.
     *
     * @param accountSpaceId the space ID
     * @param enabled whether automatic transfers are enabled
     * @param frequency the transfer frequency
     * @param amount the transfer amount
     * @param sourceSpaceId the source space ID (null for main account)
     * @return a Mono emitting the updated space
     */
    Mono<AccountSpaceDTO> configureAutomaticTransfers(
            UUID accountSpaceId,
            Boolean enabled,
            TransferFrequencyEnum frequency,
            BigDecimal amount,
            UUID sourceSpaceId
    );

    /**
     * Executes all pending automatic transfers for an account.
     * This would typically be called by a scheduler.
     *
     * @param accountId the account ID
     * @return a Mono emitting the number of transfers executed
     */
    Mono<Integer> executeAutomaticTransfers(UUID accountId);

    /**
     * Simulates future balances based on automatic transfers.
     *
     * @param accountId the account ID
     * @param months number of months to simulate
     * @return a Mono emitting a map of space IDs to projected balances
     */
    Mono<Map<UUID, BigDecimal>> simulateFutureBalances(UUID accountId, int months);

    // ===== Status Management Methods =====

    /**
     * Freezes an account space, preventing any withdrawals or transfers from it.
     * This is useful for temporary restrictions without deleting the space.
     *
     * @param accountSpaceId the unique identifier of the account space to freeze
     * @return a Mono emitting the updated account space with frozen status
     */
    Mono<AccountSpaceDTO> freezeAccountSpace(UUID accountSpaceId);

    /**
     * Unfreezes a previously frozen account space, allowing normal operations again.
     *
     * @param accountSpaceId the unique identifier of the account space to unfreeze
     * @return a Mono emitting the updated account space with unfrozen status
     */
    Mono<AccountSpaceDTO> unfreezeAccountSpace(UUID accountSpaceId);

    /**
     * Updates the balance of an account space directly.
     * This should be used for administrative adjustments only, not regular transactions.
     *
     * @param accountSpaceId the unique identifier of the account space
     * @param newBalance the new balance to set
     * @param reason the reason for the balance adjustment
     * @return a Mono emitting the updated account space with the new balance
     */
    Mono<AccountSpaceDTO> updateAccountSpaceBalance(UUID accountSpaceId, BigDecimal newBalance, String reason);

    // ===== Analytics Methods =====

    /**
     * Calculates the distribution of funds across spaces for an account.
     *
     * @param accountId the account ID
     * @return a Mono emitting a map of space IDs to percentage of total balance
     */
    Mono<Map<UUID, BigDecimal>> calculateBalanceDistribution(UUID accountId);

    /**
     * Calculates the growth rate for each space over a period.
     *
     * @param accountId the account ID
     * @param startDate the start date for the calculation
     * @param endDate the end date for the calculation
     * @return a Mono emitting a map of space IDs to growth rates
     */
    Mono<Map<UUID, BigDecimal>> calculateGrowthRates(
            UUID accountId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    /**
     * Generates analytics for a specific account space over a period.
     * This includes growth rate, transaction frequency, and other metrics.
     *
     * @param accountSpaceId the unique identifier of the account space
     * @param startDate the start date for the analysis period
     * @param endDate the end date for the analysis period
     * @return a Mono emitting a SpaceAnalyticsDTO with detailed analytics
     */
    Mono<SpaceAnalyticsDTO> getSpaceAnalytics(UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Retrieves spaces by type for an account.
     *
     * @param accountId the account ID
     * @param spaceType the space type
     * @return a Flux of spaces of the specified type
     */
    Flux<AccountSpaceDTO> getSpacesByType(UUID accountId, AccountSpaceTypeEnum spaceType);
}
