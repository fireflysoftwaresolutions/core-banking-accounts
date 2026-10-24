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


package io.fireflysoftware.core.banking.accounts.interfaces.dtos.space;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.BaseDTO;
import org.fireflyframework.utils.annotations.FilterableId;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for account space transactions.
 * Represents a transaction that affects the balance of an account space.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpaceTransactionDTO extends BaseDTO {
    
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID spaceTransactionId;
    
    @FilterableId
    @NotNull(message = "Account space ID is required")
    private UUID accountSpaceId;

    @NotNull(message = "Amount is required")
    @Digits(integer = 15, fraction = 2, message = "Amount must have at most 15 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Balance after transaction is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Balance after transaction cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Balance after transaction must have at most 15 integer digits and 2 decimal places")
    private BigDecimal balanceAfterTransaction;

    @NotNull(message = "Transaction datetime is required")
    @PastOrPresent(message = "Transaction datetime cannot be in the future")
    private LocalDateTime transactionDateTime;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 100, message = "Reference ID must not exceed 100 characters")
    private String referenceId;

    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "^(DEPOSIT|WITHDRAWAL|TRANSFER_IN|TRANSFER_OUT|INTEREST|FEE)$",
             message = "Transaction type must be one of: DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT, INTEREST, FEE")
    private String transactionType;
    
    // Calculated fields
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String spaceName; // Added for convenience in UI display
    
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID accountId; // Added for convenience in UI display
}
