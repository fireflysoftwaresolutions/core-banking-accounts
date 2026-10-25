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


package io.fireflysoftware.core.banking.accounts.models.entities.product;

import io.fireflysoftware.core.banking.accounts.models.entities.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Table("term_deposit")
public class TermDeposit extends BaseEntity implements Persistable<UUID> {

    @Id
    @Column("term_deposit_id")
    private UUID termDepositId;

    @Column("account_id")
    private UUID accountId;

    @Column("currency")
    private String currency;

    @Column("principal")
    private BigDecimal principal;

    @Column("annual_rate_percent")
    private BigDecimal annualRatePercent;

    @Column("open_date")
    private LocalDate openDate;

    @Column("maturity_date")
    private LocalDate maturityDate;

    @Column("withdrawal_date")
    private LocalDate withdrawalDate;

    @Column("penalty_rate_percent")
    private BigDecimal penaltyRatePercent;

    @Column("payout_amount")
    private BigDecimal payoutAmount;

    @Transient
    private boolean newRow = true;

    @Override
    public UUID getId() {
        return termDepositId;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }
}
