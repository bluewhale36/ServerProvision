#!/usr/bin/env python3
"""
E1.5 · E2-2 CP5 — 모조 Redfish BMC (자가서명 TLS).

실측(E0-4)이 확정한 계약의 최소 재현: Basic 인증, ComputerSystem.Reset(202 + Task Location),
TaskService 판독, PowerState. 세 가지 응답 모드로 실측 시나리오를 재연한다:
  normal      : Reset 이 즉시 상태를 바꾼다
  on-noop     : Reset(On) 이 202 를 주지만 전원이 켜지지 않는다(실측 실패 모드) — PowerCycle 은 켜진다
  auth-fallback: 표준 비밀번호(standard-pw)를 401 로 거절, 공장 기본(보드 시리얼)만 수락

E2-2 가 더한 모드 — 집행 경로의 실측 시나리오:
  flash-slow     : Task 가 계속 Running (굽는 중)
  flash-exception: Task 가 Exception 으로 떨어진다 (BMC 가 실패로 종결)
  bmc-rebooting  : 모든 조회가 연결 거부처럼 실패 (BMC 자기 재기동 5~10분 구간)
  wrong-device   : Chassis 가 다른 보드 시리얼을 답한다 (주소가 남의 장비로 바뀐 경우)

E1.6 이 더한 것 — 계정 표준화 경로의 실측 재현:
  AccountService 트리(Accounts 컬렉션 · 계정별 GET + ETag · If-Match PATCH 204/412).
  유효 비밀번호가 정적이 아니라 상태다 — PATCH 가 실제로 admin 비밀번호를 갈아끼운다.
  신품 = /__passwords {"valid": ["<보드시리얼>"]} · 제3 비밀번호 = {"valid": ["someone-else"]}.

E3-1 이 더한 것 — BIOS 설정 적용 경로의 실측 재현(E0-4-3):
  GET  Systems/Self/Bios       : Attributes 전체 + ETag
  PATCH Systems/Self/Bios/SD   : If-Match 필수(* 허용) → pending 생성 · 204
  GET  Systems/Self/Bios/SD    : pending 없으면 404 (실측 거동)
  Reset ForceRestart           : pending 을 Attributes 에 적용하고 pending 을 비운다(재부팅 = POST 재연)
  모드  patch-412-once : 첫 PATCH 를 If-Match:* 여도 412 로 거절(두 번째부터 수락 — fresh ETag 폴백 검증)
        no-pending     : PATCH 204 지만 pending 을 만들지 않는다(무변경 PATCH 거동 미실측 재연)
        readback-drift : 재부팅 시 pending 중 한 속성을 반영하지 않는다(readback 불일치 재연)
        patch-reject   : PATCH Bios/SD 를 400 으로 거절한다(속성 거절 — PATCH_REJECTED 재연)
        reset-fail     : ComputerSystem.Reset 을 500 으로 거절한다(재부팅 명령 실패 → 행이 rebootAt 없이 남아 다음 주기 재개)
  Reset On 도 pending 을 적용한다 — 꺼진 장비를 켜는 것도 POST 를 지난다.
  BIOS 값 조작(무인증): POST /__bios {"SETUP004_BootupNumLockState": "On"}

E3-2 가 더한 것 — AMI 웹 API(사내 표준 BMC 세팅 4종)의 실측 재현(E0-3 · HAR 2026-08-25):
  POST /api/session(form) → {ok:0, CSRFToken} + Set-Cookie QSESSIONID · DELETE /api/session → {ok:0}
  이후 /api/* 는 X-CSRFTOKEN + 쿠키가 세션과 맞아야 한다 — 아니면 401 + {"cc":7,"error":"Invalid Authentication"}
  GET/PUT  /api/settings/date-time (GET 8 필드 · PUT 은 요청 에코)
  GET/POST /api/cold_redundant-status · GET /api/cold_redundant-psu_count
  POST     /api/settings/fanprofile (에코 · strMode 반영) · GET …/fanprofile/mode · GET …/fanprofile/collection
  GET/PUT  /api/settings/network-bond (PUT 에코)
  모드  web-session-expire-once : 로그인 뒤 첫 쓰기에 cc:7 한 번(세션 만료 재연 — 재로그인 1회 재시도 검증)
        fanprofile-reject       : POST fanprofile 을 {"error":"Invalid Data","code":1010} 으로 거절
        bond-drop               : PUT network-bond 뒤 20초 동안 모든 요청(Redfish 포함)의 연결을 끊는다(재접속 대기 재연)
        web-auth-reject         : 로그인을 전부 cc:7 로 거절(자격증명 소진 재연)
        web-readback-drift      : date-time PUT 을 200 에코하되 저장하지 않는다(되읽기 불일치 재연)
  /__mode 바디의 dropSeconds 로 bond-drop 의 단절 길이를 바꾼다(기본 20).

E3-3 이 더한 것 — BIOS 속성 레지스트리 채집 체인의 실측 재현(2026-08-27, MD72-HB3 F44):
  GET Registries/BiosAttributeRegistry       : {"Location":[{"Uri": ".../BiosAttributeRegistry.json"}]}
  GET Registries/BiosAttributeRegistry.json  : 레지스트리 전문 — MOCK_REGISTRY_FILE 이 가리키는 파일(실기 채집본 권장),
                                               없으면 STATE['bios'] 키로 만든 최소 레지스트리(허용값 = 현재값 + 'Auto')
  모드  registry-missing : Registries/* 를 404 로 — 채집 불가(unavailable) 경로 재연(PATCH 는 종전대로 진행돼야 한다)

모드 전환(무인증, 하네스 전용): POST /__mode {"mode": "..."} · 상태 초기화: POST /__reset-state
버전 조작(무인증): POST /__inventory {"BIOS": "F29", "BMC": "13.06.27"}
비밀번호 조작(무인증): POST /__passwords {"valid": ["QG260700082"]}
기동:  python3 mock-redfish.py <port>   (기본 8443 — 443 은 비특권 바인딩 불가 환경 대비)
인증서: 같은 디렉토리에 cert.pem/key.pem 없으면 자동 생성(openssl 필요).
"""
import base64
import http.server
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.parse

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8443
HERE = os.path.dirname(os.path.abspath(__file__))
CERT, KEY = os.path.join(HERE, 'cert.pem'), os.path.join(HERE, 'key.pem')

BOARD_SERIAL = 'QG260700082'
OTHER_SERIAL = 'QG260700131'   # wrong-device 모드가 답하는 남의 장비 시리얼

def boot_initial():
    # E0-4-1 실측 초기값 — Mode 는 Legacy 가 현재값이었다(E2.5 가 UEFI 를 명시하는 근거).
    return {'BootSourceOverrideEnabled': 'Disabled', 'BootSourceOverrideTarget': 'None',
            'BootSourceOverrideMode': 'Legacy'}

STATE = {
    'power': 'Off', 'mode': 'normal', 'requests': [],
    'inventory': {'BIOS': 'F27', 'BMC': '13.06.26'},   # 굽기 전 현재 버전
    'flash': [],                                       # SimpleUpdate 요청 원문 (굽기 요청 0건 확인용)
    'pulled': [],                                      # 실제로 당겨 간 ImageURI (토큰 URL 동작 확인용)
    'pullErrors': [],
    'taskSeq': 1,
    'passwords': ['standard-pw', 'QG260700082'],   # 지금 유효한 admin 비밀번호 — PATCH 가 갈아끼운다
    'accountEtag': 1000,                           # fresh ETag 강제(E0-4-1) — PATCH 마다 증가
    'accountPatches': [],                          # If-Match · Password 요청 원문 (검증용)
    'bios': {'SETUP004_BootupNumLockState': 'On', 'BirchStream0058_SpeedStepPstates': 'Enable',
             'BirchStream0059_TurboMode': 'Enable', 'BirchStream0063_PackageCState': 'Auto'},
    'biosEtag': 5000,
    'biosPending': None,                           # PATCH 가 만든 pending (None = 비어 있음 → GET 404)
    'biosPatches': [],                             # If-Match · Attributes 요청 원문
    'patch412Served': False,
                          'boot': boot_initial(), 'systemEtag': 7000, 'bootPatches': [],
                          'bootPending': None, 'bootPatch412Served': False, 'bootedVia': [],                       # patch-412-once 모드의 1회 소비 표식
    'web': None,                                   # AMI 웹 API 상태(E3-2) — web_initial() 로 채운다
}

BOND_DROP_SECONDS = 20

def web_initial():
    return {
        'sessions': {},                            # CSRFToken → 쿠키값
        'seq': 0,
        'datetime': {'id': 1, 'primary_ntp': 'pool.ntp.org', 'secondary_ntp': 'time.nist.gov', 'ntp_auto_date': 0,
                     'timestamp': 1787642064, 'localized_timestamp': 1787674464, 'utc_minutes': 540, 'timezone': 'Etc/GMT+00'},
        'coldRedundant': {'get_cold_redundant_enable': 1, 'master_psu': 0},   # 표준(0)과 다르게 시작해 쓰기가 보이게
        'fanMode': 'default',
        'bond': {'id': 1, 'bond_enable': 0, 'bond_mode': 'active-backup', 'bond_ifc': 'eth1', 'auto_configuration_enable': 1},
        'writes': [],                              # {method, path, body} — 쓰기 순서 · 바디 검증용
        'logins': 0, 'logouts': 0,
        'expireServed': False,
        'bondDropUntil': 0,
        'dropSeconds': BOND_DROP_SECONDS,
    }

STATE['web'] = web_initial()

REGISTRY_FILE = os.environ.get('MOCK_REGISTRY_FILE')

def registry_document():
    """레지스트리 전문 — 파일이 있으면 그 원문, 없으면 현재 BIOS 값에서 만든 최소본."""
    if REGISTRY_FILE and os.path.exists(REGISTRY_FILE):
        with open(REGISTRY_FILE, 'rb') as f:
            return f.read()
    attrs = [{'AttributeName': k, 'Type': 'Enumeration', 'DisplayName': k, 'ReadOnly': False, 'ResetRequired': False,
              'DefaultValue': v, 'Value': [{'ValueName': v, 'ValueDisplayName': v}, {'ValueName': 'Auto', 'ValueDisplayName': 'Auto'}]}
             for k, v in STATE['bios'].items()]
    return json.dumps({'Id': 'BiosAttributeRegistry', 'RegistryVersion': '0.1.0', 'OwningEntity': 'GBT',
                       'RegistryEntries': {'Attributes': attrs, 'Dependencies': []}}).encode('utf-8')

def ensure_cert():
    if os.path.exists(CERT) and os.path.exists(KEY):
        return
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-keyout', KEY, '-out', CERT,
                    '-days', '30', '-nodes', '-subj', '/CN=mock-redfish'], check=True, capture_output=True)

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        sys.stderr.write('[mock-redfish] ' + (fmt % args) + '\n')

    def _raw(self, code, payload, content_type='application/json'):
        self.send_response(code)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _json(self, code, obj, headers=None):
        body = json.dumps(obj).encode()
        self.send_response(code)
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _authed(self):
        auth = self.headers.get('Authorization', '')
        if not auth.startswith('Basic '):
            return False
        try:
            user, _, pw = base64.b64decode(auth[6:]).decode().partition(':')
        except Exception:
            return False
        if user != 'admin':
            return False
        if STATE['mode'] == 'auth-fallback' and pw == 'standard-pw':
            return False  # 표준 계정 거절 → 클라이언트가 공장 기본(시리얼)으로 폴백해야 한다
        return pw in STATE['passwords']

    def _dropped(self):
        """단절 구간 판정 — bmc-rebooting 모드이거나 Bond 재구성(bond-drop) 창 안이면 어느 경로든 연결을 주지 않는다."""
        return STATE['mode'] == 'bmc-rebooting' or time.time() < STATE['web']['bondDropUntil']

    def _consume_boot_override(self):
        """POST 재연(E2.5) — pending 무장을 먼저 적용하고, Once 를 소진하며 어디로 부팅했는지 기록한다."""
        if STATE['bootPending']:
            STATE['boot'].update(STATE['bootPending'])
            STATE['bootPending'] = None
        armed = STATE['boot'].get('BootSourceOverrideEnabled') in ('Once', 'Continuous') \
            and STATE['boot'].get('BootSourceOverrideTarget') == 'Pxe'
        STATE['bootedVia'].append('Pxe' if armed else 'BootOrder')
        if STATE['boot'].get('BootSourceOverrideEnabled') == 'Once':
            STATE['boot']['BootSourceOverrideEnabled'] = 'Disabled'
            STATE['boot']['BootSourceOverrideTarget'] = 'None'

    def do_GET(self):
        if self.path == '/__state':
            self._json(200, STATE)
            return
        if self._dropped():
            # 재기동 구간 — 응답 자체를 주지 않는다(클라이언트에는 연결 실패로 보인다).
            self.close_connection = True
            return
        if self.path.startswith('/api/'):
            self._web('GET')
            return
        if not self._authed():
            self._json(401, {'error': 'unauthorized'})
            return
        if self.path == '/redfish/v1/Systems/Self':
            boot = dict(STATE['boot'])
            boot.update({
                'BootSourceOverrideEnabled@Redfish.AllowableValues': ['Disabled', 'Once', 'Continuous'],
                'BootSourceOverrideTarget@Redfish.AllowableValues': ['None', 'Pxe', 'Hdd', 'BiosSetup'],
                'BootSourceOverrideMode@Redfish.AllowableValues': ['Legacy', 'UEFI']})
            self._json(200, {'PowerState': STATE['power'], 'Boot': boot},
                       headers={'ETag': 'W/"%d"' % STATE['systemEtag']})
        elif self.path == '/redfish/v1/Managers/Self':
            # BMC 자기 버전의 표준 자리(2026-08-25 실측 · abfd28d) — 없으면 VerifyFlashStep 의 BMC 축이
            # "읽지 못함" 으로 실패한다(E2.5 CP5 F-1 발견).
            self._json(200, {'FirmwareVersion': STATE['inventory']['BMC']})
        elif self.path == '/redfish/v1/Systems/Self/Bios':
            self._json(200, {'Id': 'Bios', 'AttributeRegistry': 'BiosAttributeRegistry',
                             'Attributes': dict(STATE['bios'])},
                       headers={'ETag': 'W/"%d"' % STATE['biosEtag']})
        elif self.path == '/redfish/v1/Systems/Self/Bios/SD':
            # 실측: pending 이 비어 있으면 404, PATCH 는 수락.
            if STATE['biosPending'] is None:
                self._json(404, {'error': 'no pending settings'})
            else:
                self._json(200, {'Id': 'SD', 'Name': 'Future BIOS Settings',
                                 'Attributes': dict(STATE['biosPending'])},
                           headers={'ETag': 'W/"%d"' % STATE['biosEtag']})
        elif self.path == '/redfish/v1/AccountService/Accounts':
            self._json(200, {'Members': [
                {'@odata.id': '/redfish/v1/AccountService/Accounts/1'},
                {'@odata.id': '/redfish/v1/AccountService/Accounts/2'},
            ]})
        elif self.path == '/redfish/v1/AccountService/Accounts/1':
            # admin 이 아닌 선행 계정 — 클라이언트가 id 하드코딩 없이 UserName 매칭으로 찾는지 검증한다.
            self._json(200, {'Id': '1', 'UserName': 'anonymous', 'Enabled': False},
                       headers={'ETag': 'W/"%d"' % STATE['accountEtag']})
        elif self.path == '/redfish/v1/AccountService/Accounts/2':
            self._json(200, {'Id': '2', 'UserName': 'admin', 'Enabled': True},
                       headers={'ETag': 'W/"%d"' % STATE['accountEtag']})
        elif self.path.startswith('/redfish/v1/TaskService/Tasks/'):
            self._json(200, {'TaskState': self._task_state(), '@odata.id': self.path})
        elif self.path == '/redfish/v1/Chassis/Self':
            # 신원 확인(E2-2 D-11)의 재료. 실측대로 표준 필드는 더미이고 실제 시리얼은 OEM 노드에만 있다.
            serial = OTHER_SERIAL if STATE['mode'] == 'wrong-device' else BOARD_SERIAL
            self._json(200, {
                'SerialNumber': '01234567890123456789AB', 'PartNumber': '01234567',
                'Model': 'MS04-CE0-000', 'Manufacturer': 'Giga Computing',
                'Oem': {'GBTChassisOemProperty': {'Board Serial Number': serial}},
            })
        elif self.path == '/redfish/v1/Registries/BiosAttributeRegistry':
            if STATE['mode'] == 'registry-missing':
                self._json(404, {'error': 'registry unavailable (mock mode registry-missing)'})
            else:
                self._json(200, {'Id': 'BiosAttributeRegistry', 'Registry': 'BiosAttributeRegistry',
                                 'Location': [{'Language': 'en', 'Uri': '/redfish/v1/Registries/BiosAttributeRegistry.json'}]})
        elif self.path == '/redfish/v1/Registries/BiosAttributeRegistry.json':
            if STATE['mode'] == 'registry-missing':
                self._json(404, {'error': 'registry unavailable (mock mode registry-missing)'})
            else:
                STATE['registryServed'] = STATE.get('registryServed', 0) + 1
                self._raw(200, registry_document())
        elif self.path.startswith('/redfish/v1/UpdateService/FirmwareInventory/'):
            member = self.path.rsplit('/', 1)[-1]
            version = STATE['inventory'].get(member)
            if version is None:
                self._json(200, {'Id': member, 'Updateable': True})   # BIOS2 처럼 버전 미노출
            else:
                self._json(200, {'Id': member, 'Version': version, 'Updateable': True})
        else:
            self._json(404, {'error': self.path})

    def _pull(self, image_uri):
        """ImageURI 를 당겨 첫 줄을 버전으로 읽는다. 받지 못하면 굽지 않은 것으로 둔다."""
        if not image_uri:
            return None
        try:
            import urllib.request
            with urllib.request.urlopen(image_uri, timeout=5) as resp:
                first = resp.read(64).decode('utf-8', 'ignore').splitlines()
                STATE['pulled'].append(image_uri)
                return first[0].strip() if first else None
        except Exception as e:
            STATE['pullErrors'].append('%s : %s' % (image_uri, e))
            return None

    def _apply_pending(self):
        pending = STATE['biosPending'] or {}
        for k, v in pending.items():
            if STATE['mode'] == 'readback-drift' and k == sorted(pending)[0]:
                continue   # 첫 속성 하나를 일부러 반영하지 않는다
            STATE['bios'][k] = v
        STATE['biosPending'] = None
        STATE['biosEtag'] += 1

    def _task_state(self):
        if STATE['mode'] == 'flash-slow':
            return 'Running'
        if STATE['mode'] == 'flash-exception':
            return 'Exception'
        return 'Completed'

    def do_PUT(self):
        length = int(self.headers.get('Content-Length') or 0)
        raw = self.rfile.read(length) or b'{}'
        if STATE['mode'] == 'bmc-rebooting':
            self.close_connection = True
            return
        if self.path.startswith('/api/'):
            self._web('PUT', json.loads(raw))
            return
        self._json(404, {'error': self.path})

    def do_DELETE(self):
        if STATE['mode'] == 'bmc-rebooting':
            self.close_connection = True
            return
        if self.path.startswith('/api/'):
            self._web('DELETE')
            return
        self._json(404, {'error': self.path})

    def _web_authed(self):
        """세션 판정 — X-CSRFTOKEN 이 발급된 토큰이고 쿠키가 그 세션의 것이어야 한다."""
        w = STATE['web']
        token = self.headers.get('X-CSRFTOKEN')
        cookie = self.headers.get('Cookie', '')
        return token in w['sessions'] and w['sessions'][token] in cookie

    def _web(self, method, body=None):
        """AMI 웹 API 재현(E3-2) — 실측 두 실패 모양(cc:7 · error+code)과 성공 에코를 그대로 낸다."""
        w = STATE['web']
        if time.time() < w['bondDropUntil']:
            self.close_connection = True          # Bond 재구성 구간 — 연결 자체가 없다
            return
        path = self.path.split('?', 1)[0]
        if path == '/api/session':
            if method == 'POST':
                form = urllib.parse.parse_qs((body or {}).get('__raw', ''))
                user = (form.get('username') or [''])[0]
                pw = (form.get('password') or [''])[0]
                if STATE['mode'] == 'web-auth-reject' or user != 'admin' or pw not in STATE['passwords']:
                    self._json(200, {'cc': 7, 'error': 'Invalid Authentication'})
                    return
                w['seq'] += 1
                token = 'CSRF%06d' % w['seq']
                cookie = 'QSESSIONID=S%06d' % w['seq']
                w['sessions'][token] = cookie
                w['logins'] += 1
                self._json(200, {'ok': 0, 'privilege': 4, 'racsession_id': w['seq'], 'CSRFToken': token},
                           headers={'Set-Cookie': cookie + '; Path=/; HttpOnly; Secure'})
                return
            if method == 'DELETE':
                token = self.headers.get('X-CSRFTOKEN')
                w['sessions'].pop(token, None)
                w['logouts'] += 1
                self._json(200, {'ok': 0})
                return
            self._json(404, {'error': path})
            return
        if not self._web_authed():
            self._json(401, {'cc': 7, 'error': 'Invalid Authentication'})
            return
        if method in ('PUT', 'POST') and STATE['mode'] == 'web-session-expire-once' and not w['expireServed']:
            w['expireServed'] = True
            w['sessions'].pop(self.headers.get('X-CSRFTOKEN'), None)   # 세션이 실제로 죽는다 — 재로그인해야 한다
            self._json(401, {'cc': 7, 'error': 'Invalid Authentication'})
            return
        if method in ('PUT', 'POST'):
            w['writes'].append({'method': method, 'path': path, 'body': body})
        if path == '/api/settings/date-time':
            if method == 'GET':
                self._json(200, dict(w['datetime']))
            else:
                if STATE['mode'] != 'web-readback-drift':
                    for k in ('timezone', 'ntp_auto_date', 'primary_ntp', 'secondary_ntp', 'timestamp', 'utc_minutes'):
                        if k in body:
                            w['datetime'][k] = body[k]
                self._json(200, body)
            return
        if path == '/api/cold_redundant-status':
            if method == 'GET':
                self._json(200, dict(w['coldRedundant']))
            else:
                w['coldRedundant'] = {'get_cold_redundant_enable': body.get('set_cold_redundant_enable', 0),
                                      'master_psu': body.get('master_psu', 0)}
                self._json(200, body)
            return
        if path == '/api/cold_redundant-psu_count':
            self._json(200, {'psu_count': 2})
            return
        if path == '/api/settings/fanprofile' and method == 'POST':
            if STATE['mode'] == 'fanprofile-reject':
                self._json(200, {'error': 'Invalid Data', 'code': 1010})
                return
            w['fanMode'] = body.get('strMode', w['fanMode'])
            self._json(200, body)
            return
        if path == '/api/settings/fanprofile/mode':
            self._json(200, {'strMode': w['fanMode']})
            return
        if path == '/api/settings/fanprofile/collection':
            self._json(200, [])
            return
        if path == '/api/settings/network-bond':
            if method == 'GET':
                self._json(200, dict(w['bond']))
            else:
                w['bond'] = dict(w['bond'], **body)
                self._json(200, body)
                if STATE['mode'] == 'bond-drop':
                    w['bondDropUntil'] = time.time() + w['dropSeconds']
            return
        self._json(404, {'error': path})

    def do_PATCH(self):
        length = int(self.headers.get('Content-Length') or 0)
        body = json.loads(self.rfile.read(length) or b'{}')
        if STATE['mode'] == 'bmc-rebooting':
            self.close_connection = True
            return
        if not self._authed():
            self._json(401, {'error': 'unauthorized'})
            return
        if self.path == '/redfish/v1/Systems/Self':
            # E2.5 — boot override PATCH. If-Match 사다리(* / fresh ETag)와 모드별 거동을 재연한다.
            if_match = self.headers.get('If-Match')
            if STATE['mode'] == 'boot-override-412-once' and not STATE['bootPatch412Served']:
                STATE['bootPatch412Served'] = True
                self._json(412, {'error': 'precondition failed'})
                return
            if if_match not in ('*', 'W/"%d"' % STATE['systemEtag']):
                self._json(412, {'error': 'precondition failed'})
                return
            if STATE['mode'] == 'boot-override-reject':
                self._json(400, {'error': {'@Message.ExtendedInfo': [
                    {'Message': 'boot override rejected (mock mode boot-override-reject)'}]}})
                return
            boot = body.get('Boot') or {}
            STATE['bootPatches'].append({'ifMatch': if_match, 'boot': boot})
            if STATE['mode'] == 'boot-override-pending':
                STATE['bootPending'] = dict(STATE['bootPending'] or {}, **boot)   # Systems/Self 표시 불변(SD 경유 재연)
            else:
                STATE['boot'].update(boot)
            STATE['systemEtag'] += 1
            self.send_response(204)
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        if self.path == '/redfish/v1/Systems/Self/Bios/SD':
            if_match = self.headers.get('If-Match')
            if STATE['mode'] == 'patch-412-once' and not STATE['patch412Served']:
                STATE['patch412Served'] = True
                self._json(412, {'error': 'precondition failed'})
                return
            if if_match not in ('*', 'W/"%d"' % STATE['biosEtag']):
                self._json(412, {'error': 'precondition failed'})
                return
            if STATE['mode'] == 'patch-reject':
                self._json(400, {'error': 'attribute rejected (mock mode patch-reject)'})
                return
            attrs = body.get('Attributes') or {}
            STATE['biosPatches'].append({'ifMatch': if_match, 'attributes': attrs})
            if STATE['mode'] != 'no-pending':
                STATE['biosPending'] = dict(STATE['biosPending'] or {}, **attrs)
            STATE['biosEtag'] += 1
            self.send_response(204)
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        if self.path == '/redfish/v1/AccountService/Accounts/2':
            # 실측 계약(E0-4-1): fresh ETag 의 If-Match 필수 — 없거나 낡았으면 412.
            expected = 'W/"%d"' % STATE['accountEtag']
            if self.headers.get('If-Match') != expected:
                self._json(412, {'error': 'precondition failed'})
                return
            STATE['accountPatches'].append({'ifMatch': self.headers.get('If-Match'), 'body': body})
            new_password = body.get('Password')
            if new_password:
                STATE['passwords'] = [new_password]   # 교체 — 이전 비밀번호(공장 기본 포함)는 그 자리에서 죽는다
            STATE['accountEtag'] += 1
            self.send_response(204)
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        self._json(404, {'error': self.path})

    def do_POST(self):
        length = int(self.headers.get('Content-Length') or 0)
        raw = self.rfile.read(length) or b'{}'
        try:
            body = json.loads(raw)
        except ValueError:
            body = {'__raw': raw.decode('utf-8', 'ignore')}   # form(로그인)은 _web 이 해석한다
        if self.path == '/__mode':
            STATE['mode'] = body.get('mode', 'normal')
            STATE['web']['dropSeconds'] = int(body.get('dropSeconds', BOND_DROP_SECONDS))
            self._json(200, STATE)
            return
        if self.path == '/__reset-state':
            STATE.update({'power': 'Off', 'mode': 'normal', 'requests': [],
                          'inventory': {'BIOS': 'F27', 'BMC': '13.06.26'}, 'flash': [],
                          'pulled': [], 'pullErrors': [], 'taskSeq': 1,
                          'passwords': ['standard-pw', 'QG260700082'], 'accountEtag': 1000,
                          'accountPatches': [],
                          'bios': {'SETUP004_BootupNumLockState': 'On', 'BirchStream0058_SpeedStepPstates': 'Enable',
                                   'BirchStream0059_TurboMode': 'Enable', 'BirchStream0063_PackageCState': 'Auto'},
                          'biosEtag': 5000, 'biosPending': None, 'biosPatches': [], 'patch412Served': False,
                          'boot': boot_initial(), 'systemEtag': 7000, 'bootPatches': [],
                          'bootPending': None, 'bootPatch412Served': False, 'bootedVia': [],
                          'web': web_initial()})
            self._json(200, STATE)
            return
        if self.path == '/__inventory':
            STATE['inventory'].update(body)
            self._json(200, STATE)
            return
        if self.path == '/__bios':
            STATE['bios'].update(body)
            STATE['biosEtag'] += 1
            self._json(200, STATE)
            return
        if self.path == '/__passwords':
            STATE['passwords'] = list(body.get('valid', []))
            self._json(200, STATE)
            return
        if self._dropped():
            self.close_connection = True
            return
        if self.path.startswith('/api/'):
            self._web('POST', body)
            return
        if not self._authed():
            self._json(401, {'error': 'unauthorized'})
            return
        if self.path == '/redfish/v1/UpdateService/Actions/SimpleUpdate':
            # 실측 계약: UpdateComponent · TransferProtocol · ImageURI → 202 + Task 경로
            STATE['flash'].append(body)
            STATE['taskSeq'] += 1
            task = '/redfish/v1/TaskService/Tasks/%d' % STATE['taskSeq']
            # 실제 BMC 처럼 ImageURI 를 직접 당겨 온다 — 일회용 토큰 URL(E2-2 D-5)이 실제로
            # 동작하는지가 이 한 번의 GET 으로 확인된다. 파일 첫 줄을 새 버전으로 읽는다.
            if STATE['mode'] not in ('flash-exception', 'flash-slow'):
                component = body.get('UpdateComponent')
                pulled = self._pull(body.get('ImageURI'))
                if component and pulled:
                    STATE['inventory'][component] = pulled
            self._json(202, {}, headers={'Location': 'https://mock' + task})
            return
        if self.path == '/redfish/v1/Systems/Self/Actions/ComputerSystem.Reset':
            reset = body.get('ResetType')
            if STATE['mode'] == 'reset-fail':
                self._json(500, {'error': 'reset failed (mock mode reset-fail)'})
                return
            STATE['requests'].append(reset)
            if reset in ('ForceOff', 'GracefulShutdown'):
                STATE['power'] = 'Off'
            elif reset == 'On':
                if STATE['mode'] != 'on-noop':
                    STATE['power'] = 'On'      # on-noop: 202 만 주고 전원 불변(실측 실패 모드)
                    self._apply_pending()      # 꺼진 장비를 켜는 것도 POST 를 지난다 — pending 반영(E3-1)
                    self._consume_boot_override()
            elif reset in ('PowerCycle', 'ForceRestart'):
                STATE['power'] = 'On'
                self._apply_pending()   # 재부팅 = POST 재연: pending 을 현재값에 반영하고 비운다
                self._consume_boot_override()
            else:
                self._json(400, {'error': 'unknown ResetType ' + str(reset)})
                return
            self._json(202, {}, headers={'Location': 'https://mock/redfish/v1/TaskService/Tasks/1'})
        else:
            self._json(404, {'error': self.path})

if __name__ == '__main__':
    ensure_cert()
    server = http.server.ThreadingHTTPServer(('127.0.0.1', PORT), Handler)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(CERT, KEY)
    server.socket = ctx.wrap_socket(server.socket, server_side=True)
    print(f'[mock-redfish] listening on https://127.0.0.1:{PORT} (mode={STATE["mode"]})')
    server.serve_forever()
