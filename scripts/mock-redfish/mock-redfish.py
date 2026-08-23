#!/usr/bin/env python3
"""
E1.5 CP5 — 모조 Redfish BMC (자가서명 TLS).

실측(E0-4)이 확정한 계약의 최소 재현: Basic 인증, ComputerSystem.Reset(202 + Task Location),
TaskService 판독, PowerState. 세 가지 응답 모드로 실측 시나리오를 재연한다:
  normal      : Reset 이 즉시 상태를 바꾼다
  on-noop     : Reset(On) 이 202 를 주지만 전원이 켜지지 않는다(실측 실패 모드) — PowerCycle 은 켜진다
  auth-fallback: 표준 비밀번호(standard-pw)를 401 로 거절, 공장 기본(보드 시리얼)만 수락

모드 전환(무인증, 하네스 전용): POST /__mode {"mode": "..."} · 상태 초기화: POST /__reset-state
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

STATE = {'power': 'Off', 'mode': 'normal', 'requests': []}
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
        if not self._authed():
            self._json(401, {'error': 'unauthorized'})
            return
        if self.path == '/redfish/v1/Systems/Self':
            self._json(200, {'PowerState': STATE['power']})
        elif self.path.startswith('/redfish/v1/TaskService/Tasks/'):
            self._json(200, {'TaskState': 'Completed', '@odata.id': self.path})
        else:
            self._json(404, {'error': self.path})

    def do_POST(self):
        length = int(self.headers.get('Content-Length') or 0)
        body = json.loads(self.rfile.read(length) or b'{}')
        if self.path == '/__mode':
            STATE['mode'] = body.get('mode', 'normal')
            self._json(200, STATE)
            return
        if self.path == '/__reset-state':
            STATE.update({'power': 'Off', 'mode': 'normal', 'requests': []})
            self._json(200, STATE)
            return
        if not self._authed():
            self._json(401, {'error': 'unauthorized'})
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
