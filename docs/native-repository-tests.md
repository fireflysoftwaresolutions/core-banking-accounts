# Owned PostgreSQL repository tests

The three maintained `*NativeIT` classes exercise real Spring Data proxies. They are not selected by the normal Surefire test pattern. Run each class against a **separate new disposable PostgreSQL17 cluster**; never point them at an existing database. They insert/delete synthetic records. These focused tests do not start an HTTP server, producer or external financial owner.

Requirements: Java25, Maven with this project's resolved dependencies, Python3, and PostgreSQL17 `initdb`, `pg_ctl`, `psql` on `PATH`. Supply Maven authentication through your own settings if needed; no private settings file is shipped. Do not enable shell tracing or print environment secrets. Use the project's normal full `mvn verify` separately for original producer/SDK qualification.

## Create one owned cluster

Choose one row for each fresh run:

| Test class | `NATIVE_ROLE` | `NATIVE_MODULE` |
| --- | --- | --- |
| `AccountsOrdinaryRepositoryNativeIT` | `accounts_queries_runtime` | `core-banking-accounts-models` |
| `AccountsFundsPersistenceNativeIT` | `accounts_funds_runtime` | `core-banking-accounts-core` |
| `AssetPriceOwnerNativeIT` | `accounts_asset_runtime` | `core-banking-accounts-web` |

From the repository root, set `NATIVE_TEST`, `NATIVE_ROLE` and `NATIVE_MODULE` to the selected row. The commands below use database `postgres`, matching the existing test classes. Keep their cluster path for ownership verification and cleanup; do not substitute a shared cluster.

```bash
export NATIVE_TEST=AssetPriceOwnerNativeIT
export NATIVE_ROLE=accounts_asset_runtime
export NATIVE_MODULE=core-banking-accounts-web
export NATIVE_ROOT="$(mktemp -d /tmp/accounts-native.XXXXXX)"
export NATIVE_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')"
export ACCOUNTS_NATIVE_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
initdb -D "$NATIVE_ROOT/data" -U accounts_native_owner \
  --auth-local=trust --auth-host=scram-sha-256 --no-locale --encoding=UTF8
pg_ctl -D "$NATIVE_ROOT/data" -l "$NATIVE_ROOT/postgres.log" \
  -o "-k $NATIVE_ROOT -p $NATIVE_PORT -h 127.0.0.1 -c max_connections=12 -c shared_buffers=16MB" -w start
psql -X -h "$NATIVE_ROOT" -p "$NATIVE_PORT" -U accounts_native_owner -d postgres -v ON_ERROR_STOP=1 <<'SQL'
\getenv native_role NATIVE_ROLE
\getenv native_password ACCOUNTS_NATIVE_PASSWORD
CREATE ROLE firefly_funds_policy_reader NOLOGIN NOSUPERUSER NOBYPASSRLS;
CREATE ROLE firefly_funds_policy_writer NOLOGIN NOSUPERUSER NOBYPASSRLS;
CREATE ROLE :"native_role" LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD :'native_password';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"native_role";
SQL
python3 - <<'PY'
import os, pathlib, subprocess
paths = sorted(pathlib.Path('core-banking-accounts-models/src/main/resources/db/migration').glob('V*.sql'),
               key=lambda p: int(p.name.split('__')[0][1:]))
assert len(paths) == 16
for path in paths:
    subprocess.run(['psql', '-X', '-h', os.environ['NATIVE_ROOT'], '-p', os.environ['NATIVE_PORT'],
                    '-U', 'accounts_native_owner', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1',
                    '--single-transaction', '-f', str(path)], check=True)
PY
psql -X -h "$NATIVE_ROOT" -p "$NATIVE_PORT" -U accounts_native_owner -d postgres -v ON_ERROR_STOP=1 <<'SQL'
\getenv native_role NATIVE_ROLE
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO :"native_role";
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO :"native_role";
SELECT NOT rolsuper AND NOT rolbypassrls
  AND NOT has_schema_privilege(:'native_role','funds_private','USAGE')
FROM pg_roles WHERE rolname=:'native_role';
SQL
```

The final query must return true. The native runtime is a separate nonowner with no private-schema access or private policy-role membership; keep the migration owner separate. These grants apply only to the newly created synthetic fixture database.

## Ordinary-query fixture only

Only `AccountsOrdinaryRepositoryNativeIT` needs this seed, after the original migrations. Its asset rows are reset by the test itself. Other classes create their own records.

```bash
psql -X -h "$NATIVE_ROOT" -p "$NATIVE_PORT" -U accounts_native_owner -d postgres -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO account(account_id,contract_id,account_number,account_type,currency,open_date,account_status,branch_id) VALUES
('00000000-0000-0000-0000-000000000001',gen_random_uuid(),'owned-query-one','CHECKING','USD',CURRENT_DATE,'OPEN',gen_random_uuid()),
('00000000-0000-0000-0000-000000000002',gen_random_uuid(),'owned-query-zero','CHECKING','USD',CURRENT_DATE,'OPEN',gen_random_uuid());
INSERT INTO account_space(account_space_id,account_id,space_name,space_type,balance) VALUES
('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001','owned-query-one','MAIN',12.3456),
('00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000002','owned-query-zero','MAIN',0);
INSERT INTO space_transaction(account_space_id,amount,balance_after_transaction,transaction_datetime,transaction_type) VALUES
('00000000-0000-0000-0000-000000000011',1.2345,0,'2026-01-01','DEPOSIT'),
('00000000-0000-0000-0000-000000000011',3.0001,0,'2026-01-02','DEPOSIT'),
('00000000-0000-0000-0000-000000000011',-0.0001,0,'2026-01-01','WITHDRAWAL'),
('00000000-0000-0000-0000-000000000011',-2.3456,0,'2026-01-01 12:00','WITHDRAWAL'),
('00000000-0000-0000-0000-000000000011',-1.0000,0,'2026-01-02','WITHDRAWAL'),
('00000000-0000-0000-0000-000000000011',0,0,'2026-01-01 12:00','DEPOSIT'),
('00000000-0000-0000-0000-000000000011',100,0,'2025-12-31 23:59:59.999999','DEPOSIT'),
('00000000-0000-0000-0000-000000000011',200,0,'2026-01-02 00:00:00.000001','DEPOSIT');
SQL
```

## Execute and stop owned resources

```bash
mvn -B -ntp -DskipOpenApiGen=true -Dopenapi.gen.skip=true \
  -Dtest="$NATIVE_TEST" -Dsurefire.failIfNoSpecifiedTests=false \
  -Daccounts.native.port="$NATIVE_PORT" -pl "$NATIVE_MODULE" -am test
```

Add your own `-s` settings and `-Dmaven.repo.local` options if needed. Inspect the actual selected test report: expected class counts are8 ordinary-query,10 funds-persistence and9 asset-auditing tests. These focused commands suppress the unrelated producer, not the native tests. The asset class uses a controlled projected trusted principal; signed JWT admission and actual ledger transport require the separate original application/SDK gate.

Always stop the exact owned cluster, including after failed tests:

```bash
pg_ctl -D "$NATIVE_ROOT/data" -m fast -w stop
pg_ctl -D "$NATIVE_ROOT/data" status
python3 - <<'PY'
import os, pathlib, socket
assert not (pathlib.Path(os.environ['NATIVE_ROOT'])/'data/postmaster.pid').exists()
with socket.socket() as probe:
    assert probe.connect_ex(('127.0.0.1',int(os.environ['NATIVE_PORT']))) != 0
print('Owned PID file and loopback port are absent')
PY
unset ACCOUNTS_NATIVE_PASSWORD
```

`pg_ctl status` returns3 when stopped. Preserve the owned logs/data until reviewed; no data deletion is part of these instructions. This document describes source prerequisites and commands; it is not a new runtime qualification result.
