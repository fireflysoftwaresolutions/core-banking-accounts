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


package io.fireflysoftware.core.banking.accounts.core.services.core.impl;

import io.fireflysoftware.core.banking.accounts.core.mappers.core.AccountMapper;
import io.fireflysoftware.core.banking.accounts.core.services.space.AccountSpaceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.AccountSpaceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountStatusEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountTypeEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.core.Account;
import io.fireflysoftware.core.banking.accounts.models.repositories.core.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountSpaceService accountSpaceService;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Account testAccount;
    private AccountDTO testAccountDTO;
    private final UUID TEST_ACCOUNT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440015");

    @BeforeEach
    void setUp() {
        // Setup test account entity
        testAccount = new Account();
        testAccount.setAccountId(TEST_ACCOUNT_ID);
        testAccount.setContractId(UUID.fromString("550e8400-e29b-41d4-a716-446655440016"));
        testAccount.setAccountNumber("TEST-ACCOUNT-001");
        testAccount.setAccountType(AccountTypeEnum.CHECKING);
        testAccount.setCurrency("USD");
        testAccount.setOpenDate(LocalDate.now());
        testAccount.setAccountStatus(AccountStatusEnum.OPEN);
        testAccount.setBranchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440017"));
        testAccount.setDescription("Test Account");

        // Setup test account DTO
        testAccountDTO = AccountDTO.builder()
                .accountId(TEST_ACCOUNT_ID)
                .contractId(UUID.fromString("550e8400-e29b-41d4-a716-446655440016"))
                .accountNumber("TEST-ACCOUNT-001")
                .accountType(AccountTypeEnum.CHECKING)
                .currency("USD")
                .openDate(LocalDate.now())
                .accountStatus(AccountStatusEnum.OPEN)
                .branchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440017"))
                .description("Test Account")
                .dateCreated(LocalDateTime.now())
                .dateUpdated(LocalDateTime.now())
                .build();
    }

    @Test
    void createAccount_ShouldCreateMainSpaceAndReturnCreatedAccount() {
        // Arrange
        when(accountMapper.toEntity(any(AccountDTO.class))).thenReturn(testAccount);
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(testAccount));
        when(accountMapper.toDTO(any(Account.class))).thenReturn(testAccountDTO);
        when(accountSpaceService.createAccountSpace(any(AccountSpaceDTO.class))).thenReturn(Mono.just(
                AccountSpaceDTO.builder()
                        .accountSpaceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440020"))
                        .accountId(TEST_ACCOUNT_ID)
                        .spaceName("Main Account")
                        .spaceType(AccountSpaceTypeEnum.MAIN)
                        .build()
        ));

        // Act & Assert
        StepVerifier.create(accountService.createAccount(testAccountDTO))
                .expectNext(testAccountDTO)
                .verifyComplete();

        verify(accountMapper).toEntity(testAccountDTO);
        verify(accountRepository).save(testAccount);

        // Verify that createAccountSpace was called with a DTO containing the correct values
        verify(accountSpaceService).createAccountSpace(argThat(spaceDTO ->
                spaceDTO.getAccountId().equals(TEST_ACCOUNT_ID) &&
                spaceDTO.getSpaceType() == AccountSpaceTypeEnum.MAIN &&
                "Main Account".equals(spaceDTO.getSpaceName())
        ));

        verify(accountMapper).toDTO(testAccount);
    }

    @Test
    void getAccount_ShouldReturnAccount_WhenAccountExists() {
        // Arrange
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Mono.just(testAccount));
        when(accountMapper.toDTO(testAccount)).thenReturn(testAccountDTO);

        // Act & Assert
        StepVerifier.create(accountService.getAccount(TEST_ACCOUNT_ID))
                .expectNext(testAccountDTO)
                .verifyComplete();

        verify(accountRepository).findById(TEST_ACCOUNT_ID);
        verify(accountMapper).toDTO(testAccount);
    }

    @Test
    void getAccount_ShouldReturnEmptyMono_WhenAccountDoesNotExist() {
        // Arrange
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountService.getAccount(TEST_ACCOUNT_ID))
                .verifyComplete();

        verify(accountRepository).findById(TEST_ACCOUNT_ID);
        verifyNoMoreInteractions(accountMapper);
    }

    @Test
    void updateAccount_ShouldReturnUpdatedAccount_WhenAccountExists() {
        // Arrange
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Mono.just(testAccount));
       // when(accountMapper.updateEntityFromDto(testAccountDTO, testAccount)).thenReturn(testAccount);
        when(accountRepository.save(testAccount)).thenReturn(Mono.just(testAccount));
        when(accountMapper.toDTO(testAccount)).thenReturn(testAccountDTO);

        // Act & Assert
        StepVerifier.create(accountService.updateAccount(TEST_ACCOUNT_ID, testAccountDTO))
                .expectNext(testAccountDTO)
                .verifyComplete();

        verify(accountRepository).findById(TEST_ACCOUNT_ID);
        //verify(accountMapper).toEntity(testAccountDTO);
        verify(accountRepository).save(testAccount);
        verify(accountMapper).toDTO(testAccount);
    }

    @Test
    void updateAccount_ShouldReturnEmptyMono_WhenAccountDoesNotExist() {
        // Arrange
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountService.updateAccount(TEST_ACCOUNT_ID, testAccountDTO))
                .verifyComplete();

        verify(accountRepository).findById(TEST_ACCOUNT_ID);
        verifyNoMoreInteractions(accountMapper, accountRepository);
    }

    @Test
    void deleteAccount_ShouldDeleteAccount_WhenAccountExists() {
        // Arrange
        testAccount.setAccountStatus(AccountStatusEnum.DRAFT);
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Mono.just(testAccount));
        when(accountRepository.delete(testAccount)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountService.deleteAccount(TEST_ACCOUNT_ID))
                .verifyComplete();

        verify(accountRepository).findById(TEST_ACCOUNT_ID);
        verify(accountRepository).delete(testAccount);
    }

    @Test
    void deleteAccount_ShouldReject_WhenAccountIsOpen() {
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Mono.just(testAccount));

        StepVerifier.create(accountService.deleteAccount(TEST_ACCOUNT_ID))
                .expectError(IllegalStateException.class)
                .verify();

        verify(accountRepository, never()).delete(any(Account.class));
    }

    @Test
    void deleteAccount_ShouldReturnEmptyMono_WhenAccountDoesNotExist() {
        // Arrange
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountService.deleteAccount(TEST_ACCOUNT_ID))
                .verifyComplete();

        verify(accountRepository).findById(TEST_ACCOUNT_ID);
        verifyNoMoreInteractions(accountRepository);
    }
    
    @Test
    void createAccount_ShouldCreateCryptoWalletAccount() {
        // Arrange
        AccountDTO cryptoAccountDTO = AccountDTO.builder()
                .contractId(UUID.fromString("550e8400-e29b-41d4-a716-446655440016"))
                .accountNumber("CRYPTO-001")
                .accountType(AccountTypeEnum.CRYPTO_WALLET)
                .currency("USD")
                .openDate(LocalDate.now())
                .accountStatus(AccountStatusEnum.OPEN)
                .branchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440017"))
                .description("Bitcoin Wallet")
                .walletAddress("0x742d35Cc6634C0532925a3b844Bc454e4438f44e")
                .blockchainNetwork("Ethereum")
                .isCustodial(true)
                .build();
                
        Account cryptoAccount = new Account();
        cryptoAccount.setAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440018"));
        cryptoAccount.setContractId(UUID.fromString("550e8400-e29b-41d4-a716-446655440016"));
        cryptoAccount.setAccountNumber("CRYPTO-001");
        cryptoAccount.setAccountType(AccountTypeEnum.CRYPTO_WALLET);
        cryptoAccount.setCurrency("USD");
        cryptoAccount.setOpenDate(LocalDate.now());
        cryptoAccount.setAccountStatus(AccountStatusEnum.OPEN);
        cryptoAccount.setBranchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440017"));
        cryptoAccount.setDescription("Bitcoin Wallet");
        cryptoAccount.setWalletAddress("0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
        cryptoAccount.setBlockchainNetwork("Ethereum");
        cryptoAccount.setIsCustodial(true);
        
        when(accountMapper.toEntity(any(AccountDTO.class))).thenReturn(cryptoAccount);
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(cryptoAccount));
        when(accountMapper.toDTO(any(Account.class))).thenReturn(cryptoAccountDTO);
        when(accountSpaceService.createAccountSpace(any(AccountSpaceDTO.class))).thenReturn(Mono.just(
                AccountSpaceDTO.builder()
                        .accountSpaceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440024"))
                        .accountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440018"))
                        .spaceName("Main Account")
                        .spaceType(AccountSpaceTypeEnum.MAIN)
                        .build()
        ));

        // Act & Assert
        StepVerifier.create(accountService.createAccount(cryptoAccountDTO))
                .expectNext(cryptoAccountDTO)
                .verifyComplete();

        verify(accountMapper).toEntity(cryptoAccountDTO);
        verify(accountRepository).save(cryptoAccount);
        
        // Verify that createAccountSpace was called with a DTO containing the correct values
        verify(accountSpaceService).createAccountSpace(argThat(spaceDTO ->
                spaceDTO.getAccountId().equals(UUID.fromString("550e8400-e29b-41d4-a716-446655440018")) &&
                spaceDTO.getSpaceType() == AccountSpaceTypeEnum.MAIN &&
                "Main Account".equals(spaceDTO.getSpaceName())
        ));
        
        verify(accountMapper).toDTO(cryptoAccount);
    }
    
    @Test
    void createAccount_ShouldCreateTokenizedAssetAccount() {
        // Arrange
        AccountDTO tokenizedAssetDTO = AccountDTO.builder()
                .contractId(UUID.fromString("550e8400-e29b-41d4-a716-446655440016"))
                .accountNumber("TOKEN-001")
                .accountType(AccountTypeEnum.TOKENIZED_ASSET)
                .currency("USD")
                .openDate(LocalDate.now())
                .accountStatus(AccountStatusEnum.OPEN)
                .branchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440017"))
                .description("USDT Token")
                .walletAddress("0x742d35Cc6634C0532925a3b844Bc454e4438f44e")
                .blockchainNetwork("Ethereum")
                .tokenContractAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7")
                .tokenStandard("ERC-20")
                .isCustodial(true)
                .build();
                
        Account tokenizedAsset = new Account();
        tokenizedAsset.setAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440019"));
        tokenizedAsset.setContractId(UUID.fromString("550e8400-e29b-41d4-a716-446655440016"));
        tokenizedAsset.setAccountNumber("TOKEN-001");
        tokenizedAsset.setAccountType(AccountTypeEnum.TOKENIZED_ASSET);
        tokenizedAsset.setCurrency("USD");
        tokenizedAsset.setOpenDate(LocalDate.now());
        tokenizedAsset.setAccountStatus(AccountStatusEnum.OPEN);
        tokenizedAsset.setBranchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440017"));
        tokenizedAsset.setDescription("USDT Token");
        tokenizedAsset.setWalletAddress("0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
        tokenizedAsset.setBlockchainNetwork("Ethereum");
        tokenizedAsset.setTokenContractAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7");
        tokenizedAsset.setTokenStandard("ERC-20");
        tokenizedAsset.setIsCustodial(true);
        
        when(accountMapper.toEntity(any(AccountDTO.class))).thenReturn(tokenizedAsset);
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(tokenizedAsset));
        when(accountMapper.toDTO(any(Account.class))).thenReturn(tokenizedAssetDTO);
        when(accountSpaceService.createAccountSpace(any(AccountSpaceDTO.class))).thenReturn(Mono.just(
                AccountSpaceDTO.builder()
                        .accountSpaceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440025"))
                        .accountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440019"))
                        .spaceName("Main Account")
                        .spaceType(AccountSpaceTypeEnum.MAIN)
                        .build()
        ));

        // Act & Assert
        StepVerifier.create(accountService.createAccount(tokenizedAssetDTO))
                .expectNext(tokenizedAssetDTO)
                .verifyComplete();

        verify(accountMapper).toEntity(tokenizedAssetDTO);
        verify(accountRepository).save(tokenizedAsset);
        
        // Verify that createAccountSpace was called with a DTO containing the correct values
        verify(accountSpaceService).createAccountSpace(argThat(spaceDTO ->
                spaceDTO.getAccountId().equals(UUID.fromString("550e8400-e29b-41d4-a716-446655440019")) &&
                spaceDTO.getSpaceType() == AccountSpaceTypeEnum.MAIN &&
                "Main Account".equals(spaceDTO.getSpaceName())
        ));
        
        verify(accountMapper).toDTO(tokenizedAsset);
    }
}
