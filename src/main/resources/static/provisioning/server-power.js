/*
 * E1.5 — 게스트 전원 제어 (단발 XHR).
 * 판정 · 문구의 SSOT 는 서버(PowerControlResult) — 이 스크립트는 결과(kind · powerState · message)를 표기만 한다.
 * 화면 경로는 폴링하지 않는다(D4) — 발행 후 [상태 조회] 로 잇는다. 파괴적 액션(RedfishResetType.destructive:
 * ON 외 전부)은 confirm 모달을 거친다.
 */
(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', function () {
        const section = document.getElementById('powerControlSection');
        if (!section || section.dataset.bmcDetected !== 'true') return;

        const serverId = section.dataset.serverId;
        const badge = document.getElementById('powerStateBadge');
        const message = document.getElementById('powerMessage');
        const refreshBtn = document.getElementById('powerRefreshBtn');
        const base = '/provisioning/server/' + serverId + '/power';

        const STATE_LABEL = {ON: 'On', OFF: 'Off', POWERING_ON: '켜는 중', POWERING_OFF: '끄는 중', UNKNOWN: '불명'};
        const RESET_LABEL = {ON: '켜기', FORCE_OFF: '강제 끄기', FORCE_RESTART: '재시작', GRACEFUL_SHUTDOWN: '정상 종료'};

        function paint(result) {
            const state = result.powerState;
            // FAILED 인데 상태도 모르면 '불명' 대신 '실패' — 색만으로 실패를 전하지 않는다(CP5 F-2).
            badge.textContent = state ? (STATE_LABEL[state] || state)
                : result.kind === 'FAILED' ? '실패' : '불명';
            badge.className = 'n-badge ' + (state === 'ON' ? 'n-badge-green'
                : result.kind === 'FAILED' ? 'n-badge-red' : 'n-badge-gray');
            message.textContent = result.message || '';
            message.classList.toggle('is-danger', result.kind === 'FAILED');
        }

        function busy(on) {
            section.querySelectorAll('button').forEach(function (btn) { btn.disabled = on; });
            if (on) {
                // BMC 무응답이면 연결 타임아웃(약 10초)까지 침묵하므로 진행 중임을 말한다(CP5 F-3). 결과가 오면 paint 가 덮는다.
                message.textContent = '요청 중 — BMC 응답을 기다립니다…';
                message.classList.remove('is-danger');
            }
        }

        async function call(method, url, body) {
            busy(true);
            try {
                const resp = await fetch(url, {
                    method: method,
                    headers: body ? {'Content-Type': 'application/json'} : undefined,
                    body: body ? JSON.stringify(body) : undefined
                });
                if (!resp.ok) {
                    const err = await resp.json().catch(function () { return null; });
                    paint({kind: 'FAILED', powerState: null,
                        message: (err && err.message) ? err.message : ('요청 실패 (HTTP ' + resp.status + ')')});
                    return;
                }
                paint(await resp.json());
            } catch (e) {
                paint({kind: 'FAILED', powerState: null, message: '서버와 통신할 수 없습니다: ' + e.message});
            } finally {
                busy(false);
            }
        }

        function reset(type) {
            call('POST', base + '/reset', {resetType: type});
        }

        refreshBtn.addEventListener('click', function () { call('GET', base); });

        section.querySelectorAll('[data-power-reset]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const type = btn.dataset.powerReset;
                if (type === 'ON') { reset(type); return; }     // destructive() 아님 — confirm 없이 발행
                if (!window.ConfirmModal) { reset(type); return; } // base 미적재 폴백(정상 경로에선 항상 적재됨)
                window.ConfirmModal.open('powerConfirm', {
                    title: '전원 제어 — ' + RESET_LABEL[type],
                    message: '이 게스트에 "' + RESET_LABEL[type] + '"(' + type + ') 명령을 보낼까요? 실행 중인 작업이 중단될 수 있습니다.',
                    confirmLabel: RESET_LABEL[type] + ' 실행',
                    onConfirm: function () { reset(type); }
                });
            });
        });
    });
})();
