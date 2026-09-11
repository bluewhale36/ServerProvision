/*
 * E1.5 — 게스트 전원 제어 (단발 XHR).
 * 판정 · 문구의 SSOT 는 서버(PowerControlResult) — 이 스크립트는 결과(kind · powerState · message)를 표기만 한다.
 * 화면 경로는 폴링하지 않는다(D4) — 발행 후 [상태 조회] 로 잇는다. 파괴적 액션(RedfishResetType.destructive:
 * ON 외 전부)은 confirm 모달을 거친다.
 * 섹션은 실시간 갱신 영역(data-live="power" · HF15-7)이라 SSE 신호마다 통째로 교체된다 — 리스너는 문서에 위임하고
 * 요소는 쓸 때마다 다시 찾는다. 정상 종료 감시(세대 · 타이머)는 클로저에 남아 교체 뒤에도 이어진다.
 */
(function () {
    'use strict';

    const STATE_LABEL = {ON: 'On', OFF: 'Off', POWERING_ON: '켜는 중', POWERING_OFF: '끄는 중', UNKNOWN: '불명'};
    const RESET_LABEL = {ON: '켜기', FORCE_OFF: '강제 끄기', FORCE_RESTART: '재시작', GRACEFUL_SHUTDOWN: '정상 종료'};

    function section() { return document.getElementById('powerControlSection'); }
    function badge() { return document.getElementById('powerStateBadge'); }
    function message() { return document.getElementById('powerMessage'); }
    function forceOffBtn() { const s = section(); return s ? s.querySelector('[data-power-reset="FORCE_OFF"]') : null; }
    function base() { const s = section(); return s ? '/provisioning/server/' + s.dataset.serverId + '/power' : null; }

    function paint(result) {
        const b = badge(), m = message();
        if (!b || !m) return;   // 영역이 교체되는 찰나 — 다음 표기가 채운다
        const state = result.powerState;
        if (state) lastPower = state;
        // FAILED 인데 상태도 모르면 '불명' 대신 '실패' — 색만으로 실패를 전하지 않는다(CP5 F-2).
        b.textContent = state ? (STATE_LABEL[state] || state)
            : result.kind === 'FAILED' ? '실패' : '불명';
        b.className = 'n-badge ' + (state === 'ON' ? 'n-badge-green'
            : result.kind === 'FAILED' ? 'n-badge-red' : 'n-badge-gray');
        m.textContent = result.message || '';
        m.classList.toggle('is-danger', result.kind === 'FAILED');
    }

    function busy(on) {
        const s = section(), m = message();
        if (!s) return;
        s.querySelectorAll('button').forEach(function (btn) { btn.disabled = on; });
        if (on && m) {
            // BMC 무응답이면 연결 타임아웃(약 10초)까지 침묵하므로 진행 중임을 말한다(CP5 F-3). 결과가 오면 paint 가 덮는다.
            m.textContent = '요청 중 — BMC 응답을 기다립니다…';
            m.classList.remove('is-danger');
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

    // 정상 종료(GracefulShutdown)는 BMC 가 ACPI 전원 버튼을 누르는 것이라 호스트 OS 가 응답해야 꺼진다 — 진단 리눅스 ·
    // 설치 화면처럼 그 이벤트를 처리하지 않는 상태에서는 아무 일도 안 일어난다(실기 3호 F-10 · 3/3). 서버는 단발(D4)로
    // 두고, 화면이 상태를 잠시 지켜보다 시한 안에 꺼지지 않으면 강제 끄기를 안내한다.
    const GRACEFUL_WATCH_MS = 60000, GRACEFUL_STEP_MS = 5000;
    let watchTimer = null;
    let watchGen = 0;   // 감시 세대 — 조회 대기 중이던 옛 사슬이 깨어나 새 조작의 문구를 덮지 않게(HF15-4 Q4)
    let lastPower = null;   // 마지막으로 칠한 전원 상태 — 영역이 교체돼도 카운트다운이 배지를 되살린다(CP5 F-1)

    function stopWatch() {
        watchGen++;
        if (watchTimer) { clearTimeout(watchTimer); watchTimer = null; }
        const f = forceOffBtn();
        if (f) f.classList.replace('n-btn-danger', 'n-btn-outline-danger');
    }

    async function fetchState() {
        const resp = await fetch(base());
        return resp.ok ? resp.json() : null;
    }

    function watchGraceful(sentAt, gen) {
        if (gen === undefined) gen = watchGen;
        watchTimer = setTimeout(async function () {
            let state = null;
            try { state = await fetchState(); } catch (e) { /* 다음 주기 */ }
            if (gen !== watchGen) return;   // 조회하는 사이 다른 조작이 감시를 끝냈다 — 이 사슬은 여기서 죽는다
            if (state && state.powerState === 'OFF') {
                paint({kind: 'SENT', powerState: 'OFF', message: '정상 종료가 완료됐습니다 (PowerState Off 확인).'});
                stopWatch();
                return;
            }
            if (Date.now() - sentAt >= GRACEFUL_WATCH_MS) {
                paint({kind: 'FAILED', powerState: state ? state.powerState : null,
                    message: '호스트가 정상 종료 요청에 응답하지 않습니다 — 전원 버튼을 처리하는 OS 가 없는 상태(진단 리눅스 · 설치 화면 · UEFI 셸)일 수 있습니다. [강제 끄기] 로 종료하십시오.'});
                const f = forceOffBtn();
                if (f) f.classList.replace('n-btn-outline-danger', 'n-btn-danger');   // 기존 클래스 재사용 — 채움 빨강으로 강조
                watchTimer = null;
                return;
            }
            // 문구만 바꾸지 않고 배지까지 다시 칠한다 — 영역이 교체되면 배지가 서버 초기값(미조회)으로 돌아가 있기 때문(CP5 F-1)
            paint({kind: 'SENT', powerState: state && state.powerState ? state.powerState : lastPower,
                message: '정상 종료 요청 전달 — 호스트 응답을 기다립니다 (' + Math.round((Date.now() - sentAt) / 1000) + '초)…'});
            watchGraceful(sentAt, gen);
        }, GRACEFUL_STEP_MS);
    }

    async function reset(type) {
        stopWatch();
        await call('POST', base() + '/reset', {resetType: type});
        const m = message();
        if (type === 'GRACEFUL_SHUTDOWN' && m && !m.classList.contains('is-danger')) {
            watchGraceful(Date.now());
        }
    }

    // 문서 위임 — 섹션이 교체돼도 살아 있다. 버튼은 BMC 가 검출된 렌더에만 있으므로 검출 여부를 따로 보지 않는다.
    document.addEventListener('click', function (event) {
        const target = event.target instanceof Element ? event.target : null;
        if (!target || !section()) return;
        if (target.closest('#powerRefreshBtn')) { call('GET', base()); return; }
        const btn = target.closest('[data-power-reset]');
        if (!btn || !section().contains(btn)) return;
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
})();
