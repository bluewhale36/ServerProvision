/* ============================================================
   R1-4-1 hotfix — 전역 에러 modal.

   목적 : backend 가 응답한 모든 에러 (XHR JSON 또는 SSR redirect 시
   error.html) 를 공통된 시각 스타일로 표시. 옛 alert(msg) 를 대체.

   사용 :
     1) AsyncSubmitResult.onRejected(form, status, payload) — confirm-modal-base 의
        submitAsync 가 거절 응답 받으면 호출. payload.message 를 modal 로 표시.
     2) window.ErrorModal.show({title, message, status}) — 다른 호출자가 직접 표시 가능.
     3) window.fetch 응답에 일관 처리하려면 caller 가 자체 catch + ErrorModal.show 호출.

   백엔드 응답 포맷 :
     { "message": "...", "status": 409, "errors": [...] }
     ApiExceptionHandler 가 보낸 ApiErrorResponse. message 필드 표시.
   ============================================================ */
(function () {
    'use strict';

    const STATUS_LABEL = {
        400: '잘못된 요청', 401: '인증 필요', 403: '권한 부족', 404: '대상 없음',
        409: '충돌', 422: '검증 실패', 423: '잠금', 500: '서버 오류', 502: '서버 응답 없음'
    };

    function ensureModalRoot() {
        let root = document.getElementById('global-error-modal');
        if (root) return root;
        root = document.createElement('div');
        root.id = 'global-error-modal';
        root.className = 'gem-overlay';
        root.hidden = true;
        root.innerHTML = '' +
            '<div class="gem-backdrop" data-close></div>' +
            '<div class="gem-card" role="alertdialog" aria-modal="true">' +
            '  <h2 class="gem-title" data-title>오류가 발생했어요</h2>' +
            '  <div class="gem-status" data-status></div>' +
            '  <p class="gem-message" data-message></p>' +
            '  <div class="gem-actions">' +
            '    <button type="button" class="n-btn n-btn-primary" data-close>확인</button>' +
            '  </div>' +
            '</div>';
        document.body.appendChild(root);
        root.querySelectorAll('[data-close]').forEach(el => {
            el.addEventListener('click', hide);
        });
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && !root.hidden) hide();
        });
        return root;
    }

    function show({title, message, status}) {
        const root = ensureModalRoot();
        root.querySelector('[data-title]').textContent = title || '오류가 발생했어요';
        const statusEl = root.querySelector('[data-status]');
        if (status) {
            statusEl.textContent = 'HTTP ' + status + (STATUS_LABEL[status] ? ' · ' + STATUS_LABEL[status] : '');
            statusEl.hidden = false;
        } else {
            statusEl.textContent = '';
            statusEl.hidden = true;
        }
        root.querySelector('[data-message]').textContent = message || '요청을 처리하지 못했어요.';
        root.hidden = false;
        // focus confirm button — 키보드 사용자 친화
        const btn = root.querySelector('[data-close].n-btn');
        if (btn) btn.focus();
    }

    function hide() {
        const root = document.getElementById('global-error-modal');
        if (root) root.hidden = true;
    }

    // confirm-modal-base 의 submitAsync 가 호출하는 AsyncSubmitResult 통합 핸들러.
    // 페이지별로 override 한 경우 (예: trash-action) 는 그대로 보존 — 없을 때만 본 modal 사용.
    if (!window.AsyncSubmitResult) {
        window.AsyncSubmitResult = {
            onSuccess: () => window.location.reload(),
            onRejected: (_form, status, payload) => {
                const msg = (payload && payload.message) || '요청이 거절되었어요.';
                show({message: msg, status: status});
            },
            onNetworkError: () => {
                show({message: '서버와 통신할 수 없어요. 잠시 후 다시 시도해주세요.', status: 0});
            }
        };
    }

    window.ErrorModal = {show, hide};
})();
