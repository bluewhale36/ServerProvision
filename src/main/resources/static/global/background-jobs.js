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
        document.addEventListener('DOMContentLoaded', ensureEl, { once: true });
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
    const panel   = document.getElementById('bgjobPanel');
    const dot     = document.getElementById('bgjobDot');
    const list    = document.getElementById('bgjobList');
    const empty   = document.getElementById('bgjobEmpty');
    const count   = document.getElementById('bgjobCount');
    const clear   = document.getElementById('bgjobClear');

    if (!trigger || !panel || !list || !empty || !count || !clear) return;

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

    function statusLabel(status) {
        switch (status) {
            case 'PENDING':   return '대기';
            case 'RUNNING':   return '진행 중';
            case 'COMPLETED': return '완료';
            case 'FAILED':    return '실패';
            default:          return status || '';
        }
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
            job.status === 'FAILED'    ? 'is-failed' :
            job.status === 'RUNNING'   ? 'is-running' : 'is-pending';
        const isTerminal = job.status === 'COMPLETED' || job.status === 'FAILED';
        const rawPercent = typeof job.percent === 'number' ? job.percent : -1;
        const percent = rawPercent >= 0 ? rawPercent : 0;
        // RUNNING 인데 percent 를 아직 못 받은 경우는 indeterminate 애니메이션 — Job 타입에 무관.
        const isIndeterminate = job.status === 'RUNNING' && rawPercent < 0;
        const indetClass = isIndeterminate ? ' is-indeterminate' : '';
        const subtitle = job.subtitle ? `<div class="n-bgjob-card-subtitle">${escapeHtml(job.subtitle)}</div>` : '';
        const time = isTerminal ? formatTime(job.completedAt) : formatTime(job.createdAt);
        const barHidden = isTerminal && job.status === 'FAILED' ? ' hidden' : '';
        const barStyle = isIndeterminate ? '' : `style="width:${percent}%"`;
        return `
            <li class="n-bgjob-card ${stateClass}${indetClass}" data-job-id="${escapeHtml(job.id)}">
                <div class="n-bgjob-card-header">
                    <span class="n-bgjob-card-title">${escapeHtml(job.title)}</span>
                    <span class="n-bgjob-card-type">${escapeHtml(job.typeLabel)}</span>
                </div>
                ${subtitle}
                <div class="n-bgjob-card-message">${escapeHtml(job.message || '')}</div>
                <div class="n-bgjob-card-bar-wrap"${barHidden}>
                    <div class="n-bgjob-card-bar" ${barStyle}></div>
                </div>
                <div class="n-bgjob-card-footer">
                    <span class="n-bgjob-card-status">${statusLabel(job.status)}</span>
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
    async function poll() {
        try {
            const resp = await fetch('/pxe/v1/jobs', { headers: { 'Accept': 'application/json' } });
            if (!resp.ok) {
                schedule();
                return;
            }
            const body = await resp.json();
            const jobs = Array.isArray(body.jobs) ? body.jobs : [];
            if (!firstPollDone) {
                seedTerminalSeen(jobs);
                firstPollDone = true;
            } else {
                detectTerminalTransitions(jobs);
            }
            render(jobs);
        } catch (e) {
            // 네트워크 오류는 조용히 흘림 — 다음 폴링에서 자연 복구
        } finally {
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
            await fetch(`/pxe/v1/jobs/${encodeURIComponent(id)}/dismiss`, { method: 'POST' });
        } catch (_) { /* no-op — 다음 폴링에서 자연 재동기화 */ }
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
            return fetch(`/pxe/v1/jobs/${encodeURIComponent(id)}/dismiss`, { method: 'POST' })
                .catch(() => { /* no-op */ });
        }));
        poll();
    });

    // ---- 초기 실행 ---------------------------------------------
    poll();
})();
