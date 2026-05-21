/**
 * MK2 — nudge modal 공통 처리 모듈.
 *
 * 사용법:
 *   NudgeModal.handle(payload, {
 *       baseUrl: '/management/os/image-nudge',  // confirm endpoint prefix
 *       listUrl: '/management/os',              // 종결 후 redirect 기본값
 *       toastKey: 'os.image.toast',             // sessionStorage 키 (선택)
 *       onError: (msg) => { ... },              // 표시 콜백
 *       afterCancel: () => { ... }              // cancel 종결 후 콜백 (선택)
 *   });
 *
 * 본 함수는 fragments/management/nudge-modal.html (prefix='nudge') 의 element id 를 그대로 잡는다.
 * 다른 prefix 사용 시 options.prefix 로 지정 가능.
 *
 * confirm 엔드포인트 응답이 200 + { redirect } JSON 이면 그 URL 로 이동.
 * 204 No Content (cancel) 면 afterCancel 호출. 실패 시 onError 호출.
 */
(function () {
    'use strict';

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

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
                span.textContent = '만료됨';
                stopTimer(modalEl);
                modalEl.hidden = true;
                if (typeof onExpire === 'function') onExpire();
                return;
            }
            span.textContent = '⏱ ' + _formatRemaining(remaining);
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
        if (status === 409 && body && typeof body.message === 'string'
            && body.message.indexOf('만료') >= 0) return true;
        return false;
    }

    function handle(payload, options) {
        options = options || {};
        const prefix = options.prefix || 'nudge';
        const modal = el(prefix, 'Modal');
        if (!modal) {
            console.error('[NudgeModal] element 없음:', prefix + 'Modal');
            if (options.onError) options.onError(payload.message || '동일한 자원이 이미 존재합니다.');
            return;
        }

        const baseUrl = options.baseUrl || modal.dataset.confirmBaseUrl;
        const list = el(prefix, 'ConflictsList');
        const proceedBtn = el(prefix, 'ProceedBtn');
        const replaceBtn = el(prefix, 'ReplaceBtn');
        const cancelBtn = el(prefix, 'CancelBtn');
        const backdrop = el(prefix, 'Backdrop');

        list.innerHTML = '';
        let selectedTargetId = null;
        replaceBtn.disabled = true;
        // S5-3-2 — wrapper 의 tooltip 활성 상태 reset (modal 재진입 시 tooltip 다시 보이도록).
        const replaceBtnWrap = replaceBtn.closest('.n-btn-tooltip-wrap');
        if (replaceBtnWrap) delete replaceBtnWrap.dataset.tooltipActive;

        (payload.conflicts || []).forEach(c => {
            const li = document.createElement('li');
            li.style.padding = '10px 12px';
            li.style.borderBottom = '1px solid var(--n-border)';
            li.style.cursor = 'pointer';
            li.dataset.targetId = c.id;
            const hashFragment = c.hash ? (' · ' + escapeHtml(String(c.hash).slice(0, 16)) + '…') : '';
            li.innerHTML =
                '<div style="font-weight: 600;">' + escapeHtml(c.name || '') + ' ' + escapeHtml(c.version || '') + '</div>' +
                '<div style="font-size: 12px; color: var(--n-text-muted);">' +
                'id=' + c.id + ' · ' + escapeHtml(c.state || '') + hashFragment + '</div>';
            li.addEventListener('click', () => {
                Array.from(list.children).forEach(item => item.style.background = '');
                li.style.background = 'var(--n-bg-soft, #eef)';
                selectedTargetId = c.id;
                replaceBtn.disabled = false;
                // S5-3-2 — 자원 선택 후 tooltip 침묵 (활성 버튼에 hint 노이즈 회피).
                if (replaceBtnWrap) replaceBtnWrap.dataset.tooltipActive = 'false';
            });
            list.appendChild(li);
        });

        modal.hidden = false;

        // MK2 — TTL countdown 시작. 0:00 도달 시 modal 자동 닫힘 + 안내.
        startTimer(modal, payload.expiresAt, () => {
            if (options.onError) options.onError('nudge 세션이 만료되었습니다. 다시 시도해주세요.');
        });

        proceedBtn.onclick = () => confirmNudge(baseUrl, payload.nudgeId, 'proceed', null, options);
        replaceBtn.onclick = () => {
            if (selectedTargetId == null) return;
            confirmNudge(baseUrl, payload.nudgeId, 'replace', selectedTargetId, options);
        };
        const onCancel = () => confirmNudge(baseUrl, payload.nudgeId, 'cancel', null, options);
        cancelBtn.onclick = onCancel;
        backdrop.onclick = onCancel;
    }

    function confirmNudge(baseUrl, nudgeId, action, targetId, options) {
        let url = baseUrl + '/' + encodeURIComponent(nudgeId) + '/' + action;
        if (action === 'replace' && targetId != null) {
            url += '?targetId=' + encodeURIComponent(targetId);
        }
        fetch(url, {method: 'POST', headers: {'Accept': 'application/json'}})
            .then(resp => resp.json().catch(() => ({})).then(body => ({status: resp.status, ok: resp.ok, body})))
            .then(({status, ok, body}) => {
                if (!ok) {
                    // MK2 — 만료 응답은 modal 강제 닫힘 + 명시적 안내.
                    if (isExpiredResponse(status, body)) {
                        hide(options.prefix || 'nudge');
                        if (options.onError) options.onError('nudge 세션이 만료되었습니다. 다시 시도해주세요.');
                        return;
                    }
                    throw new Error((body && body.message) || ('HTTP ' + status));
                }
                hide(options.prefix || 'nudge');
                if (action === 'cancel') {
                    if (options.afterCancel) options.afterCancel();
                    else if (options.onError) options.onError('등록을 취소했습니다.');
                    return;
                }
                const redirect = (body && body.redirect) || options.listUrl || '/';
                if (options.toastKey) {
                    try {
                        sessionStorage.setItem(options.toastKey,
                            action === 'replace'
                                ? '기존 자원을 영구 삭제하고 신규 자원을 등록했습니다.'
                                : '신규 자원을 등록했습니다.');
                    } catch (_) { /* ignore */
                    }
                }
                window.location.href = redirect;
            })
            .catch(err => {
                hide(options.prefix || 'nudge');
                if (options.onError) options.onError(err.message || 'nudge 처리에 실패했습니다.');
            });
    }

    function hide(prefix) {
        const modal = el(prefix, 'Modal');
        if (modal) {
            stopTimer(modal);
            modal.hidden = true;
        }
    }

    window.NudgeModal = {handle: handle, hide: hide};
    window.NudgeTimer = {start: startTimer, stop: stopTimer, isExpiredResponse: isExpiredResponse};
})();
