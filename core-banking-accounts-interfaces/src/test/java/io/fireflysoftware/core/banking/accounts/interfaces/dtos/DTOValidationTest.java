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


package io.fireflysoftware.core.banking.accounts.interfaces.dtos;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountBalanceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.AccountSpaceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.notification.AccountNotificationDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.parameter.AccountParameterDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.restriction.AccountRestrictionDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.provider.AccountProviderDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.status.AccountStatusHistoryDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountStatusEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.BalanceTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.notification.NotificationTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.parameter.ParamTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.restriction.RestrictionTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.provider.ProviderStatusEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.status.StatusCodeEnum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DTO Validation Tests")
public class DTOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("AccountDTO - Valid object should pass validation")
    void testAccountDTO_ValidObject() {
        AccountDTO accountDTO = AccountDTO.builder()
                .contractId(UUID.randomUUID())
                .accountNumber("ACC123456")
                .accountType(AccountTypeEnum.CHECKING)
                .currency("USD")
                .openDate(LocalDate.now().minusDays(1))
                .accountStatus(AccountStatusEnum.OPEN)
                .branchId(UUID.randomUUID())
                .description("Test account")
                .minimumBalance(new BigDecimal("100.00"))
                .overdraftLimit(new BigDecimal("500.00"))
                .build();

        Set<ConstraintViolation<AccountDTO>> violations = validator.validate(accountDTO);
        assertTrue(violations.isEmpty(), "Valid AccountDTO should not have validation errors");
    }

    @Test
    @DisplayName("AccountDTO - Invalid fields should fail validation")
    void testAccountDTO_InvalidFields() {
        AccountDTO accountDTO = AccountDTO.builder()
                .contractId(null) // Required field
                .accountNumber("") // Blank not allowed
                .accountType(null) // Required field
                .currency("US") // Must be 3 characters
                .openDate(LocalDate.now().plusDays(1)) // Cannot be in future
                .accountStatus(null) // Required field
                .branchId(null) // Required field
                .description("A".repeat(501)) // Exceeds max length
                .minimumBalance(new BigDecimal("-100.00")) // Cannot be negative
                .build();

        Set<ConstraintViolation<AccountDTO>> violations = validator.validate(accountDTO);
        assertFalse(violations.isEmpty(), "Invalid AccountDTO should have validation errors");
        assertTrue(violations.size() >= 8, "Should have multiple validation errors");
    }

    @Test
    @DisplayName("AccountBalanceDTO - Valid object should pass validation")
    void testAccountBalanceDTO_ValidObject() {
        AccountBalanceDTO balanceDTO = AccountBalanceDTO.builder()
                .accountId(UUID.randomUUID())
                .balanceType(BalanceTypeEnum.CURRENT)
                .balanceAmount(new BigDecimal("1000.50"))
                .asOfDatetime(LocalDateTime.now().minusHours(1))
                .assetSymbol("BTC")
                .assetDecimals("8")
                .transactionHash("0x123abc")
                .confirmations(6)
                .build();

        Set<ConstraintViolation<AccountBalanceDTO>> violations = validator.validate(balanceDTO);
        assertTrue(violations.isEmpty(), "Valid AccountBalanceDTO should not have validation errors");
    }

    @Test
    @DisplayName("AccountSpaceDTO - Invalid fields should fail validation")
    void testAccountSpaceDTO_InvalidFields() {
        AccountSpaceDTO spaceDTO = AccountSpaceDTO.builder()
                .accountId(null) // Required field
                .spaceName("") // Blank not allowed
                .spaceType(null) // Required field
                .balance(null) // Required field
                .targetAmount(new BigDecimal("-100.00")) // Must be positive
                .targetDate(LocalDateTime.now().minusDays(1)) // Must be in future
                .colorCode("invalid") // Invalid hex color
                .description("A".repeat(501)) // Exceeds max length
                .build();

        Set<ConstraintViolation<AccountSpaceDTO>> violations = validator.validate(spaceDTO);
        assertFalse(violations.isEmpty(), "Invalid AccountSpaceDTO should have validation errors");
    }

    @Test
    @DisplayName("AssetPriceDTO - Valid object should pass validation")
    void testAssetPriceDTO_ValidObject() {
        AssetPriceDTO assetPriceDTO = AssetPriceDTO.builder()
                .assetSymbol("BTC")
                .quoteCurrency("USD")
                .price(new BigDecimal("45000.12345678"))
                .asOfDatetime(LocalDateTime.now().minusMinutes(5))
                .priceSource("Coinbase")
                .build();

        Set<ConstraintViolation<AssetPriceDTO>> violations = validator.validate(assetPriceDTO);
        assertTrue(violations.isEmpty(), "Valid AssetPriceDTO should not have validation errors");
    }

    @Test
    @DisplayName("AccountNotificationDTO - Invalid priority should fail validation")
    void testAccountNotificationDTO_InvalidPriority() {
        AccountNotificationDTO notificationDTO = AccountNotificationDTO.builder()
                .accountId(UUID.randomUUID())
                .notificationType(NotificationTypeEnum.LOW_BALANCE)
                .title("Test Notification")
                .message("Test message")
                .creationDateTime(LocalDateTime.now())
                .priority(15) // Exceeds max value of 10
                .build();

        Set<ConstraintViolation<AccountNotificationDTO>> violations = validator.validate(notificationDTO);
        assertFalse(violations.isEmpty(), "Invalid priority should cause validation error");
    }

    @Test
    @DisplayName("AccountParameterDTO - Valid object should pass validation")
    void testAccountParameterDTO_ValidObject() {
        AccountParameterDTO parameterDTO = AccountParameterDTO.builder()
                .accountId(UUID.randomUUID())
                .paramType(ParamTypeEnum.MONTHLY_FEE)
                .paramValue(new BigDecimal("25.00"))
                .paramUnit("USD")
                .effectiveDate(LocalDateTime.now())
                .description("Monthly maintenance fee")
                .build();

        Set<ConstraintViolation<AccountParameterDTO>> violations = validator.validate(parameterDTO);
        assertTrue(violations.isEmpty(), "Valid AccountParameterDTO should not have validation errors");
    }

    @Test
    @DisplayName("AccountRestrictionDTO - Required fields should be validated")
    void testAccountRestrictionDTO_RequiredFields() {
        AccountRestrictionDTO restrictionDTO = AccountRestrictionDTO.builder()
                .accountId(null) // Required field
                .restrictionType(null) // Required field
                .startDateTime(null) // Required field
                .reason("") // Required field (blank not allowed)
                .appliedBy("") // Required field (blank not allowed)
                .build();

        Set<ConstraintViolation<AccountRestrictionDTO>> violations = validator.validate(restrictionDTO);
        assertFalse(violations.isEmpty(), "Missing required fields should cause validation errors");
        assertTrue(violations.size() >= 5, "Should have multiple validation errors for required fields");
    }

    @Test
    @DisplayName("AccountProviderDTO - Valid object should pass validation")
    void testAccountProviderDTO_ValidObject() {
        AccountProviderDTO providerDTO = AccountProviderDTO.builder()
                .accountId(UUID.randomUUID())
                .providerName("Test Provider")
                .externalReference("EXT123")
                .status(ProviderStatusEnum.ACTIVE)
                .accountSpaceId(UUID.randomUUID())
                .build();

        Set<ConstraintViolation<AccountProviderDTO>> violations = validator.validate(providerDTO);
        assertTrue(violations.isEmpty(), "Valid AccountProviderDTO should not have validation errors");
    }

    @Test
    @DisplayName("AccountStatusHistoryDTO - Valid object should pass validation")
    void testAccountStatusHistoryDTO_ValidObject() {
        AccountStatusHistoryDTO statusHistoryDTO = AccountStatusHistoryDTO.builder()
                .accountId(UUID.randomUUID())
                .statusCode(StatusCodeEnum.OPEN)
                .statusStartDatetime(LocalDateTime.now().minusDays(1))
                .statusEndDatetime(LocalDateTime.now())
                .reason("Account opened")
                .build();

        Set<ConstraintViolation<AccountStatusHistoryDTO>> violations = validator.validate(statusHistoryDTO);
        assertTrue(violations.isEmpty(), "Valid AccountStatusHistoryDTO should not have validation errors");
    }

    @Test
    @DisplayName("Currency validation - Invalid currency codes should fail")
    void testCurrencyValidation() {
        AccountDTO accountDTO = AccountDTO.builder()
                .contractId(UUID.randomUUID())
                .accountNumber("ACC123456")
                .accountType(AccountTypeEnum.CHECKING)
                .currency("usd") // Should be uppercase
                .openDate(LocalDate.now().minusDays(1))
                .accountStatus(AccountStatusEnum.OPEN)
                .branchId(UUID.randomUUID())
                .build();

        Set<ConstraintViolation<AccountDTO>> violations = validator.validate(accountDTO);
        assertFalse(violations.isEmpty(), "Lowercase currency should fail validation");
    }

    @Test
    @DisplayName("BigDecimal precision validation")
    void testBigDecimalPrecisionValidation() {
        AccountDTO accountDTO = AccountDTO.builder()
                .contractId(UUID.randomUUID())
                .accountNumber("ACC123456")
                .accountType(AccountTypeEnum.CHECKING)
                .currency("USD")
                .openDate(LocalDate.now().minusDays(1))
                .accountStatus(AccountStatusEnum.OPEN)
                .branchId(UUID.randomUUID())
                .minimumBalance(new BigDecimal("123456789012345678.123")) // Too many decimal places
                .build();

        Set<ConstraintViolation<AccountDTO>> violations = validator.validate(accountDTO);
        assertFalse(violations.isEmpty(), "Invalid decimal precision should fail validation");
    }
}
