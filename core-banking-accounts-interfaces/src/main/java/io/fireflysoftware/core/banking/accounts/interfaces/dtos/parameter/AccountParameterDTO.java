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


package io.fireflysoftware.core.banking.accounts.interfaces.dtos.parameter;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.parameter.ParamTypeEnum;
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
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AccountParameterDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID accountParameterId;

    @FilterableId
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotNull(message = "Parameter type is required")
    private ParamTypeEnum paramType;

    @NotNull(message = "Parameter value is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Parameter value cannot be negative")
    @Digits(integer = 15, fraction = 8, message = "Parameter value must have at most 15 integer digits and 8 decimal places")
    private BigDecimal paramValue;

    @Size(max = 20, message = "Parameter unit must not exceed 20 characters")
    private String paramUnit;

    @NotNull(message = "Effective date is required")
    private LocalDateTime effectiveDate;

    @Future(message = "Expiry date must be in the future")
    private LocalDateTime expiryDate;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
