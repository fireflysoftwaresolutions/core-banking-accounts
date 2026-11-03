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


package io.fireflysoftware.core.banking.accounts.core.services.product.impl;

import io.fireflysoftware.core.banking.accounts.core.operations.DepositAccountRules;
import io.fireflysoftware.core.banking.accounts.core.services.product.DepositProductService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.AccountLimitDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.AccountStatementDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.AccountSweepDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.SavingsInterestDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.TermDepositDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.product.AccountLimit;
import io.fireflysoftware.core.banking.accounts.models.entities.product.AccountStatement;
import io.fireflysoftware.core.banking.accounts.models.entities.product.AccountSweep;
import io.fireflysoftware.core.banking.accounts.models.entities.product.SavingsInterest;
import io.fireflysoftware.core.banking.accounts.models.entities.product.TermDeposit;
import io.fireflysoftware.core.banking.accounts.models.repositories.product.AccountLimitRepository;
import io.fireflysoftware.core.banking.accounts.models.repositories.product.AccountStatementRepository;
import io.fireflysoftware.core.banking.accounts.models.repositories.product.AccountSweepRepository;
import io.fireflysoftware.core.banking.accounts.models.repositories.product.SavingsInterestRepository;
import io.fireflysoftware.core.banking.accounts.models.repositories.product.TermDepositRepository;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service("depositProductService")
public class DepositProductServiceImpl implements DepositProductService {

    private final AccountStatementRepository statements;
    private final AccountLimitRepository limits;
    private final SavingsInterestRepository interest;
    private final TermDepositRepository termDeposits;
    private final AccountSweepRepository sweeps;

    public DepositProductServiceImpl(AccountStatementRepository statements, AccountLimitRepository limits,
                                 SavingsInterestRepository interest, TermDepositRepository termDeposits,
                                 AccountSweepRepository sweeps) {
        this.statements = statements;
        this.limits = limits;
        this.interest = interest;
        this.termDeposits = termDeposits;
        this.sweeps = sweeps;
    }

    public Mono<AccountStatementDTO> recordStatement(UUID accountId, AccountStatementDTO request) {
        if (accountId == null || request == null) {
            return Mono.error(new IllegalArgumentException("Account and statement are required"));
        }
        try {
            requireCurrency(request.currency());
            List<DepositAccountRules.StatementLine> lines = request.lineAmounts() == null
                    ? List.of()
                    : request.lineAmounts().stream().map(DepositAccountRules.StatementLine::new).toList();
            DepositAccountRules.Statement calculated = DepositAccountRules.statement(request.openingBalance(), lines);
            AccountStatement row = new AccountStatement();
            row.setAccountStatementId(UUID.randomUUID());
            row.setAccountId(accountId);
            row.setCurrency(request.currency());
            row.setOpeningBalance(calculated.opening());
            row.setCredits(calculated.credits());
            row.setDebits(calculated.debits());
            row.setClosingBalance(calculated.closing());
            return statements.save(row).map(saved -> new AccountStatementDTO(saved.getAccountStatementId(), accountId,
                    saved.getCurrency(), saved.getOpeningBalance(), request.lineAmounts(), saved.getCredits(),
                    saved.getDebits(), saved.getClosingBalance()));
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
    }

    public Mono<AccountStatementDTO> getStatement(UUID accountId, UUID statementId) {
        return statements.findById(statementId)
                .filter(row -> accountId.equals(row.getAccountId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Account statement was not found")))
                .map(row -> new AccountStatementDTO(row.getAccountStatementId(), row.getAccountId(), row.getCurrency(),
                        row.getOpeningBalance(), List.of(), row.getCredits(), row.getDebits(), row.getClosingBalance()));
    }

    public Mono<AccountLimitDTO> recordLimit(UUID accountId, AccountLimitDTO request) {
        if (accountId == null || request == null) {
            return Mono.error(new IllegalArgumentException("Account and limit are required"));
        }
        try {
            requireCurrency(request.currency());
            DepositAccountRules.checkWithdrawal(request.withdrawalAmount(), request.balance(),
                    request.withdrawalLimit(), request.overdraftLimit());
            AccountLimit row = new AccountLimit();
            row.setAccountLimitId(UUID.randomUUID());
            row.setAccountId(accountId);
            row.setCurrency(request.currency());
            row.setWithdrawalLimit(request.withdrawalLimit());
            row.setOverdraftLimit(request.overdraftLimit());
            row.setBalance(request.balance());
            row.setWithdrawalAmount(request.withdrawalAmount());
            row.setAllowed(true);
            return limits.save(row).map(saved -> new AccountLimitDTO(saved.getAccountLimitId(), accountId,
                    saved.getCurrency(), saved.getWithdrawalLimit(), saved.getOverdraftLimit(), saved.getBalance(),
                    saved.getWithdrawalAmount(), saved.getAllowed()));
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
    }

    public Mono<AccountLimitDTO> getLimit(UUID accountId, UUID limitId) {
        return limits.findById(limitId)
                .filter(row -> accountId.equals(row.getAccountId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Account limit was not found")))
                .map(row -> new AccountLimitDTO(row.getAccountLimitId(), row.getAccountId(), row.getCurrency(),
                        row.getWithdrawalLimit(), row.getOverdraftLimit(), row.getBalance(),
                        row.getWithdrawalAmount(), row.getAllowed()));
    }

    public Mono<SavingsInterestDTO> recordInterest(UUID accountId, SavingsInterestDTO request) {
        if (accountId == null || request == null || request.dayCount() == null) {
            return Mono.error(new IllegalArgumentException("Account, balance, rate, and day count are required"));
        }
        try {
            requireCurrency(request.currency());
            BigDecimal amount = DepositAccountRules.savingsInterest(request.balance(), request.annualRatePercent(),
                    request.dayCount());
            SavingsInterest row = new SavingsInterest();
            row.setSavingsInterestId(UUID.randomUUID());
            row.setAccountId(accountId);
            row.setCurrency(request.currency());
            row.setBalance(request.balance());
            row.setAnnualRatePercent(request.annualRatePercent());
            row.setDayCount(request.dayCount());
            row.setInterestAmount(amount);
            return interest.save(row).map(saved -> new SavingsInterestDTO(saved.getSavingsInterestId(), accountId,
                    saved.getCurrency(), saved.getBalance(), saved.getAnnualRatePercent(), saved.getDayCount(),
                    saved.getInterestAmount()));
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
    }

    public Mono<SavingsInterestDTO> getInterest(UUID accountId, UUID interestId) {
        return interest.findById(interestId)
                .filter(row -> accountId.equals(row.getAccountId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Savings interest was not found")))
                .map(row -> new SavingsInterestDTO(row.getSavingsInterestId(), row.getAccountId(), row.getCurrency(),
                        row.getBalance(), row.getAnnualRatePercent(), row.getDayCount(), row.getInterestAmount()));
    }

    public Mono<TermDepositDTO> recordTermDeposit(UUID accountId, TermDepositDTO request) {
        if (accountId == null || request == null) {
            return Mono.error(new IllegalArgumentException("Account and term deposit are required"));
        }
        try {
            requireCurrency(request.currency());
            BigDecimal payout = DepositAccountRules.termDepositPayout(request.principal(), request.annualRatePercent(),
                    request.openDate(), request.maturityDate(), request.withdrawalDate(), request.penaltyRatePercent());
            TermDeposit row = new TermDeposit();
            row.setTermDepositId(UUID.randomUUID());
            row.setAccountId(accountId);
            row.setCurrency(request.currency());
            row.setPrincipal(request.principal());
            row.setAnnualRatePercent(request.annualRatePercent());
            row.setOpenDate(request.openDate());
            row.setMaturityDate(request.maturityDate());
            row.setWithdrawalDate(request.withdrawalDate());
            row.setPenaltyRatePercent(request.penaltyRatePercent() == null ? BigDecimal.ZERO : request.penaltyRatePercent());
            row.setPayoutAmount(payout);
            return termDeposits.save(row).map(saved -> new TermDepositDTO(saved.getTermDepositId(), accountId,
                    saved.getCurrency(), saved.getPrincipal(), saved.getAnnualRatePercent(), saved.getOpenDate(),
                    saved.getMaturityDate(), saved.getWithdrawalDate(), saved.getPenaltyRatePercent(),
                    saved.getPayoutAmount()));
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
    }

    public Mono<TermDepositDTO> getTermDeposit(UUID accountId, UUID termDepositId) {
        return termDeposits.findById(termDepositId)
                .filter(row -> accountId.equals(row.getAccountId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Term deposit was not found")))
                .map(row -> new TermDepositDTO(row.getTermDepositId(), row.getAccountId(), row.getCurrency(),
                        row.getPrincipal(), row.getAnnualRatePercent(), row.getOpenDate(), row.getMaturityDate(),
                        row.getWithdrawalDate(), row.getPenaltyRatePercent(), row.getPayoutAmount()));
    }

    public Mono<AccountSweepDTO> recordSweep(UUID accountId, AccountSweepDTO request) {
        if (accountId == null || request == null || request.sourceSpaceId() == null || request.targetSpaceId() == null) {
            return Mono.error(new IllegalArgumentException("Account and both spaces are required"));
        }
        if (request.sourceSpaceId().equals(request.targetSpaceId())) {
            return Mono.error(new IllegalArgumentException("A sweep needs two different spaces"));
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            return Mono.error(new IllegalArgumentException("Sweep amount must be positive"));
        }
        try {
            requireCurrency(request.currency());
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
        AccountSweep row = new AccountSweep();
        row.setAccountSweepId(UUID.randomUUID());
        row.setAccountId(accountId);
        row.setSourceSpaceId(request.sourceSpaceId());
        row.setTargetSpaceId(request.targetSpaceId());
        row.setAmount(request.amount());
        row.setCurrency(request.currency());
        return sweeps.save(row).map(saved -> new AccountSweepDTO(saved.getAccountSweepId(), accountId,
                saved.getSourceSpaceId(), saved.getTargetSpaceId(), saved.getAmount(), saved.getCurrency()));
    }

    public Mono<AccountSweepDTO> getSweep(UUID accountId, UUID sweepId) {
        return sweeps.findById(sweepId)
                .filter(row -> accountId.equals(row.getAccountId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Account sweep was not found")))
                .map(row -> new AccountSweepDTO(row.getAccountSweepId(), row.getAccountId(), row.getSourceSpaceId(),
                        row.getTargetSpaceId(), row.getAmount(), row.getCurrency()));
    }

    private static void requireCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a 3-letter code");
        }
    }
}
