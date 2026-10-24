package io.fireflysoftware.core.banking.accounts.models.repositories.funds;

import io.fireflysoftware.core.banking.accounts.models.entities.funds.*;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Fixed native primitives whose locking or atomic admission semantics cannot be split into CRUD.
 */
public interface FundsNativeRepository extends Repository<AccountMoneyEnrollment, UUID> {
    @Query(
            "SELECT account_id, contract_id, currency, account_status::text AS account_status,"
                + " close_date FROM account WHERE account_id=:account FOR NO KEY UPDATE")
    Mono<FundsAccountSnapshot> lockAccount(@Param("account") UUID account);

    @Modifying
    @Query("SELECT pg_advisory_xact_lock(hashtextextended(:account,55601))")
    Mono<Void> lockEnrollmentGate(@Param("account") String account);

    @Modifying
    @Query(
            """
            INSERT INTO account_money_enrollment(account_id,tenant_id,enrollment_id,contract_id,currency,kind)
            SELECT a.account_id,:t,:e,a.contract_id,a.currency,:k FROM account a
            WHERE a.account_id=:a AND a.account_status='OPEN' AND COALESCE(a.overdraft_limit,0)=0
              AND NOT EXISTS(SELECT 1 FROM account_balance b WHERE b.account_id=a.account_id)
              AND NOT EXISTS(SELECT 1 FROM account_statement h WHERE h.account_id=a.account_id)
              AND NOT EXISTS(SELECT 1 FROM account_limit h WHERE h.account_id=a.account_id)
              AND NOT EXISTS(SELECT 1 FROM savings_interest h WHERE h.account_id=a.account_id)
              AND NOT EXISTS(SELECT 1 FROM term_deposit h WHERE h.account_id=a.account_id)
              AND NOT EXISTS(SELECT 1 FROM account_sweep h WHERE h.account_id=a.account_id)
              AND NOT EXISTS(SELECT 1 FROM account_restriction r WHERE r.account_id=a.account_id AND r.is_active)
              AND NOT EXISTS(SELECT 1 FROM account_space s WHERE s.account_id=a.account_id
                AND (s.space_type::text<>'MAIN' OR s.balance<>0 OR s.is_frozen OR s.enable_automatic_transfers))
              AND (SELECT COUNT(*) FROM account_space s WHERE s.account_id=a.account_id)<=1
              AND NOT EXISTS(SELECT 1 FROM space_transaction st JOIN account_space s USING(account_space_id) WHERE s.account_id=a.account_id)
            ON CONFLICT(account_id) DO NOTHING
            """)
    Mono<Integer> admitFreshEnrollment(
            @Param("t") String tenant,
            @Param("e") UUID enrollment,
            @Param("k") String kind,
            @Param("a") UUID account);

    @Modifying
    @Query(
            "INSERT INTO"
                + " account_money_action(tenant_id,action_id,account_id,target,expected_version,actor)"
                + " VALUES(:tenant,:action,:account,:target,:version,:actor) ON CONFLICT DO"
                + " NOTHING")
    Mono<Integer> claimAction(
            @Param("tenant") String tenant,
            @Param("action") UUID action,
            @Param("account") UUID account,
            @Param("target") String target,
            @Param("version") long version,
            @Param("actor") String actor);

    @Query(
            """
            SELECT NOT r.rolsuper AND NOT r.rolbypassrls
              AND NOT pg_catalog.has_schema_privilege(current_user,'public','CREATE')
              AND NOT pg_catalog.has_schema_privilege(current_user,'funds_private','USAGE')
              AND NOT EXISTS(SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relrowsecurity AND pg_catalog.pg_get_userbyid(c.relowner)=current_user)
              AND EXISTS(SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
                JOIN pg_catalog.pg_roles owner ON owner.oid=p.proowner WHERE n.nspname='public' AND p.proname='funds_reference_allowed'
                AND p.prosecdef AND NOT owner.rolsuper AND NOT owner.rolbypassrls AND NOT owner.rolcanlogin) AS allowed
            FROM pg_catalog.pg_roles r WHERE r.rolname=current_user
            """)
    Mono<Boolean> runtimeAllowed();

    @Modifying
    @Query(
            "SELECT"
                + " set_config('firefly.funds.tenant',:tenant,true),set_config('firefly.funds.accounts',:accounts,true),set_config('firefly.funds.purpose',:purpose,true)")
    Mono<Void> establishContext(
            @Param("tenant") String tenant,
            @Param("accounts") String accounts,
            @Param("purpose") String purpose);

    @Query("SELECT CURRENT_DATE AS business_date, CURRENT_TIMESTAMP::timestamp AS business_time")
    Mono<FundsDatabaseTime> databaseTime();
}
