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

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.SpaceTransactionDTO;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing transaction history for account spaces.
 * This service tracks and retrieves transactions specific to account spaces.
 */
public interface AccountSpaceTransactionService {
    
    /**
     * Record a transaction for an account space
     * @param accountSpaceId The account space ID
     * @param amount The transaction amount (positive for deposits, negative for withdrawals)
     * @param description The transaction description
     * @param referenceId Optional reference ID (e.g., transaction ID from another system)
     * @return Mono of SpaceTransactionDTO
     */
    Mono<SpaceTransactionDTO> recordTransaction(UUID accountSpaceId, BigDecimal amount, String description, String referenceId);
    
    /**
     * Get all transactions for an account space
     * @param accountSpaceId The account space ID
     * @param paginationRequest The pagination request
     * @return Mono of PaginationResponse containing SpaceTransactionDTO
     */
    Mono<PaginationResponse<SpaceTransactionDTO>> getTransactions(UUID accountSpaceId, PaginationRequest paginationRequest);
    
    /**
     * Get transactions for an account space within a date range
     * @param accountSpaceId The account space ID
     * @param startDate The start date
     * @param endDate The end date
     * @param paginationRequest The pagination request
     * @return Mono of PaginationResponse containing SpaceTransactionDTO
     */
    Mono<PaginationResponse<SpaceTransactionDTO>> getTransactionsByDateRange(
            UUID accountSpaceId, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            PaginationRequest paginationRequest);
    
    /**
     * Calculate total deposits for an account space within a date range
     * @param accountSpaceId The account space ID
     * @param startDate The start date
     * @param endDate The end date
     * @return Mono of BigDecimal
     */
    Mono<BigDecimal> calculateTotalDeposits(UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Calculate total withdrawals for an account space within a date range
     * @param accountSpaceId The account space ID
     * @param startDate The start date
     * @param endDate The end date
     * @return Mono of BigDecimal
     */
    Mono<BigDecimal> calculateTotalWithdrawals(UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get the balance of an account space at a specific point in time
     * @param accountSpaceId The account space ID
     * @param dateTime The date and time
     * @return Mono of BigDecimal
     */
    Mono<BigDecimal> getBalanceAtDateTime(UUID accountSpaceId, LocalDateTime dateTime);
}
