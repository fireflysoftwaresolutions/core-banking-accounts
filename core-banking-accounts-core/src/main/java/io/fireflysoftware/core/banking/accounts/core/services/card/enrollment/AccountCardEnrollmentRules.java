/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment.AccountCardEnrollmentContracts.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Pure comparisons of actual stored owner rows. Authentication and database locking are external. */
public final class AccountCardEnrollmentRules {
    private AccountCardEnrollmentRules() {}

    public record Owner(String tenantId, UUID accountId, UUID contractId, String currency,
            String kind, String mode, String control, long accountVersion, UUID pendingControl,
            String accountStatus, LocalDate closeDate, UUID mandateId, UUID partyId,
            boolean mandateActive, String mandateRole) {}

    public static void validate(Acquire request) {
        if (request == null || !id(request.actionId()) || request.expectedAccountVersion() < 0
                || request.acceptNotAfterEpochMillis() <= 0 || !reason(request.reasonCode()))
            throw new IllegalArgumentException("EXPLICIT_CARD_ENROLLMENT_INTENT_REQUIRED");
    }

    public static void admit(Owner owner, UUID tenant, UUID party, UUID account,
            Acquire request, Instant databaseTime) {
        validate(request);
        if (!activeOwner(owner, tenant, party, account)) throw new Refused();
        if (owner.accountVersion() != request.expectedAccountVersion()
                || request.acceptNotAfterEpochMillis() <= databaseTime.toEpochMilli()) throw new Conflict();
    }

    public static Fence claim(Fence fence, Claim request, Owner owner, Instant databaseTime) {
        if (request == null || !id(request.claimActionId())) throw new IllegalArgumentException("EXPLICIT_CLAIM_ACTION_REQUIRED");
        transition(fence, request.receiptId(), request.expectedFenceVersion());
        if (!eligible(fence, owner, databaseTime)) throw new Refused();
        return transitionTo(fence, State.CLAIMED, request.claimActionId(), null);
    }

    public static Fence cancel(Fence fence, Cancel request) {
        if (request == null || !id(request.cancellationActionId()) || !reason(request.reasonCode()))
            throw new IllegalArgumentException("EXPLICIT_CANCELLATION_ACTION_REQUIRED");
        transition(fence, request.receiptId(), request.expectedFenceVersion());
        return transitionTo(fence, State.CANCELLED, null, request.cancellationActionId());
    }

    public static boolean eligible(Fence fence, Owner owner, Instant databaseTime) {
        return pending(fence) && activeOwner(owner, fence.tenantId(), fence.partyId(), fence.accountId())
                && fence.contractId().equals(owner.contractId()) && fence.mandateId().equals(owner.mandateId())
                && fence.currency().equals(owner.currency()) && fence.accountVersion() == owner.accountVersion()
                && databaseTime.toEpochMilli() < fence.acceptNotAfterEpochMillis();
    }

    public static boolean pending(Fence fence) { return fence.state() != State.CANCELLED; }

    public static boolean controlAllowed(State state, String target) {
        return state == State.CANCELLED || "BLOCKED".equals(target);
    }

    public static void requireAcquireReplay(Fence fence, Acquire request,
            UUID tenant, UUID actor, UUID party, UUID account) {
        validate(request);
        if (!Objects.equals(fence.actionId(), request.actionId()) || !Objects.equals(fence.tenantId(), tenant)
                || !Objects.equals(fence.actorId(), actor) || !Objects.equals(fence.partyId(), party)
                || !Objects.equals(fence.accountId(), account) || fence.accountVersion() != request.expectedAccountVersion()
                || fence.acceptNotAfterEpochMillis() != request.acceptNotAfterEpochMillis()
                || !Objects.equals(fence.reasonCode(), request.reasonCode())) throw new Conflict();
    }

    private static boolean activeOwner(Owner owner, UUID tenant, UUID party, UUID account) {
        return owner != null && id(tenant) && id(party) && id(account)
                && tenant.toString().equals(owner.tenantId()) && account.equals(owner.accountId())
                && party.equals(owner.partyId()) && id(owner.contractId()) && id(owner.mandateId())
                && "CUSTOMER".equals(owner.kind()) && "MANAGED".equals(owner.mode())
                && "ACTIVE".equals(owner.control()) && owner.pendingControl() == null
                && "OPEN".equals(owner.accountStatus()) && owner.closeDate() == null
                && owner.mandateActive() && "OWNER".equals(owner.mandateRole())
                && owner.currency() != null && owner.currency().matches("[A-Z]{3}");
    }

    private static void transition(Fence fence, UUID receipt, long version) {
        if (fence == null || !Objects.equals(fence.receiptId(), receipt) || fence.version() != version
                || fence.state() != State.HELD) throw new Conflict();
    }

    private static Fence transitionTo(Fence f, State state, UUID claim, UUID cancel) {
        return new Fence(f.actionId(), f.receiptId(), f.tenantId(), f.actorId(), f.partyId(), f.accountId(),
                f.contractId(), f.mandateId(), f.accountVersion(), f.currency(), f.reasonCode(),
                f.acceptNotAfterEpochMillis(), f.recordedAt(), state, f.version() + 1, claim, cancel);
    }

    private static boolean id(UUID id) { return id != null && !id.equals(new UUID(0, 0)); }
    private static boolean reason(String reason) { return reason != null && reason.matches("[A-Z][A-Z0-9_]{0,63}"); }

    public static final class Conflict extends IllegalStateException {
        public Conflict() { super("ACCOUNT_CARD_ENROLLMENT_INTENT_OR_STATE_CONFLICT"); }
    }
    public static final class Refused extends IllegalStateException {
        public Refused() { super("CURRENT_ACCOUNT_CARD_ENROLLMENT_ADMISSION_REQUIRED"); }
    }
}
