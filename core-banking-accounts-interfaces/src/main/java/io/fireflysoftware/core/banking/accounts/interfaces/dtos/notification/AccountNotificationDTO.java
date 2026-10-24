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


package io.fireflysoftware.core.banking.accounts.interfaces.dtos.notification;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.notification.NotificationTypeEnum;
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
public class AccountNotificationDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID accountNotificationId;

    @FilterableId
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotNull(message = "Notification type is required")
    private NotificationTypeEnum notificationType;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;

    @NotNull(message = "Creation datetime is required")
    @PastOrPresent(message = "Creation datetime cannot be in the future")
    private LocalDateTime creationDateTime;

    @Future(message = "Expiry datetime must be in the future")
    private LocalDateTime expiryDateTime;

    private Boolean isRead;

    private LocalDateTime readDateTime;

    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 10, message = "Priority must not exceed 10")
    private Integer priority;

    @Size(max = 100, message = "Delivery channels must not exceed 100 characters")
    private String deliveryChannels;

    @Size(max = 100, message = "Event reference must not exceed 100 characters")
    private String eventReference;

    @DecimalMin(value = "0.0", inclusive = true, message = "Related amount cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Related amount must have at most 15 integer digits and 2 decimal places")
    private BigDecimal relatedAmount;

    @Size(max = 500, message = "Action URL must not exceed 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Action URL must be a valid HTTP/HTTPS URL")
    private String actionUrl;

    @Size(max = 100, message = "Action text must not exceed 100 characters")
    private String actionText;
}
