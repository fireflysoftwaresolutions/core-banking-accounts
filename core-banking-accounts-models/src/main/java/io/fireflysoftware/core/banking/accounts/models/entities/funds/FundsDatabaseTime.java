package io.fireflysoftware.core.banking.accounts.models.entities.funds;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FundsDatabaseTime(LocalDate businessDate, LocalDateTime businessTime) {}
