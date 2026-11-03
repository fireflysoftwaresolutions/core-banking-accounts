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


package io.fireflysoftware.core.banking.accounts.interfaces.dtos.core;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountStatusEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountSubTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.interest.InterestAccrualMethodEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.interest.InterestPaymentFrequencyEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.regulatory.RegulatoryStatusEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.regulatory.TaxReportingStatusEnum;
import org.fireflyframework.utils.annotations.FilterableId;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AccountDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID accountId;

    @FilterableId
    @NotNull(message = "Contract ID is required")
    private UUID contractId;

    @NotBlank(message = "Account number is required")
    @Size(max = 50, message = "Account number must not exceed 50 characters")
    private String accountNumber;

    @NotNull(message = "Account type is required")
    private AccountTypeEnum accountType;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    private String currency;

    @NotNull(message = "Open date is required")
    @PastOrPresent(message = "Open date cannot be in the future")
    private LocalDate openDate;

    @Future(message = "Close date must be in the future")
    private LocalDate closeDate;

    @NotNull(message = "Account status is required")
    private AccountStatusEnum accountStatus;

    @FilterableId
    @NotNull(message = "Branch ID is required")
    private UUID branchId;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private AccountSubTypeEnum accountSubType;
    private TaxReportingStatusEnum taxReportingStatus;
    private RegulatoryStatusEnum regulatoryStatus;

    @Future(message = "Maturity date must be in the future")
    private LocalDate maturityDate;

    private InterestAccrualMethodEnum interestAccrualMethod;
    private InterestPaymentFrequencyEnum interestPaymentFrequency;

    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum balance cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Minimum balance must have at most 15 integer digits and 2 decimal places")
    private BigDecimal minimumBalance;

    @DecimalMin(value = "0.0", inclusive = true, message = "Overdraft limit cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Overdraft limit must have at most 15 integer digits and 2 decimal places")
    private BigDecimal overdraftLimit;

    // Crypto-specific fields
    @Size(max = 100, message = "Wallet address must not exceed 100 characters")
    private String walletAddress;

    @Size(max = 50, message = "Blockchain network must not exceed 50 characters")
    private String blockchainNetwork;

    @Size(max = 100, message = "Token contract address must not exceed 100 characters")
    private String tokenContractAddress;

    @Size(max = 20, message = "Token standard must not exceed 20 characters")
    private String tokenStandard;

    private Boolean isCustodial;
}