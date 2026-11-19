/**
 * 중복 확인 창 공통 처리(MK2 · HF30-3 D14).
 *
 * 사용법:
 *   NudgeModal.open(payload, {
 *       prefix: 'biosNudge',            // 조각 nudgeModal(prefix, confirmBaseUrl) 의 prefix
 *       phase: 'content' | 'intent',    // 업로드 뒤 창(.../nudge) 또는 업로드 전 창(.../intent-nudge)
 *       cancelNote: 'beforeUpload' | 'afterUpload' | 'existingFolder',  // [취소] 설명 줄
 *       onDone: (body) => { ... },      // [그래도 등록] · [기존 영구 삭제 후 등록] 성공 응답
 *       notify: {info: fn, error: fn}   // 창이 닫힌 뒤 화면에 남길 알림(취소, 만료)
 *   });
 *
 * 후보 목록, 선택, 자원명 대조, 남은 시간, 만료, 창 안 오류를 여기 한 곳에서 다룬다. 화면은 끝난 뒤 할 일만 넘긴다.
 * 자원명 대조 기준은 서버와 같은 displayName 이다(UserPurgeService 의 TypedNameGuard).
 */
(function () {
    'use strict';

    const MSG_EXPIRED = '중복 확인이 만료되었습니다. 다시 등록해야 합니다.';
    const MSG_CANCELLED = '등록을 취소했습니다.';
    const MSG_NETWORK = '서버와 통신할 수 없습니다. 잠시 후 다시 시도해야 합니다.';
    const MSG_FAILED = '요청을 처리하지 못했습니다.';
    const CODE_REPLACE_REGISTRATION_FAILED = 'NUDGE_REPLACE_REGISTRATION_FAILED';

    function el(prefix, suffix) {
        return document.getElementById(prefix + suffix);
    }

    /* ============================================================
       NudgeTimer — modal 안 [data-nudge-timer] span 에 mm:ss 카운트다운.
       0:00 도달 시 onExpire 콜백 + modal 자동 닫힘.
       ============================================================ */
    const _activeTimers = new WeakMap(); // modalEl → intervalId

    function _formatRemaining(ms) {
        if (ms <= 0) return '0:00';
        const totalSec = Math.ceil(ms / 1000);
        const m = Math.floor(totalSec / 60);
        const s = totalSec % 60;
        return m + ':' + String(s).padStart(2, '0');
    }

    function startTimer(modalEl, expiresAtIso, onExpire) {
        if (!modalEl || !expiresAtIso) return;
        stopTimer(modalEl);
        const span = modalEl.querySelector('[data-nudge-timer]');
        if (!span) return;
        const expiresAtMs = new Date(expiresAtIso).getTime();
        if (Number.isNaN(expiresAtMs)) return;

        const tick = () => {
            const remaining = expiresAtMs - Date.now();
            if (remaining <= 0) {
                span.textContent = '만료';
                stopTimer(modalEl);
                modalEl.hidden = true;
                if (typeof onExpire === 'function') onExpire();
                return;
            }
            span.textContent = _formatRemaining(remaining);
            // 1분 미만일 때 빨간색 강조
            span.style.color = remaining < 60_000 ? 'var(--n-orange)' : 'var(--n-text-muted)';
        };
        tick();
        const id = setInterval(tick, 1000);
        _activeTimers.set(modalEl, id);
    }

    function stopTimer(modalEl) {
        if (!modalEl) return;
        const id = _activeTimers.get(modalEl);
        if (id != null) {
            clearInterval(id);
            _activeTimers.delete(modalEl);
        }
        const span = modalEl.querySelector('[data-nudge-timer]');
        if (span) span.textContent = '';
    }

    /**
     * 만료 응답 인지 — NudgeNotFoundException(404) / NudgeSessionExpiredException(409+"만료").
     * 도메인 JS 의 fetch 응답 catch 에서 호출. true 반환 시 "만료" UX 처리하도록.
     */
    function isExpiredResponse(status, body) {
        if (status === 404) return true; // NudgeNotFound (pruner 가 정리한 후)
        // R2-5 — 만료 판정을 message 문자열('만료') 대신 안정 머신 code 로. backend handleNudgeSessionExpired 가 발행.
        if (status === 409 && body && body.code === 'NUDGE_SESSION_EXPIRED') return true;
        return false;
    }

    function open(payload, options) {
        const prefix = options.prefix;
        const notify = options.notify || {};
        const modal = el(prefix, 'Modal');
        const ui = {
            warning: el(prefix, 'Warning'),
            replaceNote: el(prefix, 'ReplaceNote'),
            list: el(prefix, 'ConflictsList'),
            proceedBtn: el(prefix, 'ProceedBtn'),
            replaceBtn: el(prefix, 'ReplaceBtn'),
            cancelBtn: el(prefix, 'CancelBtn'),
            typedWrap: el(prefix, 'TypedNameWrap'),
            typedExpected: el(prefix, 'TypedNameExpected'),
            typedInput: el(prefix, 'TypedName'),
            error: el(prefix, 'Error')
        };
        if (!modal || Object.values(ui).some(v => !v)) {
            if (notify.error) notify.error('중복 확인 창을 찾을 수 없습니다. 페이지를 새로 고쳐야 합니다.');
            return;
        }
        const contentBase = modal.dataset.confirmBaseUrl || '';
        const base = options.phase === 'intent' ? contentBase.replace(/\/nudge$/, '/intent-nudge') : contentBase;
        const url = action => base + '/' + encodeURIComponent(payload.nudgeId) + '/' + action;
        const replaceWrap = ui.replaceBtn.closest('.n-btn-tooltip-wrap') || ui.replaceBtn;
        let selected = null;
        ui.warning.hidden = false;
        ui.replaceNote.hidden = false;
        replaceWrap.hidden = false;

        modal.querySelectorAll('[data-cancel-note]').forEach(note => {
            note.hidden = note.dataset.cancelNote !== (options.cancelNote || 'beforeUpload');
        });
        renderCandidates(ui.list, payload.conflicts || [], prefix, entry => {
            selected = entry;
            ui.typedExpected.textContent = entry.displayName || '';
            ui.typedInput.value = '';
            ui.typedWrap.hidden = false;
            refreshReplace();
            ui.typedInput.focus();
        });
        selected = null;
        ui.typedWrap.hidden = true;
        ui.typedInput.value = '';
        ui.typedInput.oninput = refreshReplace;
        hideError();
        refreshReplace();
        setBusy(false);
        modal.hidden = false;

        startTimer(modal, payload.expiresAt, () => closeWith(notify.error, MSG_EXPIRED));

        ui.proceedBtn.onclick = () => submit('proceed', null);
        ui.replaceBtn.onclick = () => {
            if (!typedMatches()) return;
            submit('replace', new URLSearchParams({targetId: String(selected.id), typedName: ui.typedInput.value}));
        };
        ui.cancelBtn.onclick = async () => {
            setBusy(true);
            try {
                const resp = await fetch(url('cancel'), {method: 'POST', headers: {'Accept': 'application/json'}});
                const body = await resp.json().catch(() => ({}));
                // 다른 요청이 같은 창을 처리 중이면(409) 취소되지 않았다. 그 결과가 목록에 남으므로 사유를 알리고 닫는다.
                if (!resp.ok && !isExpiredResponse(resp.status, body)) {
                    closeWith(notify.error, body.message || MSG_FAILED);
                    return;
                }
            } catch (_) { /* 통신 실패: 세션과 업로드 트리는 만료 회수가 정리한다 */
            }
            closeWith(notify.info, MSG_CANCELLED);
        };

        function typedMatches() {
            return selected != null && ui.typedInput.value === (selected.displayName || '');
        }

        function refreshReplace() {
            const ready = typedMatches();
            ui.replaceBtn.disabled = !ready;
            replaceWrap.dataset.tooltipActive = ready ? 'false' : 'true';
        }

        function setBusy(busy) {
            ui.proceedBtn.disabled = busy;
            ui.cancelBtn.disabled = busy;
            ui.replaceBtn.disabled = busy || !typedMatches();
        }

        function showError(msg) {
            ui.error.textContent = msg;
            ui.error.hidden = false;
        }

        function hideError() {
            ui.error.textContent = '';
            ui.error.hidden = true;
        }

        function closeWith(sink, msg) {
            stopTimer(modal);
            modal.hidden = true;
            ui.proceedBtn.onclick = ui.replaceBtn.onclick = ui.cancelBtn.onclick = ui.typedInput.oninput = null;
            if (sink && msg) sink(msg);
        }

        // 영구 삭제는 끝났고 등록만 실패했다(D16). 지워진 후보는 다시 고를 수 없으므로 목록에서 뺀다.
        // 후보가 0 이 되면 영구 삭제할 대상이 없으므로 경고, [기존 영구 삭제 후 등록] 과 그 설명 줄도 숨긴다(HF30-4, 상자는 CSS :empty).
        function dropPurgedCandidate() {
            const item = ui.list.querySelector('[data-target-id="' + selected.id + '"]');
            if (item) item.remove();
            selected = null;
            ui.typedWrap.hidden = true;
            ui.typedInput.value = '';
            if (!ui.list.children.length) {
                ui.list.innerHTML = '';
                ui.warning.hidden = true;
                ui.replaceNote.hidden = true;
                replaceWrap.hidden = true;
            }
        }

        async function submit(action, form) {
            hideError();
            setBusy(true);
            try {
                const resp = await fetch(url(action), {
                    method: 'POST', headers: {'Accept': 'application/json'}, body: form
                });
                const body = await resp.json().catch(() => ({}));
                if (resp.ok) {
                    closeWith(null, null);
                    options.onDone(body);
                    return;
                }
                if (isExpiredResponse(resp.status, body)) {
                    closeWith(notify.error, MSG_EXPIRED);
                    return;
                }
                if (body.code === CODE_REPLACE_REGISTRATION_FAILED) dropPurgedCandidate();
                showError(body.message || MSG_FAILED);
            } catch (_) {
                showError(MSG_NETWORK);
            }
            setBusy(false);
        }
    }

    function trashedAtHtml(entry) {
        if (!entry.trashedAt || !entry.trashedAtText) return '-';
        return '<time datetime="' + UiUtil.escapeHtml(entry.trashedAt) + '">' + UiUtil.escapeHtml(entry.trashedAtText) + '</time>';
    }

    // 후보 줄 = 전역 n-option-row. 이름 칸은 자원명 입력이 맞춰야 하는 displayName 이다(HF30-4, ISO 는 OS 와 파일 이름).
    // 버전(ISO 는 경로)과 휴지통 이동 시각은 뒤의 보조 글자로 둔다. 시각 글자는 서버가 형식을 맞춰 준 값을 그대로 쓴다.
    function renderCandidates(list, conflicts, prefix, onSelect) {
        list.innerHTML = '';
        conflicts.forEach(entry => {
            const inputId = prefix + 'Target' + entry.id;
            const li = document.createElement('li');
            li.className = 'n-option-row';
            li.dataset.targetId = entry.id;
            li.innerHTML =
                '<input type="radio" name="' + prefix + 'Target" id="' + UiUtil.escapeHtml(inputId) + '" value="' + UiUtil.escapeHtml(String(entry.id)) + '">' +
                '<label for="' + UiUtil.escapeHtml(inputId) + '">' +
                '<span class="n-option-title">' + UiUtil.escapeHtml(entry.displayName || entry.name || '') + '</span> ' +
                '<span class="cm-note">' + UiUtil.escapeHtml(entry.detail || '') + ', 휴지통 이동 ' + trashedAtHtml(entry) + '</span>' +
                '</label>';
            li.querySelector('input').addEventListener('change', () => onSelect(entry));
            list.appendChild(li);
        });
    }

    window.NudgeModal = {open: open};
    window.NudgeTimer = {start: startTimer, stop: stopTimer, isExpiredResponse: isExpiredResponse};
})();
