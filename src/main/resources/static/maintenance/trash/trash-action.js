/* ============================================================
   S5-2-3-1 — 휴지통 액션 결과 안내 (async submit handler).
   ─────────────────────────────────────────────────────────────
   휴지통 페이지의 4 form (restore / extend / purge / clear-ghost) 은
   data-async-submit 마커 보유 — confirm-modal-base 의 submitAsync 가 fetch 로 전송한다.
   본 모듈은 그 응답을 받아 (성공 → reload / 거절 → modal 안내) 처리한다.

   설계 의도 : 부모-자식 lifecycle cascade 거절 (ChildLifecycleBlockedByParentException) 같은
   409 응답이 raw JSON 페이지로 노출되는 사고를 막는다.
   ============================================================ */
(function () {
    'use strict';
    const PREFIX = 'trashResult';

    function openResult(opts) {
        const modal     = document.getElementById(PREFIX + 'Modal');
        const titleEl   = document.getElementById(PREFIX + 'Title');
        const messageEl = document.getElementById(PREFIX + 'Message');
        const hintEl    = document.getElementById(PREFIX + 'Hint');
        const okBtn     = document.getElementById(PREFIX + 'OkBtn');
        const backdrop  = document.getElementById(PREFIX + 'Backdrop');
        if (!modal || !okBtn) {
            alert(opts.message || '알림');
            return;
        }
        titleEl.textContent = opts.title || '알림';
        messageEl.textContent = opts.message || '';
        if (hintEl) {
            if (opts.hint) {
                hintEl.textContent = opts.hint;
                hintEl.hidden = false;
            } else {
                hintEl.hidden = true;
                hintEl.textContent = '';
            }
        }
        okBtn.className = 'n-btn ' + (opts.confirmClass || 'n-btn-primary');
        okBtn.textContent = opts.confirmLabel || '확인';
        modal.hidden = false;
        okBtn.focus();

        const close = () => {
            modal.hidden = true;
            okBtn.onclick = null;
            if (backdrop) backdrop.onclick = null;
            document.removeEventListener('keydown', onKey);
            if (typeof opts.onClose === 'function') opts.onClose();
        };
        const onKey = (ev) => { if (ev.key === 'Escape' || ev.key === 'Enter') close(); };
        okBtn.onclick = close;
        if (backdrop) backdrop.onclick = close;
        document.addEventListener('keydown', onKey);
    }

    /** 4xx/5xx 응답을 사용자 친화 안내로 변환. payload.message 가 있으면 그대로 사용. */
    function describeRejection(status, payload) {
        const baseMessage = (payload && payload.message) ? payload.message : null;
        if (status === 409) {
            return {
                title: '액션이 거절되었어요',
                message: baseMessage || '현재 상태에서는 이 액션을 수행할 수 없어요.',
                confirmClass: 'n-btn-outline-info'
            };
        }
        if (status === 400) {
            return {
                title: '입력을 확인해주세요',
                message: baseMessage || '요청 형식이 올바르지 않아요.',
                confirmClass: 'n-btn-outline-info'
            };
        }
        if (status === 404) {
            return {
                title: '자원을 찾을 수 없어요',
                message: baseMessage || '대상 자원이 이미 없어졌거나 식별자가 잘못됐어요.',
                hint: '페이지를 새로고침해보세요.',
                confirmClass: 'n-btn-primary',
                onClose: () => window.location.reload()
            };
        }
        return {
            title: '서버 오류',
            message: baseMessage || ('요청 처리 중 오류가 발생했어요. (HTTP ' + status + ')'),
            confirmClass: 'n-btn-outline-danger'
        };
    }

    window.AsyncSubmitResult = {
        onSuccess: () => { window.location.reload(); },
        onRejected: (_form, status, payload) => {
            openResult(describeRejection(status, payload));
        },
        onNetworkError: () => {
            openResult({
                title: '연결 오류',
                message: '서버와 통신할 수 없어요. 네트워크 상태를 확인하고 다시 시도해주세요.',
                confirmClass: 'n-btn-outline-danger'
            });
        }
    };
})();
