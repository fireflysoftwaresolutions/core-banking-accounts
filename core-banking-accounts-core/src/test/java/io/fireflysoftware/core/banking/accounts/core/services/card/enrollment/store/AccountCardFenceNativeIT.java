/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.store;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.*;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.impl.*;
import io.fireflysoftware.core.banking.accounts.core.services.funds.*;
import io.fireflysoftware.core.banking.accounts.core.services.funds.impl.*;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment.AccountCardEnrollmentContracts.*;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds.AccountFundsContracts;
import io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment.*;
import io.fireflysoftware.core.banking.accounts.models.repositories.funds.*;
import io.r2dbc.spi.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Real owner database and released canonical audit; identity/PDP and remote Ledger are controlled fixtures. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountCardFenceNativeIT {
    static String host,database,user,password;static int port;
    static final String writerPassword=UUID.randomUUID().toString(),readerPassword=UUID.randomUUID().toString(),fundsPassword=UUID.randomUUID().toString();
    static final Duration LIMIT=Duration.ofSeconds(10);
    static final List<String> TABLES=List.of("account_card_enrollment_fence","account_card_enrollment_action","account_card_enrollment_audit","firefly_audit.audit_head","firefly_audit.audit_event","firefly_audit.audit_outbox");

    @BeforeAll static void install() throws Exception {
        Path path=Path.of(Objects.requireNonNull(System.getProperty("accounts.card-fence.test.database-profile")));
        assertEquals(Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,java.nio.file.attribute.PosixFilePermission.OWNER_WRITE),Files.getPosixFilePermissions(path));
        var profile=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(path));
        assertEquals("isolated-owned-account-card-fence-native",profile.get("purpose").asText());
        host=profile.get("host").asText();assertEquals("127.0.0.1",host);port=profile.get("port").asInt();database=profile.get("database").asText();
        assertTrue(database.startsWith("accounts_card_fence_native_"));user=profile.get("user").asText();password=profile.get("password").asText();
        try(var c=owner()) {
            assertEquals("0",scalar(c,"SELECT count(*) FROM pg_tables WHERE schemaname='public'"));
            sql(c,"REVOKE CREATE ON SCHEMA public FROM PUBLIC;CREATE ROLE firefly_funds_policy_reader NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS;CREATE ROLE firefly_funds_policy_writer NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS");
            try(var files=Files.list(Path.of(System.getProperty("accounts.card-fence.test.migrations")))) {
                for(Path file:files.filter(p->p.getFileName().toString().matches("V[0-9]+__.*\\.sql")).sorted(Comparator.comparingInt(p->Integer.parseInt(p.getFileName().toString().split("__")[0].substring(1)))).toList()) sql(c,Files.readString(file));
            }
            sql(c,"CREATE ROLE firefly_audit_guard NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS");
            sql(c,"CREATE ROLE firefly_audit_writer LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS PASSWORD '"+writerPassword+"'");
            sql(c,"CREATE ROLE firefly_audit_reader LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS PASSWORD '"+UUID.randomUUID()+"'");
            for(String resource:List.of("db/optional/immutable-audit/postgresql.sql","db/optional/immutable-audit/completed-append.sql")) {
                try(var stream=AccountCardFenceNativeIT.class.getClassLoader().getResourceAsStream(resource)){assertNotNull(stream);sql(c,new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            }
            sql(c,"SET ROLE firefly_audit_guard;GRANT EXECUTE ON FUNCTION public.firefly_audit_require_completed(uuid,uuid,bigint,text,boolean) TO firefly_audit_writer;RESET ROLE");
            sql(c,Files.readString(Path.of(System.getProperty("accounts.card-fence.test.migration"))));
            sql(c,"CREATE ROLE accounts_card_test_reader LOGIN PASSWORD '"+readerPassword+"' IN ROLE accounts_card_fence_reader");
            sql(c,"CREATE ROLE accounts_card_test_funds LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '"+fundsPassword+"';GRANT USAGE ON SCHEMA public TO accounts_card_test_funds;GRANT SELECT,INSERT,UPDATE ON account,account_money_enrollment,account_money_action,account_status_history TO accounts_card_test_funds");
        }
    }
    static java.sql.Connection owner() throws Exception {return DriverManager.getConnection("jdbc:postgresql://"+host+":"+port+"/"+database,user,password);}
    static void sql(java.sql.Connection c,String sql)throws Exception {try(var s=c.createStatement()){s.execute(sql);}}
    static String scalar(java.sql.Connection c,String sql)throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    static Map<String,String> snapshot()throws Exception {var result=new LinkedHashMap<String,String>();try(var c=owner()){
        var tables=new TreeSet<>(TABLES);try(var s=c.createStatement();var rows=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")){while(rows.next())tables.add(rows.getString(1));}
        for(String t:tables){assertTrue(t.matches("[a-z_]+(?:\\.[a-z_]+)?"));result.put(t,scalar(c,"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "+t+" t"));}}return result;}
    static ConnectionFactory factory(String role,String secret) {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder().option(ConnectionFactoryOptions.DRIVER,"postgresql")
                .option(ConnectionFactoryOptions.HOST,host).option(ConnectionFactoryOptions.PORT,port).option(ConnectionFactoryOptions.DATABASE,database)
                .option(ConnectionFactoryOptions.USER,role).option(ConnectionFactoryOptions.PASSWORD,secret).build());
    }
    static AccountCardFenceSession session(boolean reader) {
        var factory=new OwnedLeaseConnectionFactory(factory(reader?"accounts_card_test_reader":"firefly_audit_writer",reader?readerPassword:writerPassword));
        var client=DatabaseClient.create(factory);var template=template(client);
        return new AccountCardFenceSession(reader?AccountCardFenceSession.Role.READER:AccountCardFenceSession.Role.COMMAND,factory,client,template,new R2dbcTransactionManager(factory));
    }
    static R2dbcEntityTemplate template(DatabaseClient client) {
        var conversions=org.springframework.data.r2dbc.convert.R2dbcCustomConversions.of(PostgresDialect.INSTANCE,
                io.fireflysoftware.core.banking.accounts.models.config.AccountsStatusR2dbcConverters.converters());
        var mapping=new org.springframework.data.r2dbc.mapping.R2dbcMappingContext();mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        var converter=new org.springframework.data.r2dbc.convert.MappingR2dbcConverter(mapping,conversions);
        return new R2dbcEntityTemplate(client,new org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE,converter));
    }
    static final class Fixture implements AccountCardFenceAuthority {
        final UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),party=UUID.randomUUID(),account=UUID.randomUUID(),contract=UUID.randomUUID(),mandate=UUID.randomUUID();
        final AccountCardFenceSession command=session(false),reader=session(true);
        final AccountCardFenceService service=new AccountCardFenceServiceImpl(this,command,reader,Clock.systemUTC(),System::nanoTime,UUID::randomUUID);
        final AtomicBoolean withdrawn=new AtomicBoolean();final AtomicInteger authorityChecks=new AtomicInteger(),ledgerCalls=new AtomicInteger();
        UUID currentTenant=tenant,currentActor=actor,currentParty=party;int withdrawOnCheck=Integer.MAX_VALUE;
        Acquire request=new Acquire(UUID.randomUUID(),7,System.currentTimeMillis()+120000,"CUSTOMER_REQUEST");
        Fixture() throws Exception {
            try(var c=owner()) {
                sql(c,"INSERT INTO account(account_id,contract_id,account_number,account_type,currency,open_date,account_status,branch_id) VALUES('"+account+"','"+contract+"','fence-"+account+"','CHECKING','USD',CURRENT_DATE,'OPEN','"+UUID.randomUUID()+"')");
                sql(c,"INSERT INTO servicing_mandate(servicing_mandate_id,account_id,party_id,role,active) VALUES('"+mandate+"','"+account+"','"+party+"','OWNER',true)");
                sql(c,"INSERT INTO account_money_enrollment(account_id,tenant_id,enrollment_id,contract_id,currency,kind,mode,control,version) VALUES('"+account+"','"+tenant+"','"+UUID.randomUUID()+"','"+contract+"','USD','CUSTOMER','MANAGED','ACTIVE',7)");
            }
        }
        public Mono<Permit> authorize(UUID accountId,Purpose purpose) {var now=Instant.now();var principal=SecurityPrincipal.builder().issuer("https://fixture.invalid").subject("controlled-owner").tenantId(currentTenant.toString()).build();
            return Mono.just(new Permit(principal,currentTenant,currentActor,currentParty,accountId,purpose,new AuditActor(principal.issuer(),principal.subject(),"cards-fixture",principal.tenantId(),null,null),"controlled-policy-v1",now,now.plusSeconds(5)));}
        public Mono<Void> requireCurrent(Permit permit) {return Mono.defer(()->withdrawn.get()||authorityChecks.incrementAndGet()>=withdrawOnCheck?Mono.error(new IllegalStateException("Controlled current authority withdrawn")):Mono.empty());}
        Observation acquire(){return service.acquire(account,request).block(LIMIT);}
        Observation recover(){return service.recover(account,request.actionId(),request.actionId()).block(LIMIT);}
        AccountFundsService funds() {
            var factory=factory("accounts_card_test_funds",fundsPassword);var client=DatabaseClient.create(factory);var template=template(client);
            var repositories=new R2dbcRepositoryFactory(template);var nativeRepository=repositories.getRepository(FundsNativeRepository.class);
            var enrollments=repositories.getRepository(AccountMoneyEnrollmentRepository.class,RepositoryFragments.just(new FundsPersistenceImpl(template)));
            var actions=repositories.getRepository(AccountMoneyActionRepository.class);var beans=new StaticListableBeanFactory();
            // Only the existing owning control coordination is exercised here; no Ledger physical effect is claimed.
            LedgerFundsPort port=(t,e,a,target)->Mono.fromRunnable(ledgerCalls::incrementAndGet);beans.addBean("controlledLedger",port);
            return new AccountFundsServiceImpl(enrollments,actions,nativeRepository,new R2dbcTransactionManager(factory),beans.getBeanProvider(LedgerFundsPort.class),new FundsDatabaseContextImpl(nativeRepository));
        }
        SecurityPrincipal fundsPrincipal(){return SecurityPrincipal.builder().subject("controlled-funds-operator").tenantId(tenant.toString()).scopes(Set.of("funds:control")).claims(Map.of("exp",Instant.now().plusSeconds(120),"funds_accounts",List.of(account.toString()))).build();}
        Mono<AccountFundsContracts.Enrollment> control(AccountFundsContracts.Control target) {return funds().control(fundsPrincipal(),tenant.toString(),account,new AccountFundsContracts.Manage(UUID.randomUUID(),target,7L));}
    }

    @Test @Order(1) void acquireCommitsOriginalFenceReceiptAndCompletedCanonicalAuditInOneTransaction()throws Exception {
        var f=new Fixture();var result=f.acquire();assertEquals(State.HELD,result.fence().state());assertTrue(result.currentlyEligible());
        try(var c=owner()) {
            assertEquals("1",scalar(c,"SELECT count(*) FROM account_card_enrollment_audit b JOIN firefly_audit.audit_event e ON e.resource_tenant_id=b.tenant_id AND e.event_id=b.event_id AND e.sequence=b.audit_sequence AND e.record_hash=b.audit_record_hash WHERE b.operation_action_id='"+f.request.actionId()+"'"));
            assertEquals("1",scalar(c,"SELECT count(*) FROM firefly_audit_private.context x JOIN account_card_enrollment_audit b ON b.audit_context_nonce=x.nonce WHERE b.operation_action_id='"+f.request.actionId()+"' AND x.db_session_user='firefly_audit_writer' AND x.new_event"));
            assertEquals("1",scalar(c,"SELECT count(DISTINCT x) FROM (SELECT xmin::text x FROM account_card_enrollment_fence WHERE action_id='"+f.request.actionId()+"' UNION ALL SELECT xmin::text FROM account_card_enrollment_action WHERE operation_action_id='"+f.request.actionId()+"' UNION ALL SELECT xmin::text FROM account_card_enrollment_audit WHERE operation_action_id='"+f.request.actionId()+"') t"));
            assertEquals("0",scalar(c,"SELECT count(*) FROM accounts_card_fence_private.context"));
        }
    }
    @Test @Order(2) void exactAcquireReplayPreservesEveryBusinessAndCanonicalAuditRow()throws Exception {
        var f=new Fixture();var first=f.acquire();var before=snapshot();var second=f.acquire();assertEquals(first.fence(),second.fence());assertEquals(first.actionReceipt(),second.actionReceipt());assertEquals(before,snapshot());
    }
    @Test @Order(3) void claimAndExactClaimReplayRetainOriginalReceiptAndRefuseCancellation()throws Exception {
        var f=new Fixture();var original=f.acquire();var request=new Claim(UUID.randomUUID(),original.fence().receiptId(),0);
        var first=f.service.claim(f.account,f.request.actionId(),request).block(LIMIT);assertEquals(State.CLAIMED,first.fence().state());var before=snapshot();
        var second=f.service.claim(f.account,f.request.actionId(),request).block(LIMIT);assertEquals(first.actionReceipt(),second.actionReceipt());assertEquals(before,snapshot());
        assertThrows(AccountCardFenceException.class,()->f.service.cancel(f.account,f.request.actionId(),new Cancel(UUID.randomUUID(),original.fence().receiptId(),0,"CUSTOMER_CANCELLED")).block(LIMIT));assertEquals(before,snapshot());
    }
    @Test @Order(4) void expiredHeldFenceRemainsPendingButCanBeCancelledWithFreshAuthority()throws Exception {
        var f=new Fixture();f.request=new Acquire(f.request.actionId(),7,System.currentTimeMillis()+1500,"CUSTOMER_REQUEST");var first=f.acquire();Thread.sleep(1600);
        assertFalse(f.recover().currentlyEligible());assertThrows(AccountCardFenceException.class,()->f.service.claim(f.account,f.request.actionId(),new Claim(UUID.randomUUID(),first.fence().receiptId(),0)).block(LIMIT));
        var request=new Cancel(UUID.randomUUID(),first.fence().receiptId(),0,"CUSTOMER_CANCELLED");var cancelled=f.service.cancel(f.account,f.request.actionId(),request).block(LIMIT);
        assertEquals(State.CANCELLED,cancelled.fence().state());assertEquals(first.fence().acceptNotAfterEpochMillis(),cancelled.fence().acceptNotAfterEpochMillis());var before=snapshot();
        assertEquals(cancelled.actionReceipt(),f.service.cancel(f.account,f.request.actionId(),request).block(LIMIT).actionReceipt());assertEquals(before,snapshot());
    }
    @Test @Order(5) void competingClaimAndCancellationHaveOnlyOneDurableWinner()throws Exception {
        var f=new Fixture();var first=f.acquire();var claim=f.service.claim(f.account,f.request.actionId(),new Claim(UUID.randomUUID(),first.fence().receiptId(),0)).materialize().subscribeOn(Schedulers.parallel());
        var cancel=f.service.cancel(f.account,f.request.actionId(),new Cancel(UUID.randomUUID(),first.fence().receiptId(),0,"CUSTOMER_CANCELLED")).materialize().subscribeOn(Schedulers.parallel());
        var outcomes=Mono.zip(claim,cancel).block(LIMIT);assertEquals(1,(outcomes.getT1().isOnNext()?1:0)+(outcomes.getT2().isOnNext()?1:0));
        var loser=outcomes.getT1().isOnError()?outcomes.getT1().getThrowable():outcomes.getT2().getThrowable();
        assertEquals(AccountCardFenceException.Reason.CONFLICT,assertInstanceOf(AccountCardFenceException.class,loser).reason());
        try(var c=owner()){assertEquals("2",scalar(c,"SELECT count(*) FROM account_card_enrollment_action WHERE fence_action_id='"+f.request.actionId()+"'"));assertEquals("2",scalar(c,"SELECT count(*) FROM account_card_enrollment_audit WHERE tenant_id='"+f.tenant+"'"));}
    }
    @Test @Order(6) void existingCloseControlCannotPassPendingFenceOrInvokeLedger()throws Exception {
        var f=new Fixture();f.acquire();var before=snapshot();assertThrows(RuntimeException.class,()->f.control(AccountFundsContracts.Control.CLOSED).block(LIMIT));assertEquals(0,f.ledgerCalls.get());assertEquals(before,snapshot());
        try(var c=owner()){assertEquals("ACTIVE:7",scalar(c,"SELECT control||':'||version FROM account_money_enrollment WHERE account_id='"+f.account+"'"));}
    }
    @Test @Order(7) void existingSecurityBlockStillCompletesAndRevokesCurrentFenceEligibility()throws Exception {
        var f=new Fixture();var held=f.acquire();var blocked=f.control(AccountFundsContracts.Control.BLOCKED).block(LIMIT);assertEquals(AccountFundsContracts.Control.BLOCKED,blocked.control());assertEquals(1,f.ledgerCalls.get());
        var recovered=f.recover();assertFalse(recovered.currentlyEligible());assertEquals(held.fence(),recovered.fence());assertEquals(State.HELD,recovered.fence().state());
        assertThrows(AccountCardFenceException.class,()->f.service.claim(f.account,f.request.actionId(),new Claim(UUID.randomUUID(),held.fence().receiptId(),0)).block(LIMIT));
    }
    @Test @Order(8) void concurrentCloseAndAcquireNeverLeaveAClaimableFenceOnClosingAccount()throws Exception {
        var f=new Fixture();var outcomes=Mono.zip(f.service.acquire(f.account,f.request).materialize().subscribeOn(Schedulers.parallel()),
                f.control(AccountFundsContracts.Control.CLOSED).materialize().subscribeOn(Schedulers.parallel())).block(LIMIT);
        assertEquals(1,(outcomes.getT1().isOnNext()?1:0)+(outcomes.getT2().isOnNext()?1:0));
        try(var c=owner()){assertEquals("0",scalar(c,"SELECT count(*) FROM account_card_enrollment_fence f JOIN account_money_enrollment e USING(account_id) WHERE f.account_id='"+f.account+"' AND f.state IN('HELD','CLAIMED') AND e.control IN('CLOSING','CLOSED')"));}
    }
    @Test @Order(9) void authorityWithdrawalRollsBackFenceActionAndAuditTogether()throws Exception {
        var f=new Fixture();f.withdrawOnCheck=1;var before=snapshot();var error=assertThrows(AccountCardFenceException.class,f::acquire);assertTrue(error.transactionFacts().rollbackAcknowledged());assertEquals(before,snapshot());
    }
    @Test @Order(10) void wrongTenantPartyOrActorCannotReadTheOriginalReceipt()throws Exception {
        var f=new Fixture();f.acquire();var before=snapshot();f.currentTenant=UUID.randomUUID();assertThrows(AccountCardFenceException.class,f::recover);f.currentTenant=f.tenant;
        f.currentParty=UUID.randomUUID();assertThrows(AccountCardFenceException.class,f::recover);f.currentParty=f.party;f.currentActor=UUID.randomUUID();assertThrows(AccountCardFenceException.class,f::recover);assertEquals(before,snapshot());
    }
    @Test @Order(11) void readRecoveryPreservesOriginalRowsAndReaderCannotWriteBusinessTables()throws Exception {
        var f=new Fixture();var first=f.acquire();var before=snapshot();var recovered=f.recover();assertEquals(first.fence(),recovered.fence());assertEquals(first.actionReceipt(),recovered.actionReceipt());assertEquals(before,snapshot());
        assertThrows(RuntimeException.class,()->f.reader.operations().insert(AccountCardFence.from(first.fence())).block(LIMIT));assertEquals(before,snapshot());
    }
    @Test @Order(12) void mappingCallbackCannotSubstituteCanonicalAuditBinding()throws Exception {
        var f=new Fixture();var before=snapshot();org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback<AccountCardFenceAudit> callback=(row,table)->Mono.just(new AccountCardFenceAudit(row.operationActionId(),row.tenantId(),row.eventId(),row.auditContextNonce(),row.auditSequence(),"0".repeat(64)));
        f.command.operations().setEntityCallbacks(org.springframework.data.mapping.callback.ReactiveEntityCallbacks.create(callback));var error=assertThrows(AccountCardFenceException.class,f::acquire);assertTrue(error.transactionFacts().rollbackAcknowledged());assertEquals(before,snapshot());
    }
    @Test @Order(13) void revokedOriginalCompletedAccessorPreventsCommitAndWithholdsReplay()throws Exception {
        var f=new Fixture();f.acquire();var fresh=new Fixture();var before=snapshot();try(var c=owner()){sql(c,"SET ROLE firefly_audit_guard;REVOKE EXECUTE ON FUNCTION public.firefly_audit_require_completed(uuid,uuid,bigint,text,boolean) FROM firefly_audit_writer;RESET ROLE");}
        try{for(var attempt:List.of(f,fresh)){var error=assertThrows(AccountCardFenceException.class,attempt::acquire);assertTrue(error.transactionFacts().rollbackAcknowledged());}assertEquals(before,snapshot());}
        finally{try(var c=owner()){sql(c,"SET ROLE firefly_audit_guard;GRANT EXECUTE ON FUNCTION public.firefly_audit_require_completed(uuid,uuid,bigint,text,boolean) TO firefly_audit_writer;RESET ROLE");}}
    }
    @Test @Order(14) void changedAcquireIntentCannotReuseOriginalActionOrExtendItsDeadline()throws Exception {
        var f=new Fixture();f.acquire();var before=snapshot();f.request=new Acquire(f.request.actionId(),7,f.request.acceptNotAfterEpochMillis()+1000,"CUSTOMER_REQUEST");var error=assertThrows(AccountCardFenceException.class,f::acquire);assertEquals(AccountCardFenceException.Reason.CONFLICT,error.reason());assertEquals(before,snapshot());
    }
    @Test @Order(15) void concurrentSecurityBlockAndClaimKeepBlockingAvailableAndRevokeEligibility()throws Exception {
        var f=new Fixture();var held=f.acquire();var claim=f.service.claim(f.account,f.request.actionId(),new Claim(UUID.randomUUID(),held.fence().receiptId(),0)).materialize().subscribeOn(Schedulers.parallel());
        var blocked=f.control(AccountFundsContracts.Control.BLOCKED).materialize().subscribeOn(Schedulers.parallel());var outcomes=Mono.zip(claim,blocked).block(LIMIT);
        assertTrue(outcomes.getT2().isOnNext());assertEquals(AccountFundsContracts.Control.BLOCKED,outcomes.getT2().get().control());
        var recovered=f.recover();assertFalse(recovered.currentlyEligible());assertEquals(outcomes.getT1().isOnNext()?State.CLAIMED:State.HELD,recovered.fence().state());
    }
    @Test @Order(16) void omittedCanonicalAuditCannotCommitEvenWhenCallerOmitsExplicitCompletion()throws Exception {
        var f=new Fixture();var before=snapshot();var permit=f.authorize(f.account,AccountCardFenceAuthority.Purpose.ACQUIRE).block();UUID nonce=UUID.randomUUID();
        var window=new AccountCardFenceWindow(Clock.systemUTC(),System::nanoTime);window.constrain(permit);var actor=permit.auditActor();
        var definition=new org.springframework.transaction.support.DefaultTransactionDefinition();definition.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var attempt=f.command.transactions().execute(window.budget(),definition,scope->f.command.nativeRepository().enter(nonce,f.tenant,f.actor,f.party,f.account,f.request.actionId(),f.request.actionId(),"ACQUIRE",null,7L,"CUSTOMER_REQUEST",f.request.acceptNotAfterEpochMillis(),actor.issuer(),actor.subject(),actor.clientId(),actor.platformTenantId(),permit.policyRevision(),permit.effectiveUntil().atOffset(ZoneOffset.UTC),permit.effectiveUntil().atOffset(ZoneOffset.UTC))
            .flatMap(stamp->f.command.store().insert(new AccountCardFence(f.request.actionId(),UUID.randomUUID(),f.tenant,f.actor,f.party,f.account,f.contract,f.mandate,7,"USD","CUSTOMER_REQUEST",f.request.acceptNotAfterEpochMillis(),stamp.databaseTime(),"HELD",0,null,null))));
        var error=assertThrows(RootTransactionException.class,()->attempt.block(LIMIT));assertFalse(error.transactionFacts().commitAcknowledged());assertEquals(before,snapshot());
    }
}
