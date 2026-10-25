package io.fireflysoftware.core.banking.accounts.web;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = AccountsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "firefly.eda.enabled=false"})
class AccountFundsRuntimeDatabaseTest {
    static final java.util.Map<UUID,java.util.Map<String,Object>> APPROVALS=new java.util.concurrent.ConcurrentHashMap<>();
    static final com.nimbusds.jose.jwk.RSAKey KEY;
    static final com.sun.net.httpserver.HttpServer JWKS;
    static {
        try {
            KEY = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("owned-funds-fixture").generate();
            JWKS = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new com.nimbusds.jose.jwk.JWKSet(KEY.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            JWKS.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static String token(String tenant, java.util.List<UUID> accounts, String... scopes) throws Exception {
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("https://owned-funds-fixture.invalid")
                .audience("accounts-funds").subject("synthetic-operator").claim("tenant_id", tenant)
                .claim("scope", String.join(" ", scopes)).claim("funds_accounts", accounts.stream().map(UUID::toString).toList())
                .claim("funds_enrollments",accounts.stream().map(APPROVALS::get).filter(java.util.Objects::nonNull).toList())
                .expirationTime(java.util.Date.from(java.time.Instant.now().plusSeconds(300))).build();
        var signed = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
        signed.sign(new com.nimbusds.jose.crypto.RSASSASigner(KEY));
        return signed.serialize();
    }


    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("ledger_qualification")
            .withUsername("ledger_test")
            .withPassword("synthetic-disposable-test-password")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(5432))));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        prepareRuntimeRole();
        registry.add("funds.security.issuer", () -> "https://owned-funds-fixture.invalid");
        registry.add("funds.security.audience", () -> "accounts-funds");
        registry.add("funds.security.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        registry.add("funds.currency-policy.version", () -> "owned-test-policy-v1");
        registry.add("DB_HOST", DATABASE::getHost);
        registry.add("DB_PORT", () -> DATABASE.getMappedPort(5432));
        registry.add("DB_NAME", DATABASE::getDatabaseName);
        registry.add("DB_USERNAME", ()->"funds_runtime");
        registry.add("DB_PASSWORD", ()->"owned-disposable-runtime-password");
        registry.add("DB_SSL_MODE", () -> "disable");
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + DATABASE.getHost() + ":"
                + DATABASE.getMappedPort(5432) + "/" + DATABASE.getDatabaseName());
        registry.add("spring.r2dbc.username", ()->"funds_runtime");
        registry.add("spring.r2dbc.password", ()->"owned-disposable-runtime-password");
        registry.add("spring.flyway.url", DATABASE::getJdbcUrl);
        registry.add("spring.flyway.user", DATABASE::getUsername);
        registry.add("spring.flyway.password", DATABASE::getPassword);
    }

    @Autowired
    DatabaseClient database;

    @Autowired
    Flyway migrations;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void managedFundsRoutesRequireTrustedIdentityBeforeEnrollment() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/v1/tenants/synthetic-tenant/funds/accounts/" + UUID.randomUUID() + "/enrollment"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }
    @Test
    void beginningMoneyEnrollmentRequiresAnActualOpenAccountAndFencesLegacyWrites() throws Exception {
        UUID account = UUID.randomUUID();
        UUID contract = UUID.randomUUID();
        database.sql("INSERT INTO account(account_id,contract_id,account_number,account_type,currency,open_date,account_status,branch_id) VALUES (:a,:c,:n,'CHECKING','USD',CURRENT_DATE,'OPEN',:b)")
                .bind("a",account).bind("c",contract).bind("n",account.toString()).bind("b",UUID.randomUUID()).fetch().rowsUpdated().block();
        APPROVALS.put(account,java.util.Map.of("tenantId","owned","accountId",account.toString(),"contractId",contract.toString(),"currency","USD","kind","CUSTOMER"));
        UUID enrollment = UUID.randomUUID();
        String body = "{\"enrollmentId\":\"" + enrollment + "\",\"kind\":\"CUSTOMER\"}";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/v1/tenants/owned/funds/accounts/" + account + "/enrollment"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token("owned", java.util.List.of(account), "funds:enroll","funds:tenant-bind"))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        var view = json.readTree(response.body());
        assertThat(view.path("mode").asText()).isEqualTo("ENROLLING");
        assertThat(view.path("contractId").asText()).isEqualTo(contract.toString());
        assertThat(database.sql("UPDATE account SET currency='EUR' WHERE account_id=:a").bind("a",account).fetch().rowsUpdated().block()).isZero();
        assertThatThrownBy(() -> database.sql("INSERT INTO account_balance(account_id,balance_type,balance_amount,as_of_datetime) VALUES (:a,'AVAILABLE',100,CURRENT_TIMESTAMP)")
                .bind("a",account).fetch().rowsUpdated().block()).hasMessageContaining("MANAGED_MONEY_WORKFLOW_REQUIRED");
    }
    @Test
    void generatedPublicContractNeverTreatsTheAuthenticatedPrincipalAsClientInput() throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var operation=json.readTree(response.body()).path("paths").path("/api/v1/tenants/{tenant}/funds/accounts/{accountId}/enrollment").path("get");
        assertThat(operation.isMissingNode()).isFalse();
        for(var parameter:operation.path("parameters")) assertThat(parameter.path("name").asText()).isNotEqualTo("jwt");
        assertThat(operation.path("security").toString()).contains("fundsBearer");
    }

    HttpResponse<String> call(String method,UUID account,String suffix,String body,String... scopes) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/owned/funds/accounts/"+account+suffix))
            .header("Content-Type","application/json").header("Authorization","Bearer "+token("owned",java.util.List.of(account),scopes))
            .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test
    void failedLedgerRestrictionDeliveryRetainsAConservativeDurablePendingControl() throws Exception {
        UUID account=UUID.randomUUID(),enrollment=UUID.randomUUID(),contract=UUID.randomUUID();
        database.sql("INSERT INTO account(account_id,contract_id,account_number,account_type,currency,open_date,account_status,branch_id) VALUES (:a,:c,:n,'CHECKING','USD',CURRENT_DATE,'OPEN',:b)")
                .bind("a",account).bind("c",contract).bind("n",account.toString()).bind("b",UUID.randomUUID()).fetch().rowsUpdated().block();
        APPROVALS.put(account,java.util.Map.of("tenantId","owned","accountId",account.toString(),"contractId",contract.toString(),"currency","USD","kind","CUSTOMER"));
        var begin=call("POST",account,"/enrollment",json.writeValueAsString(java.util.Map.of("enrollmentId",enrollment,"kind","CUSTOMER")),"funds:enroll","funds:tenant-bind");
        assertThat(begin.statusCode()).as(begin.body()).isEqualTo(201);
        var linked=call("POST",account,"/enrollment/link",json.writeValueAsString(java.util.Map.of("enrollmentId",enrollment)),"funds:link");
        assertThat(linked.statusCode()).as(linked.body()).isEqualTo(200);
        var request=json.writeValueAsString(java.util.Map.of("actionId",UUID.randomUUID(),"target","BLOCKED","expectedVersion",json.readTree(linked.body()).path("version").asLong()));
        var failed=call("POST",account,"/controls",request,"funds:control");
        assertThat(failed.statusCode()).as(failed.body()).isEqualTo(503);
        var retained=call("GET",account,"/enrollment",null,"funds:verify");
        assertThat(json.readTree(retained.body()).path("control").asText()).isEqualTo("BLOCKING");
        assertThat(call("POST",account,"/controls",request,"funds:control").statusCode()).isEqualTo(503);
        assertThat(database.sql("UPDATE account SET account_status='SUSPENDED' WHERE account_id=:a").bind("a",account).fetch().rowsUpdated().block()).isZero();
    }

    @Test
    void globalAccountExistenceCannotEstablishTenantEnrollmentAuthority() throws Exception {
        UUID account=UUID.randomUUID();
        database.sql("INSERT INTO account(account_id,contract_id,account_number,account_type,currency,open_date,account_status,branch_id) VALUES (:a,:c,:n,'CHECKING','USD',CURRENT_DATE,'OPEN',:b)")
                .bind("a",account).bind("c",UUID.randomUUID()).bind("n",account.toString()).bind("b",UUID.randomUUID()).fetch().rowsUpdated().block();
        var response=call("POST",account,"/enrollment",json.writeValueAsString(java.util.Map.of("enrollmentId",UUID.randomUUID(),"kind","CUSTOMER")),"funds:enroll","funds:tenant-bind");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    }
    @Test
    void managedAccountsRuntimeCannotUseMigrationOrBypassAuthority() {
        var role=database.sql("SELECT rolsuper,rolbypassrls,(SELECT pg_get_userbyid(relowner)=current_user FROM pg_class WHERE oid='account'::regclass) AS owner FROM pg_roles WHERE rolname=current_user")
                .map((r,m)->java.util.List.of(r.get("rolsuper",Boolean.class),r.get("rolbypassrls",Boolean.class),r.get("owner",Boolean.class))).one().block();
        assertThat(role).containsExactly(false,false,false);
    }
    UUID freshAccount() {
        UUID account=UUID.randomUUID(),contract=UUID.randomUUID();
        database.sql("INSERT INTO account(account_id,contract_id,account_number,account_type,currency,open_date,account_status,branch_id) VALUES(:a,:c,:n,'CHECKING','USD',CURRENT_DATE,'OPEN',:b)")
            .bind("a",account).bind("c",contract).bind("n",account.toString()).bind("b",UUID.randomUUID()).fetch().rowsUpdated().block();
        APPROVALS.put(account,java.util.Map.of("tenantId","owned","accountId",account.toString(),"contractId",contract.toString(),"currency","USD","kind","CUSTOMER"));
        return account;
    }
    void admin(String sql) throws Exception {
        try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var st=c.createStatement()) {st.execute(sql);}
    }
    long adminCount(String sql) throws Exception {
        try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var st=c.createStatement();var rows=st.executeQuery(sql)) {rows.next();return rows.getLong(1);}
    }
    @Test
    void enrollmentSerializesAnUnchangedParentMainBalanceWriteAndRechecksAfterWaiting() throws Exception {
        enrollmentSerializesChildWrite(false);
    }
    @Test
    void enrollmentSerializesNewSpaceHistoryEvenBeforePrivateSpaceLinkExists() throws Exception {
        enrollmentSerializesChildWrite(true);
    }
    void enrollmentSerializesChildWrite(boolean history) throws Exception {
        UUID account=freshAccount();
        database.sql("INSERT INTO account_space(account_id,space_name,space_type,balance) VALUES(:a,'Main','MAIN',0)").bind("a",account).fetch().rowsUpdated().block();
        UUID space=database.sql("SELECT account_space_id FROM account_space WHERE account_id=:a").bind("a",account).map((r,m)->r.get("account_space_id",UUID.class)).one().block();
        assertThat(space).isNotNull();
        String name="pause_enroll_"+account.toString().replace("-","");
        admin("CREATE FUNCTION public."+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.account_id='"+account+"'::uuid THEN PERFORM pg_sleep(2); END IF; RETURN NEW; END $$");
        admin("CREATE TRIGGER "+name+" BEFORE INSERT ON account_money_enrollment FOR EACH ROW EXECUTE FUNCTION public."+name+"()");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var enrollment=executor.submit(()->call("POST",account,"/enrollment",json.writeValueAsString(java.util.Map.of("enrollmentId",UUID.randomUUID(),"kind","CUSTOMER")),"funds:enroll","funds:tenant-bind"));
            long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(adminCount("SELECT count(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='PgSleep'")==0 && System.nanoTime()<deadline) Thread.sleep(10);
            assertThat(adminCount("SELECT count(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='PgSleep'")).isPositive();
            var child=executor.submit(()->{
                try(var connection=DriverManager.getConnection(DATABASE.getJdbcUrl(),"funds_runtime","owned-disposable-runtime-password");var statement=connection.createStatement()) {
                    statement.setQueryTimeout(8);
                    return statement.executeUpdate(history
                        ? "INSERT INTO space_transaction(account_space_id,amount,balance_after_transaction,transaction_datetime,transaction_type) VALUES('"+space+"',1,1,CURRENT_TIMESTAMP,'SYNTHETIC_TEST')"
                        : "UPDATE account_space SET balance=1 WHERE account_space_id='"+space+"'");
                }
            });
            var result=enrollment.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.statusCode()).as(result.body()).isEqualTo(201);
            assertThatThrownBy(()->child.get(10,java.util.concurrent.TimeUnit.SECONDS)).rootCause().hasMessageContaining("MANAGED_MONEY_WORKFLOW_REQUIRED");
            assertThat(adminCount("SELECT count(*) FROM account_space WHERE account_id='"+account+"' AND balance<>0")).isZero();
            assertThat(adminCount("SELECT count(*) FROM space_transaction WHERE account_space_id='"+space+"'")).isZero();
        } finally {
            admin("DROP TRIGGER "+name+" ON account_money_enrollment");admin("DROP FUNCTION public."+name+"()");
        }
    }


    @Test
    void historyInsertionThatHoldsTheGateCanFinishBeforeEnrollmentEligibilityIsRead() throws Exception { childFirst(false); }
    @Test
    void accountBalanceInsertionHoldingTheGateCannotDeadlockEnrollmentParentLock() throws Exception { childFirst(true); }
    void childFirst(boolean accountBalance) throws Exception {
        UUID account=freshAccount(),space=UUID.randomUUID();
        database.sql("INSERT INTO account_space(account_space_id,account_id,space_name,space_type,balance) VALUES(:s,:a,'Main','MAIN',0)").bind("s",space).bind("a",account).fetch().rowsUpdated().block();
        assertThat(call("GET",account,"/enrollment",null,"funds:verify").statusCode()).isEqualTo(404);
        String name="zz_child_pause_"+account.toString().replace("-","");
        String table=accountBalance?"account_balance":"space_transaction";
        admin("CREATE FUNCTION public."+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "+(accountBalance?"NEW.account_id='"+account+"'":"NEW.account_space_id='"+space+"'")+"::uuid THEN PERFORM pg_sleep(4); END IF; RETURN NEW; END $$");
        admin("CREATE TRIGGER "+name+" BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION public."+name+"()");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var child=executor.submit(()->{
                try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),"funds_runtime","owned-disposable-runtime-password");var st=c.createStatement()) {
                    st.setQueryTimeout(10);
                    return st.executeUpdate(accountBalance?"INSERT INTO account_balance(account_id,balance_type,balance_amount,as_of_datetime) VALUES('"+account+"','AVAILABLE',1,CURRENT_TIMESTAMP)":"INSERT INTO space_transaction(account_space_id,amount,balance_after_transaction,transaction_datetime,transaction_type) VALUES('"+space+"',1,1,CURRENT_TIMESTAMP,'SYNTHETIC_TEST')");
                }
            });
            long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(adminCount("SELECT count(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='PgSleep'")==0&&System.nanoTime()<deadline) Thread.sleep(10);
            assertThat(adminCount("SELECT count(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='PgSleep'")).isPositive();
            var enrollment=executor.submit(()->call("POST",account,"/enrollment",json.writeValueAsString(java.util.Map.of("enrollmentId",UUID.randomUUID(),"kind","CUSTOMER")),"funds:enroll","funds:tenant-bind"));
            deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
            while(adminCount("SELECT count(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='advisory'")==0&&System.nanoTime()<deadline) Thread.sleep(10);
            assertThat(adminCount("SELECT count(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='advisory'")).isPositive();
            assertThat(child.get(12,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
            var result=enrollment.get(12,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.statusCode()).as(result.body()).isEqualTo(409);
            assertThat(adminCount("SELECT count(*) FROM account_money_enrollment WHERE account_id='"+account+"'")).isZero();
        } finally {admin("DROP TRIGGER "+name+" ON "+table);admin("DROP FUNCTION public."+name+"()");}
    }


    @Test
    void aHiddenAccountControlActionCannotBecomeAnEmptyOrDuplicateSuccess() throws Exception {
        UUID a=freshAccount(),b=freshAccount(),action=UUID.randomUUID();
        for(UUID account:java.util.List.of(a,b)) {
            UUID enrollment=UUID.randomUUID();
            assertThat(call("POST",account,"/enrollment",json.writeValueAsString(java.util.Map.of("enrollmentId",enrollment,"kind","CUSTOMER")),"funds:enroll","funds:tenant-bind").statusCode()).isEqualTo(201);
            assertThat(call("POST",account,"/enrollment/link",json.writeValueAsString(java.util.Map.of("enrollmentId",enrollment)),"funds:link").statusCode()).isEqualTo(200);
        }
        String command=json.writeValueAsString(java.util.Map.of("actionId",action,"target","BLOCKED","expectedVersion",1));
        assertThat(call("POST",a,"/controls",command,"funds:control").statusCode()).isEqualTo(503);
        var hidden=call("POST",b,"/controls",command,"funds:control");assertThat(hidden.statusCode()).as(hidden.body()).isEqualTo(409);
        var retained=call("GET",b,"/enrollment",null,"funds:verify");assertThat(json.readTree(retained.body()).path("control").asText()).isEqualTo("ACTIVE");
        assertThat(adminCount("SELECT COUNT(*) FROM account_money_enrollment WHERE account_id='"+b+"' AND pending_action_id IS NOT NULL")).isZero();
    }
    @Test
    void signedEnrollmentTupleMustMatchActualCanonicalAccountCurrencyContractAndKind() throws Exception {
        UUID account=freshAccount();var original=APPROVALS.get(account);
        for(String key:java.util.List.of("contractId","currency","kind")) {
            var wrong=new java.util.HashMap<>(original);wrong.put(key,key.equals("currency")?"EUR":key.equals("kind")?"BANK_CONTROL":UUID.randomUUID().toString());APPROVALS.put(account,wrong);
            var result=call("POST",account,"/enrollment",json.writeValueAsString(java.util.Map.of("enrollmentId",UUID.randomUUID(),"kind","CUSTOMER")),"funds:enroll","funds:tenant-bind");
            assertThat(result.statusCode()).as(result.body()).isEqualTo(403);
        }
        APPROVALS.put(account,original);
        assertThat(adminCount("SELECT COUNT(*) FROM account_money_enrollment WHERE account_id='"+account+"'")).isZero();
    }

    static void prepareRuntimeRole() {
        try(var connection=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var statement=connection.createStatement()) {
            statement.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='funds_runtime') THEN CREATE ROLE funds_runtime LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'owned-disposable-runtime-password'; END IF; IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='firefly_funds_policy_reader') THEN CREATE ROLE firefly_funds_policy_reader NOLOGIN NOSUPERUSER NOBYPASSRLS; END IF; IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='firefly_funds_policy_writer') THEN CREATE ROLE firefly_funds_policy_writer NOLOGIN NOSUPERUSER NOBYPASSRLS; END IF; END $$");
            statement.execute("GRANT USAGE ON SCHEMA public TO funds_runtime");
            statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO funds_runtime");
            statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO funds_runtime");
        } catch(Exception error) { throw new IllegalStateException(error); }
    }

}
