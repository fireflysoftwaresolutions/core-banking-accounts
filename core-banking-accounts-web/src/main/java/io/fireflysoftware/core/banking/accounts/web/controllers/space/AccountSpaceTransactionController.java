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


package io.fireflysoftware.core.banking.accounts.web.controllers.space;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.core.services.space.AccountSpaceTransactionService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.SpaceTransactionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Controller for managing account space transactions.
 * <p>
 * This controller provides endpoints for recording and retrieving financial transactions
 * specific to account spaces. It allows tracking deposits, withdrawals, and historical balances
 * for each space, enabling detailed financial management within account spaces.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Record financial transactions (deposits and withdrawals) for spaces</li>
 *   <li>Retrieve transaction history with pagination</li>
 *   <li>Filter transactions by date range</li>
 *   <li>Calculate financial summaries (total deposits, withdrawals)</li>
 *   <li>Retrieve historical balances at specific points in time</li>
 * </ul>
 * </p>
 */
@Tag(name = "Account Space Transactions", description = "APIs for managing financial transactions within account spaces")
@RestController
@RequestMapping("/api/v1/account-spaces/{accountSpaceId}/transactions")
public class AccountSpaceTransactionController {

    private static final Logger logger = LoggerFactory.getLogger(AccountSpaceTransactionController.class);

    @Autowired
    private AccountSpaceTransactionService service;

    /**
     * Common error handling method for controller endpoints
     * @param e The exception that occurred
     * @param <T> The type of response entity
     * @return A response entity with appropriate status code and error message
     */
    private <T> Mono<ResponseEntity<T>> handleError(Throwable e) {
        logger.error("Error in AccountSpaceTransactionController: {}", e.getMessage());

        if (e instanceof IllegalArgumentException) {
            return Mono.just(ResponseEntity.badRequest().build());
        } else if (e instanceof IllegalStateException) {
            return Mono.just(ResponseEntity.badRequest().build());
        } else {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    @Operation(
            summary = "Record Transaction",
            description = "Records a new financial transaction for an account space.\n\n" +
                    "This endpoint allows recording deposits (positive amounts) or withdrawals (negative amounts) " +
                    "for a specific account space. Each transaction affects the space's balance and is recorded with " +
                    "a timestamp, description, and optional reference ID for cross-system tracking.\n\n" +
                    "**Business Rules:**\n" +
                    "* Positive amounts represent deposits into the space\n" +
                    "* Negative amounts represent withdrawals from the space\n" +
                    "* The space must exist and be active\n" +
                    "* For withdrawals, the space must have sufficient balance"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully recorded the transaction",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SpaceTransactionDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Account space not found",
                    content = @Content)
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<SpaceTransactionDTO>> recordTransaction(
            @Parameter(description = "Unique identifier of the account space", required = true)
            @PathVariable("accountSpaceId") UUID accountSpaceId,

            @Parameter(description = "Transaction amount (positive for deposits, negative for withdrawals)", required = true)
            @RequestParam("amount") BigDecimal amount,

            @Parameter(description = "Transaction description")
            @RequestParam(value = "description", required = false) String description,

            @Parameter(description = "Reference ID for cross-system tracking")
            @RequestParam(value = "referenceId", required = false) String referenceId
    ) {
        return service.recordTransaction(accountSpaceId, amount, description, referenceId)
                .map(transaction -> ResponseEntity.status(201).body(transaction))
                .onErrorResume(this::handleError);
    }

    @Operation(
            summary = "Get Transactions",
            description = "Retrieves a paginated list of all transactions for an account space.\n\n" +
                    "This endpoint returns the complete transaction history for a specific account space, " +
                    "including deposits, withdrawals, and other financial movements. Results are paginated " +
                    "to handle large transaction volumes efficiently.\n\n" +
                    "The response includes:\n" +
                    "* Transaction amounts and types\n" +
                    "* Transaction timestamps\n" +
                    "* Balance after each transaction\n" +
                    "* Transaction descriptions and reference IDs\n" +
                    "* Pagination metadata (total elements, total pages)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the transactions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "No transactions found",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<SpaceTransactionDTO>>> getTransactions(
            @Parameter(description = "Unique identifier of the account space", required = true)
            @PathVariable("accountSpaceId") UUID accountSpaceId,

            @ParameterObject
            @ModelAttribute PaginationRequest paginationRequest
    ) {
        return service.getTransactions(accountSpaceId, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(this::handleError);
    }

    @Operation(
            summary = "Get Transactions by Date Range",
            description = "Retrieves a paginated list of transactions for an account space within a specific date range.\n\n" +
                    "This endpoint allows filtering the transaction history by start and end dates, making it " +
                    "easier to analyze financial activity during specific time periods. Results are paginated " +
                    "to handle large transaction volumes efficiently.\n\n" +
                    "**Use cases:**\n" +
                    "* Generating monthly or quarterly transaction reports\n" +
                    "* Analyzing spending patterns over specific periods\n" +
                    "* Reconciling transactions for a particular timeframe\n\n" +
                    "The date range is inclusive of both the start and end dates specified."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the transactions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "No transactions found",
                    content = @Content)
    })
    @GetMapping(value = "/filter/date", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<SpaceTransactionDTO>>> getTransactionsByDateRange(
            @Parameter(description = "Unique identifier of the account space", required = true)
            @PathVariable("accountSpaceId") UUID accountSpaceId,

            @Parameter(description = "Start date of the range (inclusive)", required = true)
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,

            @Parameter(description = "End date of the range (inclusive)", required = true)
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,

            @ParameterObject
            @ModelAttribute PaginationRequest paginationRequest
    ) {
        return service.getTransactionsByDateRange(accountSpaceId, startDate, endDate, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(this::handleError);
    }

    @Operation(
            summary = "Calculate Total Deposits",
            description = "Calculates the sum of all deposits made to an account space within a specified date range.\n\n" +
                    "This endpoint aggregates all positive financial transactions (deposits) within the given " +
                    "time period and returns the total amount. This is useful for financial reporting, " +
                    "analyzing saving patterns, and monitoring space funding.\n\n" +
                    "**Notes:**\n" +
                    "* Only positive transactions (deposits) are included in the calculation\n" +
                    "* The calculation includes transactions on the start and end dates (inclusive range)\n" +
                    "* Returns zero if no deposits were made during the specified period"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully calculated the total deposits",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BigDecimal.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @GetMapping(value = "/analytics/deposits", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BigDecimal>> calculateTotalDeposits(
            @Parameter(description = "Unique identifier of the account space", required = true)
            @PathVariable("accountSpaceId") UUID accountSpaceId,

            @Parameter(description = "Start date of the range (inclusive)", required = true)
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,

            @Parameter(description = "End date of the range (inclusive)", required = true)
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        return service.calculateTotalDeposits(accountSpaceId, startDate, endDate)
                .map(ResponseEntity::ok)
                .onErrorResume(this::handleError);
    }

    @Operation(
            summary = "Calculate Total Withdrawals",
            description = "Calculates the sum of all withdrawals made from an account space within a specified date range.\n\n" +
                    "This endpoint aggregates all negative financial transactions (withdrawals) within the given " +
                    "time period and returns the total amount as a positive number. This is useful for expense tracking, " +
                    "budget analysis, and monitoring space utilization.\n\n" +
                    "**Notes:**\n" +
                    "* Only negative transactions (withdrawals) are included in the calculation\n" +
                    "* The result is returned as a positive number for easier consumption\n" +
                    "* The calculation includes transactions on the start and end dates (inclusive range)\n" +
                    "* Returns zero if no withdrawals were made during the specified period"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully calculated the total withdrawals",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BigDecimal.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @GetMapping(value = "/analytics/withdrawals", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BigDecimal>> calculateTotalWithdrawals(
            @Parameter(description = "Unique identifier of the account space", required = true)
            @PathVariable("accountSpaceId") UUID accountSpaceId,

            @Parameter(description = "Start date of the range (inclusive)", required = true)
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,

            @Parameter(description = "End date of the range (inclusive)", required = true)
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        return service.calculateTotalWithdrawals(accountSpaceId, startDate, endDate)
                .map(ResponseEntity::ok)
                .onErrorResume(this::handleError);
    }

    @Operation(
            summary = "Get Balance at Date Time",
            description = "Retrieves the historical balance of an account space at a specific point in time.\n\n" +
                    "This endpoint provides the ability to view what the balance of a space was at any " +
                    "historical moment. It uses the transaction history to calculate the balance as it " +
                    "existed at the exact specified date and time.\n\n" +
                    "**Use cases:**\n" +
                    "* Historical financial reporting and reconciliation\n" +
                    "* Auditing and compliance verification\n" +
                    "* Analyzing balance trends over time\n\n" +
                    "If the specified time predates the space's creation, the endpoint returns zero."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the balance",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BigDecimal.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @GetMapping(value = "/history/balance", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BigDecimal>> getBalanceAtDateTime(
            @Parameter(description = "Unique identifier of the account space", required = true)
            @PathVariable("accountSpaceId") UUID accountSpaceId,

            @Parameter(description = "Date and time to get the balance at", required = true)
            @RequestParam("dateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime
    ) {
        return service.getBalanceAtDateTime(accountSpaceId, dateTime)
                .map(ResponseEntity::ok)
                .onErrorResume(this::handleError);
    }
}
