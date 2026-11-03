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


package io.fireflysoftware.core.banking.accounts.core.services.space.impl;

import io.fireflysoftware.core.banking.accounts.core.mappers.space.AccountSpaceMapper;
import io.fireflysoftware.core.banking.accounts.core.services.core.AccountBalanceService;
import io.fireflysoftware.core.banking.accounts.core.services.space.AccountSpaceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountBalanceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.AccountSpaceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.SpaceAnalyticsDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.BalanceTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.TransferFrequencyEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.space.AccountSpace;
import io.fireflysoftware.core.banking.accounts.models.repositories.space.AccountSpaceRepository;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AccountSpaceServiceImpl implements AccountSpaceService {

    private static final Logger logger = LoggerFactory.getLogger(AccountSpaceServiceImpl.class);
    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal(100);

    // Error messages
    private static final String ERROR_SPACE_NOT_FOUND = "Account space not found with ID: %s";
    private static final String ERROR_INSUFFICIENT_FUNDS = "Insufficient funds in source space. Available: %s, Requested: %s";
    private static final String ERROR_NEGATIVE_AMOUNT = "Transfer amount must be positive, got: %s";
    private static final String ERROR_DIFFERENT_ACCOUNTS = "Cannot transfer between spaces of different accounts";
    private static final String ERROR_MAIN_SPACE_DELETION = "Cannot delete the main account space";
    private static final String ERROR_INVALID_TRANSFER_CONFIG = "Frequency and amount are required and amount must be positive when enabling automatic transfers";
    private static final String ERROR_SOURCE_SPACE_INVALID = "Source space must belong to the same account";
    private static final String ERROR_INVALID_DATE_RANGE = "Invalid date range: start date must be before end date";
    private static final String ERROR_MONTHS_POSITIVE = "Months must be positive, got: %s";
    private static final String ERROR_SPACE_ALREADY_FROZEN = "Account space is already frozen";
    private static final String ERROR_SPACE_NOT_FROZEN = "Account space is not frozen";
    private static final String ERROR_NEGATIVE_BALANCE = "New balance cannot be negative";
    private static final String ERROR_REASON_REQUIRED = "Reason is required for balance adjustments";

    @Autowired
    private AccountSpaceRepository repository;

    @Autowired
    private AccountSpaceMapper mapper;

    @Autowired
    private AccountBalanceService accountBalanceService;

    @Override
    public Mono<PaginationResponse<AccountSpaceDTO>> filterAccountSpaces(FilterRequest<AccountSpaceDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        AccountSpace.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    private static final String ERROR_ACCOUNT_ID_REQUIRED = "Account ID is required for creating a space";
    private static final String ERROR_SPACE_NAME_REQUIRED = "Space name is required";
    private static final String ERROR_SPACE_TYPE_REQUIRED = "Space type is required";
    private static final String ERROR_NEGATIVE_TARGET = "Target amount cannot be negative";
    private static final String ERROR_TARGET_DATE_PAST = "Target date cannot be in the past";

    @Override
    public Mono<AccountSpaceDTO> createAccountSpace(AccountSpaceDTO accountSpaceDTO) {
        // Validate required fields
        if (accountSpaceDTO.getAccountId() == null) {
            return Mono.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }

        if (accountSpaceDTO.getSpaceName() == null || accountSpaceDTO.getSpaceName().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException(ERROR_SPACE_NAME_REQUIRED));
        }

        if (accountSpaceDTO.getSpaceType() == null) {
            return Mono.error(new IllegalArgumentException(ERROR_SPACE_TYPE_REQUIRED));
        }

        // Ensure new spaces have a non-null balance
        if (accountSpaceDTO.getBalance() == null) {
            accountSpaceDTO.setBalance(BigDecimal.ZERO);
        } else if (accountSpaceDTO.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            return Mono.error(new IllegalArgumentException(ERROR_NEGATIVE_BALANCE));
        }

        // Validate target amount if present
        if (accountSpaceDTO.getTargetAmount() != null &&
            accountSpaceDTO.getTargetAmount().compareTo(BigDecimal.ZERO) < 0) {
            return Mono.error(new IllegalArgumentException(ERROR_NEGATIVE_TARGET));
        }

        // Validate target date if present
        if (accountSpaceDTO.getTargetDate() != null &&
            accountSpaceDTO.getTargetDate().isBefore(LocalDateTime.now())) {
            return Mono.error(new IllegalArgumentException(ERROR_TARGET_DATE_PAST));
        }

        // Ensure isVisible is set if null
        if (accountSpaceDTO.getIsVisible() == null) {
            accountSpaceDTO.setIsVisible(true);
        }

        AccountSpace accountSpace = mapper.toEntity(accountSpaceDTO);
        return repository.save(accountSpace)
                .map(mapper::toDTO);
    }

    private static final String ERROR_SPACE_ID_REQUIRED_GET = "Account space ID is required";
    private static final String ERROR_SPACE_NOT_FOUND_GET = "Account space not found with ID: %s";

    @Override
    public Mono<AccountSpaceDTO> getAccountSpace(UUID accountSpaceId) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_SPACE_ID_REQUIRED_GET));
        }

        return repository.findById(accountSpaceId)
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.empty());
    }

    private static final String ERROR_SPACE_NOT_FOUND_UPDATE = "Account space not found for update with ID: %s";
    private static final String ERROR_CANNOT_CHANGE_TYPE = "Cannot change the type of an existing space";

    @Override
    public Mono<AccountSpaceDTO> updateAccountSpace(UUID accountSpaceId, AccountSpaceDTO accountSpaceDTO) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException("Account space ID is required for update"));
        }

        return repository.findById(accountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(String.format(ERROR_SPACE_NOT_FOUND_UPDATE, accountSpaceId))))
                .flatMap(existingSpace -> {
                    // Validate space name if provided
                    if (accountSpaceDTO.getSpaceName() != null && accountSpaceDTO.getSpaceName().trim().isEmpty()) {
                        return Mono.error(new IllegalArgumentException(ERROR_SPACE_NAME_REQUIRED));
                    }

                    // Validate space type - don't allow changing the type
                    if (accountSpaceDTO.getSpaceType() != null &&
                        existingSpace.getSpaceType() != accountSpaceDTO.getSpaceType()) {
                        return Mono.error(new IllegalArgumentException(ERROR_CANNOT_CHANGE_TYPE));
                    }

                    // Validate balance if provided
                    if (accountSpaceDTO.getBalance() != null &&
                        accountSpaceDTO.getBalance().compareTo(BigDecimal.ZERO) < 0) {
                        return Mono.error(new IllegalArgumentException(ERROR_NEGATIVE_BALANCE));
                    }

                    // Validate target amount if provided
                    if (accountSpaceDTO.getTargetAmount() != null &&
                        accountSpaceDTO.getTargetAmount().compareTo(BigDecimal.ZERO) < 0) {
                        return Mono.error(new IllegalArgumentException(ERROR_NEGATIVE_TARGET));
                    }

                    // Validate target date if provided
                    if (accountSpaceDTO.getTargetDate() != null &&
                        accountSpaceDTO.getTargetDate().isBefore(LocalDateTime.now())) {
                        return Mono.error(new IllegalArgumentException(ERROR_TARGET_DATE_PAST));
                    }

                    AccountSpace updatedSpace = mapper.toEntity(accountSpaceDTO);
                    updatedSpace.setAccountSpaceId(accountSpaceId);
                    // Preserve the accountId from the existing space
                    updatedSpace.setAccountId(existingSpace.getAccountId());

                    // Preserve the space type from the existing space
                    updatedSpace.setSpaceType(existingSpace.getSpaceType());

                    return repository.save(updatedSpace);
                })
                .map(mapper::toDTO);
    }

    private static final String ERROR_SPACE_ID_REQUIRED = "Account space ID is required for deletion";
    private static final String ERROR_SPACE_NOT_FOUND_DELETE = "Account space not found for deletion with ID: %s";
    private static final String ERROR_NON_ZERO_BALANCE = "Cannot delete space with non-zero balance. Transfer funds first.";

    @Override
    public Mono<Void> deleteAccountSpace(UUID accountSpaceId) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_SPACE_ID_REQUIRED));
        }

        return repository.findById(accountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_SPACE_NOT_FOUND_DELETE, accountSpaceId))))
                .flatMap(space -> {
                    // Don't allow deletion of MAIN spaces
                    if (space.getSpaceType() == AccountSpaceTypeEnum.MAIN) {
                        return Mono.error(new IllegalStateException(ERROR_MAIN_SPACE_DELETION));
                    }

                    // Don't allow deletion of spaces with non-zero balance
                    if (space.getBalance() != null && space.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                        return Mono.error(new IllegalStateException(ERROR_NON_ZERO_BALANCE));
                    }

                    return repository.delete(space);
                });
    }

    @Override
    public Flux<AccountSpaceDTO> getAccountSpacesByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<AccountSpaceDTO>> getAccountSpacesByAccountId(UUID accountId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        return repository.countByAccountId(accountId)
                .flatMap(total ->
                    repository.findByAccountId(accountId, pageRequest)
                        .map(mapper::toDTO)
                        .collectList()
                        .map(items -> new PaginationResponse<>(
                            items,
                            page,
                            size,
                            total.intValue()
                        ))
                );
    }

    private static final String ERROR_SPACE_NOT_FOUND_TRANSFER = "Account space not found for transfer with ID: %s";
    private static final String ERROR_SAME_SPACE = "Cannot transfer funds to the same space";

    @Override
    public Mono<Boolean> transferBetweenSpaces(UUID fromAccountSpaceId, UUID toAccountSpaceId, BigDecimal amount) {
        // Validate IDs
        if (fromAccountSpaceId == null || toAccountSpaceId == null) {
            return Mono.error(new IllegalArgumentException("Source and destination space IDs are required"));
        }

        // Validate not same space
        if (fromAccountSpaceId.equals(toAccountSpaceId)) {
            return Mono.error(new IllegalArgumentException(ERROR_SAME_SPACE));
        }

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException(String.format(ERROR_NEGATIVE_AMOUNT, amount)));
        }

        // Check that spaces exist and belong to the same account
        Mono<AccountSpace> fromSpaceMono = repository.findById(fromAccountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_SPACE_NOT_FOUND_TRANSFER, fromAccountSpaceId))));

        Mono<AccountSpace> toSpaceMono = repository.findById(toAccountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_SPACE_NOT_FOUND_TRANSFER, toAccountSpaceId))));

        return Mono.zip(fromSpaceMono, toSpaceMono)
            .flatMap(tuple -> {
                AccountSpace fromSpace = tuple.getT1();
                AccountSpace toSpace = tuple.getT2();

                // Verify spaces belong to the same account
                if (!fromSpace.getAccountId().equals(toSpace.getAccountId())) {
                    return Mono.error(new IllegalArgumentException(ERROR_DIFFERENT_ACCOUNTS));
                }

                // Verify sufficient funds
                if (fromSpace.getBalance().compareTo(amount) < 0) {
                    return Mono.error(new IllegalArgumentException(
                            String.format(ERROR_INSUFFICIENT_FUNDS,
                                    fromSpace.getBalance().toString(), amount.toString())));
                }

                // Update balances
                BigDecimal fromSpaceNewBalance = fromSpace.getBalance().subtract(amount);
                BigDecimal toSpaceNewBalance = toSpace.getBalance().add(amount);
                fromSpace.setBalance(fromSpaceNewBalance);
                toSpace.setBalance(toSpaceNewBalance);

                // Create balance history records
                LocalDateTime now = LocalDateTime.now();
                UUID accountId = fromSpace.getAccountId(); // Both spaces have the same accountId

                // Create balance record for source space
                AccountBalanceDTO fromSpaceBalanceDTO = AccountBalanceDTO.builder()
                        .accountId(accountId)
                        .accountSpaceId(fromAccountSpaceId)
                        .balanceType(BalanceTypeEnum.CURRENT)
                        .balanceAmount(fromSpaceNewBalance)
                        .asOfDatetime(now)
                        .build();

                // Create balance record for destination space
                AccountBalanceDTO toSpaceBalanceDTO = AccountBalanceDTO.builder()
                        .accountId(accountId)
                        .accountSpaceId(toAccountSpaceId)
                        .balanceType(BalanceTypeEnum.CURRENT)
                        .balanceAmount(toSpaceNewBalance)
                        .asOfDatetime(now)
                        .build();

                // Save spaces and balance records
                return Mono.when(
                    repository.save(fromSpace),
                    repository.save(toSpace),
                    accountBalanceService.createBalance(accountId, fromSpaceBalanceDTO),
                    accountBalanceService.createBalance(accountId, toSpaceBalanceDTO)
                ).thenReturn(true);
            });
    }

    // ===== Goal Tracking Methods =====

    @Override
    public Mono<AccountSpaceDTO> calculateGoalProgress(UUID accountSpaceId) {
        return repository.findById(accountSpaceId)
                .flatMap(space -> {
                    // If there's no target amount, we can't calculate progress
                    if (space.getTargetAmount() == null) {
                        return Mono.just(mapper.toDTO(space));
                    }

                    AccountSpaceDTO dto = mapper.toDTO(space);

                    // Calculate progress percentage
                    BigDecimal progressPercentage = space.getBalance()
                            .multiply(ONE_HUNDRED)
                            .divide(space.getTargetAmount(), MATH_CONTEXT);
                    dto.setGoalProgressPercentage(progressPercentage);

                    // Calculate remaining amount
                    BigDecimal remaining = space.getTargetAmount().subtract(space.getBalance());
                    if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                        remaining = BigDecimal.ZERO;
                    }
                    dto.setRemainingToTarget(remaining);

                    // Determine if goal is completed
                    boolean isCompleted = space.getBalance().compareTo(space.getTargetAmount()) >= 0;
                    dto.setIsGoalCompleted(isCompleted);

                    // Calculate estimated completion date if we have a target date
                    if (space.getTargetDate() != null && !isCompleted) {
                        // If balance is zero, we can't estimate
                        if (space.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                            return Mono.just(dto);
                        }

                        // Calculate average daily growth based on current balance and creation date
                        LocalDateTime now = LocalDateTime.now();
                        long daysSinceCreation = ChronoUnit.DAYS.between(space.getDateCreated(), now);
                        if (daysSinceCreation <= 0) {
                            daysSinceCreation = 1; // Avoid division by zero
                        }

                        BigDecimal dailyGrowth = space.getBalance().divide(new BigDecimal(daysSinceCreation), MATH_CONTEXT);

                        // If no growth, we can't estimate
                        if (dailyGrowth.compareTo(BigDecimal.ZERO) <= 0) {
                            return Mono.just(dto);
                        }

                        // Calculate days needed to reach target
                        BigDecimal daysNeeded = remaining.divide(dailyGrowth, MATH_CONTEXT);

                        // Calculate estimated completion date
                        LocalDateTime estimatedDate = now.plusDays(daysNeeded.longValue());
                        dto.setEstimatedCompletionDate(estimatedDate);
                    }

                    return Mono.just(dto);
                });
    }

    @Override
    public Flux<AccountSpaceDTO> getSpacesWithGoals(UUID accountId) {
        return repository.findByAccountIdAndTargetAmountIsNotNull(accountId)
                .flatMap(space -> Mono.just(space)
                        .flatMap(s -> calculateGoalProgress(s.getAccountSpaceId())));
    }

    @Override
    public Flux<AccountSpaceDTO> getSpacesWithUpcomingTargetDates(UUID accountId, int daysThreshold) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thresholdDate = now.plusDays(daysThreshold);

        return repository.findByAccountIdAndTargetDateGreaterThanEqual(accountId, now)
                .filter(space -> space.getTargetDate().isBefore(thresholdDate))
                .flatMap(space -> Mono.just(space)
                        .flatMap(s -> calculateGoalProgress(s.getAccountSpaceId())));
    }

    // ===== Automatic Transfer Methods =====

    @Override
    public Mono<AccountSpaceDTO> configureAutomaticTransfers(
            UUID accountSpaceId,
            Boolean enabled,
            TransferFrequencyEnum frequency,
            BigDecimal amount,
            UUID sourceSpaceId
    ) {
        return repository.findById(accountSpaceId)
                .flatMap(space -> {
                    // Validate parameters
                    if (enabled && (frequency == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)) {
                        return Mono.error(new IllegalArgumentException(
                                "Frequency and amount are required and amount must be positive when enabling automatic transfers"));
                    }

                    // If source space is provided, verify it exists and belongs to the same account
                    Mono<Boolean> sourceSpaceCheck = sourceSpaceId == null ?
                            Mono.just(true) :
                            repository.findById(sourceSpaceId)
                                    .map(sourceSpace -> sourceSpace.getAccountId().equals(space.getAccountId()))
                                    .defaultIfEmpty(false);

                    return sourceSpaceCheck.flatMap(validSource -> {
                        if (!validSource) {
                            return Mono.error(new IllegalArgumentException(
                                    "Source space must belong to the same account"));
                        }

                        // Update space with automatic transfer configuration
                        space.setEnableAutomaticTransfers(enabled);
                        if (enabled) {
                            space.setTransferFrequency(frequency);
                            space.setTransferAmount(amount);
                            space.setSourceSpaceId(sourceSpaceId);
                        } else {
                            // Clear configuration if disabling
                            space.setTransferFrequency(null);
                            space.setTransferAmount(null);
                            space.setSourceSpaceId(null);
                        }

                        return repository.save(space).map(mapper::toDTO);
                    });
                });
    }

    @Override
    public Mono<Integer> executeAutomaticTransfers(UUID accountId) {
        // Find all spaces with automatic transfers enabled for this account
        return repository.findByAccountId(accountId)
                .filter(space -> Boolean.TRUE.equals(space.getEnableAutomaticTransfers()))
                .flatMap(space -> {
                    // Skip if missing required configuration
                    if (space.getTransferFrequency() == null ||
                        space.getTransferAmount() == null ||
                        space.getTransferAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        logger.warn("Skipping automatic transfer for space {} due to invalid configuration",
                                space.getAccountSpaceId());
                        return Mono.empty();
                    }

                    // Determine source space ID
                    UUID sourceSpaceId = space.getSourceSpaceId();
                    if (sourceSpaceId == null) {
                        // Find MAIN space if source not specified
                        return repository.findByAccountIdAndSpaceType(accountId, AccountSpaceTypeEnum.MAIN)
                                .next()
                                .flatMap(mainSpace ->
                                    transferBetweenSpaces(mainSpace.getAccountSpaceId(), space.getAccountSpaceId(),
                                            space.getTransferAmount())
                                    .thenReturn(1)
                                    .onErrorResume(e -> {
                                        logger.error("Error executing automatic transfer from MAIN to {}: {}",
                                                space.getAccountSpaceId(), e.getMessage());
                                        return Mono.just(0);
                                    })
                                );
                    } else {
                        // Use specified source space
                        return transferBetweenSpaces(sourceSpaceId, space.getAccountSpaceId(),
                                space.getTransferAmount())
                                .thenReturn(1)
                                .onErrorResume(e -> {
                                    logger.error("Error executing automatic transfer from {} to {}: {}",
                                            sourceSpaceId, space.getAccountSpaceId(), e.getMessage());
                                    return Mono.just(0);
                                });
                    }
                })
                .reduce(0, Integer::sum);
    }

    @Override
    public Mono<Map<UUID, BigDecimal>> simulateFutureBalances(UUID accountId, int months) {
        if (months <= 0) {
            return Mono.error(new IllegalArgumentException("Months must be positive"));
        }

        // Get all spaces for the account
        return repository.findByAccountId(accountId)
                .collectList()
                .flatMap(spaces -> {
                    Map<UUID, BigDecimal> projectedBalances = new HashMap<>();

                    // Initialize with current balances
                    spaces.forEach(space ->
                        projectedBalances.put(space.getAccountSpaceId(), space.getBalance())
                    );

                    // Get spaces with automatic transfers
                    Map<UUID, AccountSpace> spacesWithTransfers = new HashMap<>();
                    Map<UUID, AccountSpace> allSpacesMap = new HashMap<>();

                    spaces.forEach(space -> {
                        allSpacesMap.put(space.getAccountSpaceId(), space);
                        if (Boolean.TRUE.equals(space.getEnableAutomaticTransfers()) &&
                            space.getTransferFrequency() != null &&
                            space.getTransferAmount() != null &&
                            space.getTransferAmount().compareTo(BigDecimal.ZERO) > 0) {
                            spacesWithTransfers.put(space.getAccountSpaceId(), space);
                        }
                    });

                    // Find MAIN space
                    AccountSpace mainSpace = spaces.stream()
                            .filter(s -> s.getSpaceType() == AccountSpaceTypeEnum.MAIN)
                            .findFirst()
                            .orElse(null);

                    // Simulate transfers for each month
                    for (int month = 0; month < months; month++) {
                        // Process each space with automatic transfers
                        for (AccountSpace space : spacesWithTransfers.values()) {
                            // Skip if no transfer frequency or amount
                            if (space.getTransferFrequency() == null || space.getTransferAmount() == null) {
                                continue;
                            }

                            // Determine if transfer should occur this month based on frequency
                            boolean shouldTransfer = false;
                            switch (space.getTransferFrequency()) {
                                case DAILY:
                                    shouldTransfer = true;
                                    break;
                                case WEEKLY:
                                    shouldTransfer = true;
                                    break;
                                case MONTHLY:
                                    shouldTransfer = true;
                                    break;
                                case QUARTERLY:
                                    shouldTransfer = month % 3 == 0;
                                    break;
                                case ANNUALLY:
                                    shouldTransfer = month % 12 == 0;
                                    break;
                            }

                            if (!shouldTransfer) {
                                continue;
                            }

                            // Calculate transfer amount based on frequency
                            BigDecimal transferAmount = space.getTransferAmount();
                            if (space.getTransferFrequency() == TransferFrequencyEnum.DAILY) {
                                transferAmount = transferAmount.multiply(new BigDecimal(30)); // Approximate days in month
                            } else if (space.getTransferFrequency() == TransferFrequencyEnum.WEEKLY) {
                                transferAmount = transferAmount.multiply(new BigDecimal(4)); // Approximate weeks in month
                            }

                            // Determine source space
                            UUID sourceSpaceId = space.getSourceSpaceId();
                            AccountSpace sourceSpace = sourceSpaceId != null ?
                                    allSpacesMap.get(sourceSpaceId) : mainSpace;

                            if (sourceSpace == null) {
                                continue; // Skip if source space not found
                            }

                            // Get current projected balances
                            BigDecimal sourceBalance = projectedBalances.get(sourceSpace.getAccountSpaceId());
                            BigDecimal targetBalance = projectedBalances.get(space.getAccountSpaceId());

                            // Skip if insufficient funds
                            if (sourceBalance.compareTo(transferAmount) < 0) {
                                continue;
                            }

                            // Update projected balances
                            projectedBalances.put(sourceSpace.getAccountSpaceId(), sourceBalance.subtract(transferAmount));
                            projectedBalances.put(space.getAccountSpaceId(), targetBalance.add(transferAmount));
                        }
                    }

                    return Mono.just(projectedBalances);
                });
    }

    // ===== Analytics Methods =====

    @Override
    public Mono<Map<UUID, BigDecimal>> calculateBalanceDistribution(UUID accountId) {
        return repository.calculateTotalBalance(accountId)
                .flatMap(totalBalance -> {
                    if (totalBalance == null || totalBalance.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.just(new HashMap<UUID, BigDecimal>());
                    }

                    return repository.findByAccountId(accountId)
                            .collectList()
                            .map(spaces -> {
                                Map<UUID, BigDecimal> distribution = new HashMap<>();

                                spaces.forEach(space -> {
                                    BigDecimal percentage = space.getBalance()
                                            .multiply(ONE_HUNDRED)
                                            .divide(totalBalance, MATH_CONTEXT);
                                    distribution.put(space.getAccountSpaceId(), percentage);
                                });

                                return distribution;
                            });
                });
    }

    @Override
    public Mono<Map<UUID, BigDecimal>> calculateGrowthRates(
            UUID accountId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        // For a real implementation, we would need historical balance data
        // This is a simplified version that assumes linear growth from creation date
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return Mono.error(new IllegalArgumentException("Invalid date range"));
        }

        return repository.findByAccountId(accountId)
                .collectList()
                .map(spaces -> {
                    Map<UUID, BigDecimal> growthRates = new HashMap<>();

                    spaces.forEach(space -> {
                        // Skip spaces created after the start date
                        if (space.getDateCreated().isAfter(startDate)) {
                            growthRates.put(space.getAccountSpaceId(), BigDecimal.ZERO);
                            return;
                        }

                        // Calculate days in the period
                        long daysInPeriod = ChronoUnit.DAYS.between(startDate, endDate);
                        if (daysInPeriod <= 0) {
                            daysInPeriod = 1; // Avoid division by zero
                        }

                        // Calculate daily growth rate (simplified)
                        BigDecimal dailyRate = space.getBalance()
                                .divide(new BigDecimal(daysInPeriod), MATH_CONTEXT)
                                .multiply(ONE_HUNDRED)
                                .divide(space.getBalance().max(BigDecimal.ONE), MATH_CONTEXT);

                        growthRates.put(space.getAccountSpaceId(), dailyRate);
                    });

                    return growthRates;
                });
    }

    @Override
    public Flux<AccountSpaceDTO> getSpacesByType(UUID accountId, AccountSpaceTypeEnum spaceType) {
        return repository.findByAccountIdAndSpaceType(accountId, spaceType)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountSpaceDTO> freezeAccountSpace(UUID accountSpaceId) {
        return repository.findById(accountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_SPACE_NOT_FOUND, accountSpaceId))))
                .flatMap(space -> {
                    // Check if space is already frozen
                    if (Boolean.TRUE.equals(space.getIsFrozen())) {
                        return Mono.error(new IllegalStateException(ERROR_SPACE_ALREADY_FROZEN));
                    }

                    // Set frozen status
                    space.setIsFrozen(true);
                    space.setFrozenDateTime(LocalDateTime.now());

                    return repository.save(space);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountSpaceDTO> unfreezeAccountSpace(UUID accountSpaceId) {
        return repository.findById(accountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_SPACE_NOT_FOUND, accountSpaceId))))
                .flatMap(space -> {
                    // Check if space is not frozen
                    if (Boolean.FALSE.equals(space.getIsFrozen())) {
                        return Mono.error(new IllegalStateException(ERROR_SPACE_NOT_FROZEN));
                    }

                    // Unset frozen status
                    space.setIsFrozen(false);
                    space.setUnfrozenDateTime(LocalDateTime.now());

                    return repository.save(space);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountSpaceDTO> updateAccountSpaceBalance(UUID accountSpaceId, BigDecimal newBalance, String reason) {
        // Validate inputs
        if (newBalance == null || newBalance.compareTo(BigDecimal.ZERO) < 0) {
            return Mono.error(new IllegalArgumentException(ERROR_NEGATIVE_BALANCE));
        }

        if (reason == null || reason.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException(ERROR_REASON_REQUIRED));
        }

        return repository.findById(accountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_SPACE_NOT_FOUND, accountSpaceId))))
                .flatMap(space -> {
                    // Store old balance for history
                    BigDecimal oldBalance = space.getBalance();

                    // Update balance
                    space.setBalance(newBalance);
                    space.setLastBalanceUpdateReason(reason);
                    space.setLastBalanceUpdateDateTime(LocalDateTime.now());

                    // Save the updated space
                    return repository.save(space)
                            .flatMap(savedSpace -> {
                                // Create a balance history record
                                AccountBalanceDTO balanceDTO = AccountBalanceDTO.builder()
                                        .accountId(savedSpace.getAccountId())
                                        .accountSpaceId(savedSpace.getAccountSpaceId())
                                        .balanceType(BalanceTypeEnum.CURRENT)
                                        .balanceAmount(newBalance)
                                        .asOfDatetime(LocalDateTime.now())
                                        .build();

                                return accountBalanceService.createBalance(savedSpace.getAccountId(), balanceDTO)
                                        .thenReturn(savedSpace);
                            });
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<SpaceAnalyticsDTO> getSpaceAnalytics(UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate) {
        // Validate date range
        if (startDate != null && endDate != null && !startDate.isBefore(endDate)) {
            return Mono.error(new IllegalArgumentException(ERROR_INVALID_DATE_RANGE));
        }

        return repository.findById(accountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_SPACE_NOT_FOUND, accountSpaceId))))
                .flatMap(space -> {
                    // Create analytics DTO with basic information
                    SpaceAnalyticsDTO analytics = new SpaceAnalyticsDTO();
                    analytics.setAccountSpaceId(space.getAccountSpaceId());
                    analytics.setAccountId(space.getAccountId());
                    analytics.setSpaceName(space.getSpaceName());
                    analytics.setSpaceType(space.getSpaceType());
                    analytics.setStartDate(startDate);
                    analytics.setEndDate(endDate);
                    analytics.setClosingBalance(space.getBalance());

                    // Get balance history for the period
                    return accountBalanceService.getSpaceBalances(
                                space.getAccountId(),
                                space.getAccountSpaceId(),
                                null) // We'll filter by date in memory
                            .map(response -> {
                                // Filter by date range if provided
                                var balanceHistory = response.getContent().stream()
                                        .filter(b -> startDate == null || !b.getAsOfDatetime().isBefore(startDate))
                                        .filter(b -> endDate == null || !b.getAsOfDatetime().isAfter(endDate))
                                        .sorted((b1, b2) -> b1.getAsOfDatetime().compareTo(b2.getAsOfDatetime()))
                                        .toList();

                                // Calculate metrics if we have balance history
                                if (!balanceHistory.isEmpty()) {
                                    // Opening and closing balances
                                    analytics.setOpeningBalance(balanceHistory.get(0).getBalanceAmount());

                                    // Min, max, average balances
                                    analytics.setLowestBalance(balanceHistory.stream()
                                            .map(AccountBalanceDTO::getBalanceAmount)
                                            .min(BigDecimal::compareTo)
                                            .orElse(BigDecimal.ZERO));

                                    analytics.setHighestBalance(balanceHistory.stream()
                                            .map(AccountBalanceDTO::getBalanceAmount)
                                            .max(BigDecimal::compareTo)
                                            .orElse(BigDecimal.ZERO));

                                    // Net change
                                    if (analytics.getOpeningBalance() != null && analytics.getClosingBalance() != null) {
                                        analytics.setNetChange(analytics.getClosingBalance().subtract(analytics.getOpeningBalance()));

                                        // Net change percentage
                                        if (analytics.getOpeningBalance().compareTo(BigDecimal.ZERO) != 0) {
                                            analytics.setNetChangePercentage(
                                                    analytics.getNetChange()
                                                            .divide(analytics.getOpeningBalance(), MATH_CONTEXT)
                                                            .multiply(ONE_HUNDRED));
                                        }
                                    }

                                    // Time series data
                                    analytics.setBalanceHistory(balanceHistory.stream()
                                            .map(b -> new SpaceAnalyticsDTO.TimeSeriesDataPoint(
                                                    b.getAsOfDatetime(), b.getBalanceAmount()))
                                            .toList());
                                }

                                // Goal tracking if applicable
                                if (space.getTargetAmount() != null && space.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
                                    BigDecimal progress = space.getBalance()
                                            .divide(space.getTargetAmount(), MATH_CONTEXT)
                                            .multiply(ONE_HUNDRED);
                                    analytics.setGoalProgress(progress);

                                    // Projected completion if we have a target date
                                    if (space.getTargetDate() != null) {
                                        analytics.setProjectedCompletionDate(space.getTargetDate());
                                    }
                                }

                                return analytics;
                            });
                });
    }
}
