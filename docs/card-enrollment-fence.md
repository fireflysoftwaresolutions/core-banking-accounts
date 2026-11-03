# Account-owned card enrollment fence

Accounts admits one pending card enrollment per account using its actual managed enrollment, canonical account and active `OWNER` mandate. The signed current party must match that stored mandate. A caller-provided account or party identifier is not ownership proof. This capability records account admission; it neither issues nor activates a card and does not assert issuer, processor or product eligibility.

The four operations are under `/api/v1/accounts/{accountId}/card-enrollments`:

| Operation | Explicit intent | Result |
| --- | --- | --- |
| `POST /` | `actionId`, `expectedAccountVersion`, `acceptNotAfterEpochMillis`, `reasonCode` | Original `HELD` fence and acquisition receipt |
| `POST /{actionId}/claim` | `claimActionId`, original `receiptId`, `expectedFenceVersion` | Original fence becomes `CLAIMED` under a designated Cards client |
| `POST /{actionId}/cancel` | `cancellationActionId`, original `receiptId`, `expectedFenceVersion`, `reasonCode` | A still-`HELD` fence becomes `CANCELLED` |
| `GET /{actionId}/operations/{operationActionId}` | Original path identities only | Read-only original fence and operation receipt plus current eligibility observation |

Missing or null intent fields are invalid. Preserve every original action across lost responses. An exact replay returns its original operation receipt; a changed intent or a competing winning transition conflicts. GET does not append an audit event, renew a deadline, claim, cancel or call another owner/provider. Historical receipt times and current observation times are absolute `Instant` values with explicit offsets.

Acquisition requires the actual account to be open, managed, active, customer-owned and at the expected version. Claim rechecks the original deadline, original account version, actual account/enrollment state, mandate and current purpose-specific client authority. An expired `HELD` fence remains pending and can be cancelled under fresh cancellation authority. Expiry never releases it automatically. A `CLAIMED` fence cannot be released by customer cancellation or a timeout: post-claim terminal resolution requires an additional owner-proven contract and is not implemented here. Cards must not claim before its actual downstream prerequisites are ready.

The canonical account row is locked before the existing managed-enrollment gate. The new optional migration also guards the existing account/enrollment mutation paths, so pending fences block closure and re-enabling rather than merely existing as detached rows. Security blocking remains available; it changes the account version/status and makes the retained fence ineligible. Concurrent claim/cancel or closure/admission has one compatible durable winner.

## Native deployment

Apply the original `V1` through `V16` migrations first. Install the published immutable-audit PostgreSQL scripts, including `completed-append.sql`, using their original restricted roles and grants. Then explicitly apply `db/optional/card-enrollment/V17__Account_Card_Enrollment_Fence.sql`. It is deliberately outside automatic migration discovery. Do not change the original audit writer, audit accessor provenance, row policies or runtime role privileges to make a deployment pass.

Commands use the original `firefly_audit_writer` restricted `LOGIN NOINHERIT` connection. Reads use a distinct restricted login with inherited membership in `accounts_card_fence_reader`; this role has SELECT-only business permissions. Neither login may own the guard role or have elevated PostgreSQL privileges. Application default CRUD, command, reader and cache connections remain distinct. The cache uses its own configured PostgreSQL database and role.

Enable `firefly.accounts.card-enrollment.enabled=true`, the published `firefly.security.resource-server.enabled=true` receiver and strict policy. Configure trusted issuer/JWKS, the exact Accounts audience, platform tenant and claim paths through the published resource-server properties. Existing Funds and Asset Price security chains retain their own paths and order; the card-fence receiver delegates to the same published JWT decoder, principal converter and strict policy implementation only for fence routes. Without the native receiver, those routes deny access.

The `firefly.accounts.card-enrollment.native` configuration requires `issuer`, `platform-tenant`, `resource-tenant`, `audience`, `client-claim`, `actor-claim`, `party-claim`, `kind-claim` and separate `acquire-client-ids`, `claim-client-ids`, `cancel-client-ids`, `read-client-ids`. The principal must be a current human with the exact audience, canonical actor/party IDs and `accounts:card-enrollment:<purpose>` scope. Claim clients must be the explicitly designated Cards services. No token or bearer credential is stored with a fence.

Both `firefly.accounts.card-enrollment.database.command` and `.reader` require `host`, `port`, `name`, `username`, `password` and `ssl-mode`. Supply secrets through deployment secret configuration. Both sessions must address the same actual owner database, while retaining distinct roles and owned transaction resources.

Provide real `PolicyDecisionPort` and `PolicyEvaluationPort` implementations. The optional published OPA adapters can be wired with `firefly.accounts.card-enrollment.opa.enabled`, `endpoint`, `decision-path` and `evaluation-path`; loopback HTTP requires explicit `allow-loopback-http=true` for controlled tests. The owner action is `accounts.card-enrollment.<purpose>`, resource `accounts/account/<accountId>`, with the signed actor/party, mapped resource tenant, platform tenant, account, purpose and client in the context. An allowed decision must include same-response policy provenance and no unfulfilled obligations. Indeterminate/unavailable policy never becomes a business grant.

Every mutation pairs mapped Spring Data business rows with the original completed canonical audit record, original audit nonce, sequence and hash in one bounded transaction. The native completion guard rejects an omitted pair. Protected readback and exact replay preserve the original receipt/audit identity. Cleanup and commit facts distinguish acknowledged rollback, uncertain outcome and a committed result withheld after authority loss; HTTP errors retain the original operation action and do not imply that a new action should be submitted.

## Producer and qualification

Generate the complete contract from the original `OpenApiGenApplication` and original controllers, then feed those exact emitted bytes to the existing full SDK generator. `firefly.openapi.instant-scalar.enabled=true` supplies the published `FireflyInstant` component; the SDK's official schema mapping preserves `java.time.Instant`. No handwritten client or reduced operation specification is supported. Generated Java and emitted contract replacements are build evidence, not source changes to commit.

`AccountCardFenceNativeIT` exercises real PostgreSQL ownership, mapped persistence, canonical audit, replay, rollback and concurrent controls; its authority and remote Ledger ports are explicitly controlled fixtures. `AccountCardFenceRuntimeIT` starts the actual packaged Accounts executable and calls the complete official SDK with signed tokens and the published native receiver/OPA adapters. Its identity/PDP HTTP backend is controlled, and its existing account/mandate/enrollment rows are test fixtures. Neither suite establishes card issuance or a live issuer effect. The original repository native suites and original Docker database tests remain separate mandatory regression checks for full-PR qualification.
