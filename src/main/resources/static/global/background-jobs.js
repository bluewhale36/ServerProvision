/* ============================================================
   배경 작업 알림 센터 — 모든 페이지에 공통 로드된다 (layout.html commonHead)
   ─────────────────────────────────────────────────────────────
   · 2초 주기 폴링 (드롭다운 열림 시 0.5초 주기)
   · 서류가방 버튼 클릭 → 드롭다운 토글
   · 활성 Job 수 dot 표시
   · 카드 렌더 (타입 칩 / 제목 / 부제목 / 진행률 바 / 상태 / 시각 / 닫기)
   · 닫기 버튼 → POST dismiss
   · 모두 지우기 → 종료된 Job 전부 dismiss
   · Job 이 처음으로 종료 상태(COMPLETED/FAILED) 로 관측되면 커스텀 이벤트
     `bgjob:completed` / `bgjob:failed` 를 document 에 dispatch. 페이지 전용
     JS(os-list.js 등) 가 type 에 따라 후처리(예: 페이지 리로드) 할 수 있도록.
   · 공용 토스트 API : window.bgjobToast(message, { duration, error }) —
     서류가방 아이콘 바로 아래에 잠깐 뜨는 알림. 추출 시작·완료·실패 등 작업 이벤트에 사용.
   ============================================================ */

// ---- 공용 토스트 (전역 노출) -----------------------------------
// API : window.bgjobToast(message, { variant, duration })
//   variant : 'info' (기본) | 'success' | 'error'
//   duration : ms (기본 2800)
(function () {
    const VARIANTS = ['info', 'success', 'error'];
    let toastEl = null;
    let timer = null;

    function ensureEl() {
        if (toastEl) return toastEl;
        toastEl = document.createElement('div');
        toastEl.id = 'bgjobToast';
        toastEl.className = 'n-bgjob-toast';
        toastEl.setAttribute('role', 'status');
        toastEl.setAttribute('aria-live', 'polite');
        // body 가 아직 없으면 (해당 경우 드물지만) defer 로 실행되니 안전.
        (document.body || document.documentElement).appendChild(toastEl);
        // 삽입 직후 reflow 를 강제로 트리거해 초기 스타일(opacity:0; translateY)을 계산시킨다.
        // 이렇게 해야 이후 class 변경이 "초기 → 최종" 전이로 인식되어 CSS transition 이 발동한다.
        // (이 reflow 가 없으면 첫 토스트는 애니메이션 없이 즉시 최종 상태로 나타난다.)
        void toastEl.offsetWidth;
        return toastEl;
    }

    // 페이지 로드 직후 엘리먼트를 사전 생성해 둔다 — 첫 bgjobToast 호출이 왔을 때
    // 이미 DOM 에 삽입되어 초기 스타일이 계산된 상태이므로 transition 이 확실하게 동작한다.
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', ensureEl, {once: true});
    } else {
        ensureEl();
    }

    window.bgjobToast = function (message, opts) {
        opts = opts || {};
        const el = ensureEl();
        el.textContent = message;
        const variant = VARIANTS.indexOf(opts.variant) >= 0 ? opts.variant : 'info';
        VARIANTS.forEach(v => el.classList.remove('is-' + v));
        el.classList.add('is-' + variant);
        el.classList.add('is-visible');
        if (timer) clearTimeout(timer);
        const duration = typeof opts.duration === 'number' ? opts.duration : 2800;
        timer = setTimeout(() => el.classList.remove('is-visible'), duration);
    };
})();

(function () {
    const POLL_CLOSED_MS = 2000;
    const POLL_OPEN_MS = 500;

    const trigger = document.getElementById('bgjobTrigger');
    const panel = document.getElementById('bgjobPanel');
    const dot = document.getElementById('bgjobDot');
    const list = document.getElementById('bgjobList');
    const empty = document.getElementById('bgjobEmpty');
    const count = document.getElementById('bgjobCount');
    const clear = document.getElementById('bgjobClear');

    if (!trigger || !panel || !list || !empty || !count || !clear) return;

    // ---- CH3 : 폴링 실패 → 자동 reload 가드 ----------------------------
    // 임계값 / grace 는 commonHead fragment 의 data-attribute 로 외부화.
    // (override 가 없으면 default 5회 / 1500ms.)
    // 우선순위 : (1) <script src=".../background-jobs.js"> 의 data-* 속성
    //              (2) <html> 의 data-* 속성  ← layout 가 직접 제어 가능한 페이지에서 override 용
    //              (3) default
    const scriptEl = document.currentScript
        || document.querySelector('script[src*="background-jobs.js"]');

    function readIntAttr(name, fallback) {
        const sources = [scriptEl, document.documentElement];
        for (const src of sources) {
            if (!src) continue;
            const v = src.getAttribute(name);
            if (v == null) continue;
            const n = parseInt(v, 10);
            if (Number.isFinite(n) && n > 0) return n;
        }
        return fallback;
    }

    const FAIL_THRESHOLD = readIntAttr('data-bgjob-fail-threshold', 5);
    const RELOAD_GRACE_MS = readIntAttr('data-bgjob-fail-grace-ms', 1500);
    const RELOAD_LOOP_GUARD_MS = 30 * 1000; // 30초 내 직전 reload 가 있으면 보류
    const RELOAD_TS_KEY = 'bgjob:lastReloadAt';
    const PERMA_NOTICE_MSG = '서버와 연결이 끊어졌습니다. 잠시 후 다시 시도해주세요.';
    const RELOAD_NOTICE_MSG = '서버 응답이 없어 페이지를 새로고침합니다.';

    let consecutiveFailures = 0;
    let reloadScheduled = false;       // 이미 reload 예약 (지연 중) 인지
    let permaNoticeShown = false;      // 무한 reload 가드 메시지 1회만

    // 업로드 진행 카운터 — 동시 다중 업로드도 정확히 추적.
    // 외부 모듈은 `bgjob:uploadStart` / `bgjob:uploadEnd` document event 로 통보한다.
    // (보조: `window.__bgjobUploading === true` 도 인정한다.)
    let uploadInProgressCount = 0;
    document.addEventListener('bgjob:uploadStart', () => {
        uploadInProgressCount++;
    });
    document.addEventListener('bgjob:uploadEnd', () => {
        uploadInProgressCount = Math.max(0, uploadInProgressCount - 1);
        // 업로드가 모두 끝난 시점에 임계 초과가 누적된 상태였으면 그때 reload 시도
        if (uploadInProgressCount === 0 && consecutiveFailures >= FAIL_THRESHOLD) {
            tryScheduleReload();
        }
    });

    function isUploadActive() {
        return uploadInProgressCount > 0 || window.__bgjobUploading === true;
    }

    function recentlyReloaded() {
        try {
            const raw = sessionStorage.getItem(RELOAD_TS_KEY);
            if (!raw) return false;
            const last = parseInt(raw, 10);
            if (!Number.isFinite(last)) return false;
            return (Date.now() - last) < RELOAD_LOOP_GUARD_MS;
        } catch (_) {
            return false; // sessionStorage 비활성화 환경 — 무한 루프 가드 비활성화
        }
    }

    function markReloaded() {
        try {
            sessionStorage.setItem(RELOAD_TS_KEY, String(Date.now()));
        } catch (_) { /* no-op */
        }
    }

    function tryScheduleReload() {
        if (reloadScheduled) return;
        if (isUploadActive()) {
            // 업로드 중이면 보류 — `bgjob:uploadEnd` 핸들러가 다시 호출
            return;
        }
        if (recentlyReloaded()) {
            // 30초 내 reload 가 직전 → 무한 루프 회피. 영구 알림 1회.
            if (!permaNoticeShown) {
                permaNoticeShown = true;
                if (typeof window.bgjobToast === 'function') {
                    window.bgjobToast(PERMA_NOTICE_MSG, {variant: 'error', duration: 8000});
                }
            }
            return;
        }
        reloadScheduled = true;
        if (typeof window.bgjobToast === 'function') {
            window.bgjobToast(RELOAD_NOTICE_MSG, {variant: 'error', duration: RELOAD_GRACE_MS + 200});
        }
        setTimeout(() => {
            markReloaded();
            window.location.reload();
        }, RELOAD_GRACE_MS);
    }

    function recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= FAIL_THRESHOLD) {
            tryScheduleReload();
        }
    }

    function recordSuccess() {
        consecutiveFailures = 0;
    }

    // 이전 폴링 결과의 종료 상태 기록 — 신규 종료 전이를 감지해 이벤트 디스패치
    const terminalSeen = new Map();
    // 첫 폴링에서는 이벤트 dispatch 를 억제한다.
    // 이유 : 종료 Job 은 서버가 10분 보관하므로, 완료 → reload → 새 페이지 로드 직후의 첫 폴링에서
    //        같은 종료 Job 을 다시 "처음 관측됨" 으로 오판해 이벤트 재dispatch → 무한 reload 로 이어진다.
    //        페이지 로드 시점의 기존 종료 상태는 이미 한 번 소화된 사건으로 취급한다.
    let firstPollDone = false;

    let currentTimer = null;

    // ---- XSS 방어 ----------------------------------------------
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatTime(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        if (isNaN(d.getTime())) return '';
        const hh = String(d.getHours()).padStart(2, '0');
        const mm = String(d.getMinutes()).padStart(2, '0');
        return `${hh}:${mm}`;
    }

    // S5-8 — 카드 footer 의 status 영역 텍스트 합성.
    // 단순 enum 라벨 ("진행 중" / "실패") 이 아닌, RUNNING / FAILED 시에는 stages 배열에서
    // 해당 단계의 label 을 찾아 "{label} 중" / "{label} 실패" 로 합성해 "지금 무엇을 하고 있는가" 를
    // 알림 패널 1 회 시선으로 파악 가능하게 한다. fallback 으로 기존 enum 라벨 보존.
    function currentStatusText(job) {
        if (!job) return '';
        const status = job.status;
        const stages = Array.isArray(job.stages) ? job.stages : [];

        if (status === 'PENDING') return '대기 중';
        if (status === 'COMPLETED') return '완료';

        if (status === 'RUNNING') {
            const running = stages.find(s => s && s.status === 'RUNNING');
            if (running && running.label) return `${running.label} 중`;
            return '진행 중';
        }

        if (status === 'FAILED') {
            const errored = stages.find(s => s && s.status === 'ERROR');
            if (errored && errored.label) return `${errored.label} 실패`;
            return '실패';
        }

        return status || '';
    }

    // ---- 렌더 ---------------------------------------------------
    function render(jobs) {
        const active = jobs.filter(j => j.status === 'PENDING' || j.status === 'RUNNING');
        const total = jobs.length;

        count.textContent = `(${total})`;

        if (active.length > 0) {
            dot.hidden = false;
        } else {
            dot.hidden = true;
        }

        const hasTerminal = jobs.some(j => j.status === 'COMPLETED' || j.status === 'FAILED');
        clear.disabled = !hasTerminal;

        if (total === 0) {
            empty.hidden = false;
            list.hidden = true;
            list.innerHTML = '';
            return;
        }

        empty.hidden = true;
        list.hidden = false;
        list.innerHTML = jobs.map(cardHtml).join('');
    }

    function cardHtml(job) {
        const stateClass =
            job.status === 'COMPLETED' ? 'is-completed' :
                job.status === 'FAILED' ? 'is-failed' :
                    job.status === 'RUNNING' ? 'is-running' : 'is-pending';
        const isTerminal = job.status === 'COMPLETED' || job.status === 'FAILED';
        const subtitle = job.subtitle ? `<div class="n-bgjob-card-subtitle">${escapeHtml(job.subtitle)}</div>` : '';
        const time = isTerminal ? formatTime(job.completedAt) : formatTime(job.createdAt);
        const errorMsg = job.errorMessage
            ? `<div class="n-bgjob-card-message">${escapeHtml(job.errorMessage)}</div>` : '';

        // chunk progress bar — 단계별 라벨 + 색상.
        // PENDING(grey) / RUNNING(blue) / DONE(green) / ERROR(red)
        const stages = Array.isArray(job.stages) ? job.stages : [];
        const chunks = stages.map(s => {
            const cls = 'n-bgjob-stage-chunk is-' + (s.status || 'PENDING').toLowerCase();
            return `<div class="${cls}" title="${escapeHtml(s.label)}"><span class="n-bgjob-stage-label">${escapeHtml(s.label)}</span></div>`;
        }).join('');

        return `
            <li class="n-bgjob-card ${stateClass}" data-job-id="${escapeHtml(job.id)}">
                <div class="n-bgjob-card-header">
                    <span class="n-bgjob-card-title">${escapeHtml(job.title)}</span>
                    <span class="n-bgjob-card-type">${escapeHtml(job.typeLabel)}</span>
                </div>
                ${subtitle}
                ${errorMsg}
                <div class="n-bgjob-stage-track">${chunks}</div>
                <div class="n-bgjob-card-footer">
                    <span class="n-bgjob-card-status">${escapeHtml(currentStatusText(job))}</span>
                    <span class="n-bgjob-card-time">${escapeHtml(time)}</span>
                    <button type="button"
                            class="n-bgjob-card-close"
                            aria-label="닫기"
                            ${isTerminal ? '' : 'hidden'}>&times;</button>
                </div>
            </li>
        `;
    }

    // ---- 이벤트 감지 -------------------------------------------
    function detectTerminalTransitions(jobs) {
        jobs.forEach(j => {
            if (j.status === 'COMPLETED' || j.status === 'FAILED') {
                if (!terminalSeen.has(j.id)) {
                    terminalSeen.set(j.id, j.status);
                    const ev = new CustomEvent(
                        j.status === 'COMPLETED' ? 'bgjob:completed' : 'bgjob:failed',
                        {
                            detail: {
                                id: j.id,
                                type: j.type,
                                subtitle: j.subtitle,
                                title: j.title,
                                metadata: j.metadata || {}
                            }
                        }
                    );
                    document.dispatchEvent(ev);
                }
            }
        });
        // 사라진 jobId 는 맵에서 정리 (서버 dismiss/prune 반영)
        const ids = new Set(jobs.map(j => j.id));
        for (const id of Array.from(terminalSeen.keys())) {
            if (!ids.has(id)) terminalSeen.delete(id);
        }
    }

    // 첫 폴링 전용 — 기존 종료 Job 을 "이미 본 것" 으로만 등록하고 이벤트는 쏘지 않는다.
    function seedTerminalSeen(jobs) {
        jobs.forEach(j => {
            if (j.status === 'COMPLETED' || j.status === 'FAILED') {
                terminalSeen.set(j.id, j.status);
            }
        });
    }

    // ---- 폴링 --------------------------------------------------
    // CH3 : 연속 실패 (network error / 비-2xx / JSON parse 실패) 가
    //       FAIL_THRESHOLD 회 누적되면 toast 후 자동 reload.
    //       1회라도 성공하면 카운터 0 으로 리셋 → 간헐적 실패는 무시한다.
    async function poll() {
        let succeeded = false;
        try {
            const resp = await fetch('/jobs', {headers: {'Accept': 'application/json'}});
            if (!resp.ok) {
                // 비-2xx (5xx 우선, 4xx 도 포함) — 실패로 카운트
                recordFailure();
                schedule();
                return;
            }
            let body;
            try {
                body = await resp.json();
            } catch (_parseErr) {
                // JSON 파싱 실패 — 서버가 비정상 응답을 흘린 경우. 실패로 카운트.
                recordFailure();
                schedule();
                return;
            }
            const jobs = Array.isArray(body.jobs) ? body.jobs : [];
            if (!firstPollDone) {
                seedTerminalSeen(jobs);
                firstPollDone = true;
            } else {
                detectTerminalTransitions(jobs);
            }
            render(jobs);
            succeeded = true;
        } catch (_e) {
            // network error / TypeError — 실패로 카운트
            recordFailure();
        } finally {
            if (succeeded) recordSuccess();
            schedule();
        }
    }

    function schedule() {
        if (currentTimer) clearTimeout(currentTimer);
        const isOpen = !panel.hidden;
        currentTimer = setTimeout(poll, isOpen ? POLL_OPEN_MS : POLL_CLOSED_MS);
    }

    // ---- 드롭다운 토글 ----------------------------------------
    function open() {
        panel.hidden = false;
        trigger.classList.add('is-open');
        trigger.setAttribute('aria-expanded', 'true');
        schedule(); // 열림 주기(0.5s)로 즉시 재편성
    }

    function close() {
        panel.hidden = true;
        trigger.classList.remove('is-open');
        trigger.setAttribute('aria-expanded', 'false');
        schedule();
    }

    trigger.addEventListener('click', e => {
        e.stopPropagation();
        if (panel.hidden) open(); else close();
    });

    // 외부 클릭 시 닫기
    document.addEventListener('click', e => {
        if (panel.hidden) return;
        if (panel.contains(e.target) || trigger.contains(e.target)) return;
        close();
    });

    // Esc 로 닫기
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && !panel.hidden) close();
    });

    // ---- dismiss / clear ---------------------------------------
    list.addEventListener('click', async e => {
        const btn = e.target.closest('.n-bgjob-card-close');
        if (!btn) return;
        const card = btn.closest('.n-bgjob-card');
        if (!card) return;
        const id = card.dataset.jobId;
        if (!id) return;
        try {
            await fetch(`/jobs/${encodeURIComponent(id)}/dismiss`, {method: 'POST'});
        } catch (_) { /* no-op — 다음 폴링에서 자연 재동기화 */
        }
        poll();
    });

    clear.addEventListener('click', async () => {
        const cards = Array.from(list.querySelectorAll('.n-bgjob-card'));
        const terminal = cards.filter(c =>
            c.classList.contains('is-completed') || c.classList.contains('is-failed')
        );
        await Promise.all(terminal.map(c => {
            const id = c.dataset.jobId;
            if (!id) return Promise.resolve();
            return fetch(`/jobs/${encodeURIComponent(id)}/dismiss`, {method: 'POST'})
                .catch(() => { /* no-op */
                });
        }));
        poll();
    });

    // ---- 초기 실행 ---------------------------------------------
    poll();
})();
