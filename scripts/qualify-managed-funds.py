#!/usr/bin/env python3
"""Run only synthetic FND-01 journeys against two actual executable JARs and owned PostgreSQL.

No third-party Python packages. Requires Docker, OpenSSL and JDK 25. Every database,
role, identity and account below is created for this disposable loopback qualification.
"""
import argparse
import shutil
import re
import base64
import concurrent.futures
import hashlib
import http.server
import json
import os
import pathlib
import secrets
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from contextlib import ExitStack

ROOT = None
JAVA = None
DOCKER = None
ISSUER = 'https://owned-funds-peer.invalid'
APPROVALS = {}
ASSERTIONS = []
TOKEN_REQUESTS = []
DROP_CONTROL_REPLY = False
PEER_PORTS = {}
PROCESSES = {}
LOG_FILES = []


def check(condition, name, detail=''):
    if not condition:
        raise AssertionError(name + ': ' + str(detail)[:1000])
    ASSERTIONS.append(name)
    print('PASS', name, flush=True)


def run(command, **kwargs):
    return subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs).stdout


def free_port():
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


def b64(value):
    return base64.urlsafe_b64encode(value).rstrip(b'=').decode()


def token(audience, accounts, scopes, subject='synthetic-human', expiry=300):
    claims = {'iss': ISSUER, 'aud': audience, 'sub': subject, 'tenant_id': 'owned',
              'scope': ' '.join(scopes), 'funds_accounts': list(accounts),
              'funds_enrollments': [APPROVALS[a] for a in accounts if a in APPROVALS],
              'iat': int(time.time()), 'exp': int(time.time()) + expiry}
    value = b64(json.dumps({'alg': 'RS256', 'kid': 'owned-peer'}).encode()) + '.' + b64(json.dumps(claims).encode())
    signature = run(['openssl', 'dgst', '-sha256', '-sign', str(KEY)], input=value.encode())
    return value + '.' + b64(signature)


def request(service, method, path, body=None, accounts=(), scopes=(), expected=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request('http://127.0.0.1:' + str(PEER_PORTS[service]) + path,
                                 data=data, method=method, headers={'Content-Type': 'application/json'})
    if scopes:
        req.add_header('Authorization', 'Bearer ' + token(service + '-funds', accounts, scopes))
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status, value = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, value = error.code, error.read()
    parsed = json.loads(value) if value else None
    if expected is not None:
        check(status == expected, method + ' ' + service + path + ' status=' + str(expected), (status, parsed))
    return status, parsed


class Fixture(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        self.send_json(200, {'keys': [PUBLIC_KEY]})

    def send_json(self, code, value):
        raw = json.dumps(value).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self):
        global DROP_CONTROL_REPLY
        raw = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        if self.path == '/token':
            form = urllib.parse.parse_qs(raw.decode())
            client = form.get('client_id', [''])[0]
            secret = form.get('client_secret', [''])[0]
            auth = self.headers.get('Authorization', '')
            if auth.startswith('Basic '):
                client, secret = base64.b64decode(auth[6:]).decode().split(':', 1)
            if client not in ('ledger-peer', 'accounts-peer') or secret != CLIENT_SECRET:
                return self.send_json(401, {'error': 'invalid_client'})
            target = 'accounts' if client == 'ledger-peer' else 'ledger'
            scopes = form.get('scope', [''])[0].split()
            allowed = ['funds:verify'] if target == 'accounts' else ['funds:control']
            if not scopes or not set(scopes).issubset(allowed):
                return self.send_json(403, {'error': 'invalid_scope'})
            TOKEN_REQUESTS.append((client, target, scopes))
            return self.send_json(200, {'token_type': 'Bearer', 'expires_in': 60,
                                       'access_token': token(target + '-funds', list(APPROVALS), scopes, 'synthetic-service-' + client, 60)})
        if self.path.startswith('/ledger/'):
            path = self.path[len('/ledger'):]
            req = urllib.request.Request('http://127.0.0.1:' + str(PEER_PORTS['ledger']) + path,
                data=raw, method='POST', headers={'Content-Type': 'application/json', 'Authorization': self.headers.get('Authorization', '')})
            try:
                with urllib.request.urlopen(req, timeout=15) as response:
                    code, value = response.status, response.read()
            except urllib.error.HTTPError as error:
                code, value = error.code, error.read()
            if DROP_CONTROL_REPLY and code == 200 and path.endswith('/controls'):
                DROP_CONTROL_REPLY = False
                self.close_connection = True
                self.connection.shutdown(socket.SHUT_RDWR)
                return self.connection.close()
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(value)))
            self.end_headers()
            return self.wfile.write(value)
        self.send_json(404, {'error': 'not_found'})


def psql(container, sql, database='postgres'):
    return run(DOCKER + ['exec', '-i', container, 'psql', '-h', '127.0.0.1', '-U', 'postgres', '-d', database, '-v', 'ON_ERROR_STOP=1', '-At'], input=sql.encode()).decode().strip()


def boot(service):
    jar = JARS[service]
    log = (ROOT / ('fnd-peer-' + service + '-boot-' + str(time.time_ns()) + '.log')).open('w')
    LOG_FILES.append(str(log.name))
    env = dict(os.environ, DB_HOST='127.0.0.1', DB_PORT=str(DB_PORTS[service]), DB_NAME='fnd_' + service,
        DB_USERNAME='funds_runtime', DB_PASSWORD=RUNTIME_PASSWORD, DB_SSL_MODE='disable',
        SERVER_ADDRESS='127.0.0.1', SERVER_PORT=str(PEER_PORTS[service]), SPRING_FLYWAY_USER='postgres', SPRING_FLYWAY_PASSWORD=BOOTSTRAP_PASSWORD,
        FUNDS_SECURITY_ISSUER=ISSUER, FUNDS_SECURITY_AUDIENCE=service + '-funds', FUNDS_SECURITY_JWK_SET_URI=FIXTURE_URL + '/jwks',
        FUNDS_CURRENCY_POLICY_VERSION='owned-peer-qualification-v1', FIREFLY_EDA_ENABLED='false',
        LOGGING_LEVEL_ROOT='WARN', LOGGING_LEVEL_IO_FIREFLYSOFTWARE='INFO', LOGGING_LEVEL_ORG_FIREFLYFRAMEWORK='WARN')
    peer = 'accounts' if service == 'ledger' else 'ledger'
    prefix = 'FUNDS_' + peer.upper() + '_'
    env.update({prefix+'BASE_URL': ('http://127.0.0.1:' + str(PEER_PORTS[peer])) if service == 'ledger' else FIXTURE_URL + '/ledger',
                prefix+'TOKEN_URI': FIXTURE_URL+'/token', prefix+'CLIENT_ID': service+'-peer', prefix+'CLIENT_SECRET': CLIENT_SECRET,
                prefix+'SCOPES': 'funds:verify' if service == 'ledger' else 'funds:control', prefix+'ALLOW_LOOPBACK_HTTP': 'true'})
    process = subprocess.Popen([JAVA, '-jar', str(jar)], env=env, stdout=log, stderr=subprocess.STDOUT)
    PROCESSES[service] = (process, log)
    end = time.monotonic()+90
    while time.monotonic()<end:
        if process.poll() is not None:
            raise RuntimeError(service+' exited: '+str(log.name))
        try:
            urllib.request.urlopen('http://127.0.0.1:'+str(PEER_PORTS[service])+'/v3/api-docs', timeout=1).close()
            return
        except (OSError, urllib.error.HTTPError):
            time.sleep(.2)
    raise RuntimeError(service+' startup timeout: '+str(log.name))


def stop(service):
    process, log = PROCESSES.pop(service)
    process.terminate()
    try:
        process.wait(15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(5)
    log.close()


def apath(account):
    return '/api/v1/tenants/owned/funds/accounts/'+account


def funds(account):
    return request('ledger', 'GET', apath(account), accounts=[account], scopes=['funds:read'], expected=200)[1]


def accounts_view(account):
    return request('accounts', 'GET', apath(account)+'/enrollment', accounts=[account], scopes=['funds:verify'], expected=200)[1]


def control(account, state, expected=200, command=None):
    command = command or {'actionId': str(uuid.uuid4()), 'target': state, 'expectedVersion': accounts_view(account)['version']}
    return request('accounts', 'POST', apath(account)+'/controls', command, [account], ['funds:control'], expected)[1], command


def reserve(source, destination, amount, ttl=300, deadline=None):
    return {'operationId': str(uuid.uuid4()), 'sourceAccountId': source, 'destinationAccountId': destination,
            'amount': amount, 'currency': 'USD', 'ttlSeconds': ttl, 'notAfterEpochMillis': deadline or int(time.time()*1000)+ttl*1000}


def qualify():
    global DROP_CONTROL_REPLY
    for service in ['accounts','ledger']:
        boot(service)
    roles={}
    for service in ['accounts','ledger']:
        roles[service]=psql(CONTAINERS[service], "SELECT NOT rolsuper AND NOT rolbypassrls FROM pg_roles WHERE rolname='funds_runtime'; SELECT bool_and(relrowsecurity AND relforcerowsecurity) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relrowsecurity; SELECT NOT has_schema_privilege('funds_runtime','funds_private','USAGE'); SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND relrowsecurity AND pg_get_userbyid(relowner)='funds_runtime';", 'fnd_'+service).splitlines()
        check(roles[service]==['t','t','t','0'],service+' actual databases enforce nonprivileged, nonowner forced RLS',roles[service])
    data=[]
    for i,kind in enumerate(['BANK_CONTROL','CUSTOMER','CUSTOMER','CUSTOMER']):
        contract=str(uuid.uuid4())
        _, account=request('accounts','POST','/api/v1/accounts',{'contractId':contract,'accountNumber':'SYNTHETIC-'+str(uuid.uuid4()),'accountType':'CHECKING','currency':'USD','openDate':time.strftime('%Y-%m-%d'),'accountStatus':'OPEN','branchId':str(uuid.uuid4())},expected=201)
        account_id=account['accountId'];enrollment=str(uuid.uuid4())
        APPROVALS[account_id]={'tenantId':'owned','accountId':account_id,'contractId':contract,'currency':'USD','kind':kind}
        data.append((account_id,enrollment,contract,kind))
    bank,source,destination,zero=[row[0] for row in data]
    activation={}
    for account,enrollment,contract,kind in data:
        request('ledger','POST',apath(account)+'/enrollment',{'enrollmentId':enrollment,'contractId':contract,'currency':'USD','kind':kind},[account],['funds:enroll','funds:tenant-bind','funds:bank-enroll'],201)
        command={'actionId':str(uuid.uuid4()),'state':'ACTIVE'};activation[account]=command
        if account==source:
            request('ledger','POST',apath(account)+'/controls',command,[account],['funds:control'],503)
            check(funds(account)['state']=='BLOCKED','actual accounts SDK prevents activation before committed accounts link')
        request('accounts','POST',apath(account)+'/enrollment',{'enrollmentId':enrollment,'kind':kind},[account],['funds:enroll','funds:tenant-bind','funds:bank-enroll'],201)
        if account==source:
            request('ledger','POST',apath(account)+'/controls',command,[account],['funds:control'],409)
        request('accounts','POST',apath(account)+'/enrollment/link',{'enrollmentId':enrollment},[account],['funds:link'],200)
        request('ledger','POST',apath(account)+'/controls',command,[account],['funds:control'],200)
    request('ledger','POST','/api/v1/tenants/owned/funds/funding',{'operationId':str(uuid.uuid4()),'bankControlAccountId':bank,'customerAccountId':source,'amount':100,'currency':'USD'},[bank,source],['funds:fund'],201)
    intent=reserve(source,destination,10)
    _, held=request('ledger','POST','/api/v1/tenants/owned/funds/reservations',intent,[source,destination],['funds:reserve'],201)
    check(funds(source)['held']==10,'real privileged balanced funding and persisted hold')
    stop('ledger');boot('ledger')
    _, recovered=request('ledger','GET','/api/v1/tenants/owned/funds/reservations/by-operation/'+intent['operationId'],accounts=[source,destination],scopes=['funds:read'],expected=200)
    check(recovered['reservationId']==held['reservationId'],'lost create response recovered by operation after executable restart')
    action={'actionId':str(uuid.uuid4()),'action':'COMMIT'}
    path='/api/v1/tenants/owned/funds/reservations/'+held['reservationId']+'/actions'
    request('ledger','POST',path,action,[source,destination],['funds:commit'],200)
    request('ledger','POST',path,action,[source,destination],['funds:commit'],200)
    check(funds(source)['booked']==90 and funds(destination)['booked']==10,'actual commit replay moves booked funds once')
    pair_intents=[reserve(source,destination,1),reserve(destination,source,1)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        pending_holds=[executor.submit(request,'ledger','POST','/api/v1/tenants/owned/funds/reservations',item,[source,destination],['funds:reserve']) for item in pair_intents]
        pair_holds=[future.result() for future in pending_holds]
        check(all(item[0]==201 for item in pair_holds),'actual opposite-pair reserve calls both commit without deadlock',pair_holds)
        pending_commits=[executor.submit(request,'ledger','POST','/api/v1/tenants/owned/funds/reservations/'+item[1]['reservationId']+'/actions',{'actionId':str(uuid.uuid4()),'action':'COMMIT'},[source,destination],['funds:commit']) for item in pair_holds]
        check(all(future.result()[0]==200 for future in pending_commits),'actual opposite-pair commit calls both finish without deadlock')
    check(funds(source)['booked']==90 and funds(destination)['booked']==10 and funds(source)['held']==0 and funds(destination)['held']==0,'opposite actual transfers conserve both customer balances')
    DROP_CONTROL_REPLY=True
    _, pending=control(source,'BLOCKED',503)
    check(accounts_view(source)['control']=='BLOCKING' and funds(source)['state']=='BLOCKED','lost SDK control reply retains conservative pending accounts stage')
    stop('accounts');boot('accounts')
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        repeated=[executor.submit(request,'accounts','POST',apath(source)+'/controls',pending,[source],['funds:control']) for _ in range(2)]
        responses=[future.result() for future in repeated]
    check([result[0] for result in responses]==[200,200],'simultaneous retries of one durable control finish idempotently',responses)
    check(accounts_view(source)['control']=='BLOCKED','pending SDK control recovers after accounts executable restart')
    control(source,'CLOSED',409)
    check(accounts_view(source)['control']=='BLOCKED' and funds(source)['state']=='BLOCKED','definitive nonzero close rejection safely recovers blocked authority')
    control(source,'ACTIVE')
    request('ledger','POST',path,{'actionId':str(uuid.uuid4()),'action':'REVERSE'},[source,destination],['funds:reverse'],200)
    check(funds(source)['booked']==100 and funds(destination)['booked']==0,'full linked reversal restores exact funds through real core')
    closed_zero,closed_command=control(zero,'CLOSED')
    control(zero,'CLOSED',command=closed_command)
    control(zero,'ACTIVE',expected=409)
    check(accounts_view(zero)['control']=='CLOSED' and funds(zero)['state']=='CLOSED','zero account closure integrates both actual cores')
    race_intent=reserve(source,destination,3)
    _,race_hold=request('ledger','POST','/api/v1/tenants/owned/funds/reservations',race_intent,[source,destination],['funds:reserve'],201)
    freeze={'actionId':str(uuid.uuid4()),'target':'BLOCKED','expectedVersion':accounts_view(source)['version']}
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        freezing=executor.submit(request,'accounts','POST',apath(source)+'/controls',freeze,[source],['funds:control'])
        committing=executor.submit(request,'ledger','POST','/api/v1/tenants/owned/funds/reservations/'+race_hold['reservationId']+'/actions',{'actionId':str(uuid.uuid4()),'action':'COMMIT'},[source,destination],['funds:commit'])
        freeze_result=freezing.result();commit_result=committing.result()
    check(freeze_result[0]==200 and commit_result[0] in [200,409],'real accounts latch and ledger commit have one safe serialization', [freeze_result,commit_result])
    check(funds(source)['state']=='BLOCKED','acknowledged account freeze has committed ledger latch')
    if commit_result[0]==409:
        request('ledger','POST','/api/v1/tenants/owned/funds/reservations/'+race_hold['reservationId']+'/actions',{'actionId':str(uuid.uuid4()),'action':'RELEASE'},[source,destination],['funds:release'],200)
    control(source,'ACTIVE')
    if commit_result[0]==200:
        request('ledger','POST','/api/v1/tenants/owned/funds/reservations/'+race_hold['reservationId']+'/actions',{'actionId':str(uuid.uuid4()),'action':'REVERSE'},[source,destination],['funds:reverse'],200)
    check(funds(source)['booked']==100 and funds(destination)['booked']==0 and funds(source)['held']==0,'freeze/commit recovery preserves exact money')
    expiry=reserve(source,destination,2,ttl=1)
    request('ledger','POST','/api/v1/tenants/owned/funds/reservations',expiry,[source,destination],['funds:reserve'],201)
    time.sleep(1.2)
    request('ledger','POST','/api/v1/tenants/owned/funds/reservations/expiry',{'limit':10},[source,destination],['funds:expire'],200)
    check(funds(source)['held']==0,'durable bounded expiry worker releases held funds')
    absent=reserve(source,destination,2,ttl=300,deadline=int(time.time()*1000)-1)
    _, receipt=request('ledger','POST','/api/v1/tenants/owned/funds/reservations/reconciliation',absent,[source,destination],['funds:reconcile'],200)
    check(receipt['outcome']=='ABSENT_CLOSED' and receipt.get('reservation') is None,'authoritative no-effect receipt contains no fabricated reservation')
    stop('ledger');boot('ledger')
    _, replay=request('ledger','POST','/api/v1/tenants/owned/funds/reservations/reconciliation',absent,[source,destination],['funds:reconcile'],200)
    check(receipt['receiptId']==replay['receiptId'],'no-effect receipt remains stable after executable restart')
    request('ledger','POST','/api/v1/tenants/owned/funds/reservations',absent,[source,destination],['funds:reserve'],409)
    check(funds(source)['booked']==100 and funds(source)['held']==0 and funds(destination)['booked']==0 and funds(bank)['booked']==-100,'final exact conservation and zero held money')
    check(any(item[0]=='ledger-peer' for item in TOKEN_REQUESTS) and any(item[0]=='accounts-peer' for item in TOKEN_REQUESTS),'both production peer adapters acquired separate client credentials tokens')
    return roles


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accounts-jar',type=pathlib.Path,required=True)
    parser.add_argument('--ledger-jar',type=pathlib.Path,required=True)
    parser.add_argument('--output-dir',type=pathlib.Path,required=True)
    parser.add_argument('--java',default=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else shutil.which('java'))
    parser.add_argument('--docker-host',default=os.environ.get('DOCKER_HOST'))
    args=parser.parse_args()
    if not args.java or not shutil.which('docker'):
        parser.error('JDK 25 and a local Docker CLI are required.')
    for jar in [args.accounts_jar,args.ledger_jar]:
        if not jar.is_file(): parser.error('Executable JAR does not exist: '+str(jar))
    ROOT=args.output_dir.resolve();ROOT.mkdir(parents=True,exist_ok=True)
    (ROOT/'reports').mkdir(exist_ok=True)
    JAVA=args.java
    DOCKER=[shutil.which('docker')]+(['--host',args.docker_host] if args.docker_host else [])
    CONTAINERS={};DB_PORTS={};JARS={'accounts':args.accounts_jar.resolve(),'ledger':args.ledger_jar.resolve()};KEY=None
    BOOTSTRAP_PASSWORD=secrets.token_urlsafe(24);RUNTIME_PASSWORD=secrets.token_urlsafe(24);CLIENT_SECRET=secrets.token_urlsafe(24)
    fixture=None;report={'status':'FAILED'};artifact_manifest={}
    try:
        with tempfile.TemporaryDirectory(prefix='owned-fnd-peer-') as temporary:
            for service,path in list(JARS.items()):
                frozen=pathlib.Path(temporary)/(service+'.jar');shutil.copy2(path,frozen)
                digest=hashlib.sha256(frozen.read_bytes()).hexdigest()
                if digest!=hashlib.sha256(path.read_bytes()).hexdigest(): raise RuntimeError('Executable changed while copying: '+service)
                artifact_manifest[service]={'path':str(path),'sha256':digest}
                JARS[service]=frozen
            KEY=pathlib.Path(temporary)/'signing.pem'
            run(['openssl','genrsa','-out',str(KEY),'2048'])
            os.chmod(KEY,0o600)
            modulus=run(['openssl','rsa','-in',str(KEY),'-noout','-modulus']).decode().strip().split('=',1)[1]
            PUBLIC_KEY={'kty':'RSA','alg':'RS256','use':'sig','kid':'owned-peer','n':b64(bytes.fromhex(modulus)),'e':'AQAB'}
            fixture=http.server.ThreadingHTTPServer(('127.0.0.1',0),Fixture)
            FIXTURE_URL='http://127.0.0.1:'+str(fixture.server_port)
            threading.Thread(target=fixture.serve_forever,daemon=True).start()
            for service in ['accounts','ledger']:
                PEER_PORTS[service]=free_port()
                name='owned-fnd-'+service+'-'+uuid.uuid4().hex[:8]
                container=run(DOCKER+['run','--rm','-d','--name',name,'-p','127.0.0.1::5432','-e','POSTGRES_PASSWORD='+BOOTSTRAP_PASSWORD,'postgres:17-alpine']).decode().strip()
                if not re.fullmatch('[a-f0-9]{64}',container): raise RuntimeError('Unexpected owned container identifier')
                CONTAINERS[service]=container
                DB_PORTS[service]=int(run(DOCKER+['port',container,'5432/tcp']).decode().strip().rsplit(':',1)[1])
                for attempt in range(100):
                    try:
                        psql(container,'SELECT 1;');break
                    except subprocess.CalledProcessError:
                        time.sleep(.1)
                psql(container,'CREATE DATABASE fnd_'+service+';')
                psql(container,"CREATE ROLE funds_runtime LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '"+RUNTIME_PASSWORD+"'; CREATE ROLE firefly_funds_policy_reader NOLOGIN NOSUPERUSER NOBYPASSRLS; CREATE ROLE firefly_funds_policy_writer NOLOGIN NOSUPERUSER NOBYPASSRLS; REVOKE CREATE ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO funds_runtime; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO funds_runtime; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO funds_runtime;",'fnd_'+service)
            report['roles']=qualify()
            report['status']='PASS'
    except BaseException as error:
        report['error']=type(error).__name__+': '+str(error)
        raise
    finally:
        for service in list(PROCESSES):
            stop(service)
        if fixture:
            fixture.shutdown();fixture.server_close()
        for container in CONTAINERS.values():
            subprocess.run(DOCKER+['stop','--time','5',container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        report.update(assertions=len(ASSERTIONS),checks=ASSERTIONS,logs=LOG_FILES,
            artifacts=artifact_manifest,
            token_requests=[{'client':client,'audience':target+'-funds','scopes':scopes} for client,target,scopes in TOKEN_REQUESTS])
        (ROOT/'reports/fnd-01-peer-executables-result.json').write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps({key:report[key] for key in ['status','assertions']},indent=2),flush=True)
