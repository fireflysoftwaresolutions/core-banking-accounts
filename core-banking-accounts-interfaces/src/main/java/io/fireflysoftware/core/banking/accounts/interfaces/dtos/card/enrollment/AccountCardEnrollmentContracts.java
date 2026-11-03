/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** Accounts admission receipts; none of these states asserts that a card was issued. */
public final class AccountCardEnrollmentContracts {
    private AccountCardEnrollmentContracts() {}

    @Schema(name = "AccountCardEnrollmentState")
    public enum State { HELD, CLAIMED, CANCELLED }
    @Schema(name = "AccountCardEnrollmentOperation")
    public enum Operation { ACQUIRE, CLAIM, CANCEL }

    @Schema(name = "AccountCardEnrollmentAcquire")
    public record Acquire(
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) UUID actionId,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) long expectedAccountVersion,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) long acceptNotAfterEpochMillis,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) String reasonCode) {}

    @Schema(name = "AccountCardEnrollmentClaim")
    public record Claim(
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) UUID claimActionId,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) UUID receiptId,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) long expectedFenceVersion) {}

    @Schema(name = "AccountCardEnrollmentCancel")
    public record Cancel(
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) UUID cancellationActionId,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) UUID receiptId,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) long expectedFenceVersion,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) String reasonCode) {}

    @Schema(name = "AccountCardEnrollmentFence")
    public record Fence(UUID actionId, UUID receiptId, UUID tenantId, UUID actorId,
            UUID partyId, UUID accountId, UUID contractId, UUID mandateId,
            long accountVersion, String currency, String reasonCode,
            long acceptNotAfterEpochMillis, @Schema(ref = "#/components/schemas/FireflyInstant") Instant recordedAt, State state,
            long version, UUID claimActionId, UUID cancellationActionId) {}

    @Schema(name = "AccountCardEnrollmentActionReceipt")
    public record ActionReceipt(UUID operationActionId, UUID operationReceiptId,
            Operation operation, UUID fenceActionId, UUID fenceReceiptId,
            State resultingState, long resultingVersion, @Schema(ref = "#/components/schemas/FireflyInstant") Instant recordedAt) {}

    /** Retained action receipt and present owner observation have deliberately separate lifetimes. */
    @Schema(name = "AccountCardEnrollmentObservation")
    public record Observation(Fence fence, ActionReceipt actionReceipt,
            long currentAccountVersion, boolean currentlyEligible,
            @Schema(ref = "#/components/schemas/FireflyInstant") Instant observedAt,
            @Schema(ref = "#/components/schemas/FireflyInstant") Instant eligibilityValidUntil) {}
}
