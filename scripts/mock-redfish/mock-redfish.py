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

모드 전환(무인증, 하네스 전용): POST /__mode {"mode": "..."} · 상태 초기화: POST /__reset-state
버전 조작(무인증): POST /__inventory {"BIOS": "F29", "BMC": "13.06.27"}
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

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8443
HERE = os.path.dirname(os.path.abspath(__file__))
CERT, KEY = os.path.join(HERE, 'cert.pem'), os.path.join(HERE, 'key.pem')

BOARD_SERIAL = 'QG260700082'
OTHER_SERIAL = 'QG260700131'   # wrong-device 모드가 답하는 남의 장비 시리얼

STATE = {
    'power': 'Off', 'mode': 'normal', 'requests': [],
    'inventory': {'BIOS': 'F27', 'BMC': '13.06.26'},   # 굽기 전 현재 버전
    'flash': [],                                       # SimpleUpdate 요청 원문 (굽기 요청 0건 확인용)
    'pulled': [],                                      # 실제로 당겨 간 ImageURI (토큰 URL 동작 확인용)
    'pullErrors': [],
    'taskSeq': 1,
}
USERS = {'standard-pw', 'QG260700082'}  # auth-fallback 모드에서는 standard-pw 거절

def ensure_cert():
    if os.path.exists(CERT) and os.path.exists(KEY):
        return
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-keyout', KEY, '-out', CERT,
                    '-days', '30', '-nodes', '-subj', '/CN=mock-redfish'], check=True, capture_output=True)

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        sys.stderr.write('[mock-redfish] ' + (fmt % args) + '\n')

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
        return pw in USERS

    def do_GET(self):
        if self.path == '/__state':
            self._json(200, STATE)
            return
        if STATE['mode'] == 'bmc-rebooting':
            # 재기동 구간 — 응답 자체를 주지 않는다(클라이언트에는 연결 실패로 보인다).
            self.close_connection = True
            return
        if not self._authed():
            self._json(401, {'error': 'unauthorized'})
            return
        if self.path == '/redfish/v1/Systems/Self':
            self._json(200, {'PowerState': STATE['power']})
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

    def _task_state(self):
        if STATE['mode'] == 'flash-slow':
            return 'Running'
        if STATE['mode'] == 'flash-exception':
            return 'Exception'
        return 'Completed'

    def do_POST(self):
        length = int(self.headers.get('Content-Length') or 0)
        body = json.loads(self.rfile.read(length) or b'{}')
        if self.path == '/__mode':
            STATE['mode'] = body.get('mode', 'normal')
            self._json(200, STATE)
            return
        if self.path == '/__reset-state':
            STATE.update({'power': 'Off', 'mode': 'normal', 'requests': [],
                          'inventory': {'BIOS': 'F27', 'BMC': '13.06.26'}, 'flash': [],
                          'pulled': [], 'pullErrors': [], 'taskSeq': 1})
            self._json(200, STATE)
            return
        if self.path == '/__inventory':
            STATE['inventory'].update(body)
            self._json(200, STATE)
            return
        if STATE['mode'] == 'bmc-rebooting':
            self.close_connection = True
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
            STATE['requests'].append(reset)
            if reset in ('ForceOff', 'GracefulShutdown'):
                STATE['power'] = 'Off'
            elif reset == 'On':
                if STATE['mode'] != 'on-noop':
                    STATE['power'] = 'On'      # on-noop: 202 만 주고 전원 불변(실측 실패 모드)
            elif reset in ('PowerCycle', 'ForceRestart'):
                STATE['power'] = 'On'
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
