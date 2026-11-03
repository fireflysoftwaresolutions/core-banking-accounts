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


package io.fireflysoftware.core.banking.accounts.interfaces.dtos.provider;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.provider.ProviderStatusEnum;
import org.fireflyframework.utils.annotations.FilterableId;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AccountProviderDTO extends BaseDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID accountProviderId;

    @FilterableId
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotBlank(message = "Provider name is required")
    @Size(max = 100, message = "Provider name must not exceed 100 characters")
    private String providerName;

    @Size(max = 100, message = "External reference must not exceed 100 characters")
    private String externalReference;

    @NotNull(message = "Status is required")
    private ProviderStatusEnum status;

    @FilterableId
    private UUID accountSpaceId;
}
