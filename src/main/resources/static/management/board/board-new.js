/**
 * MK2 WAVE 1 — BoardModel 신규 등록 폼의 XHR 제출 + nudge modal 연결.
 *
 * 흐름:
 *  - submit → fetch POST /management/board (Accept: application/json)
 *  - 200 + { redirect } → 그 URL 로 navigate
 *  - 400 + ApiErrorResponse + fieldErrors → FormError.renderResponse 로 inline 메시지
 *  - 409 + { code: NUDGE_REQUIRED, nudgeId, conflicts } → NudgeModal.handle
 */
(function () {
    'use strict';
    const TAG = '[boardNew]';

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.getElementById('boardModelForm');
        if (!form) return;

        const banner = form.querySelector('.n-form-banner');

        function showBanner(msg) {
            if (!banner) {
                console.error(TAG, msg);
                return;
            }
            banner.hidden = false;
            banner.textContent = msg;
        }

        function hideBanner() {
            if (banner) banner.hidden = true;
        }

        form.addEventListener('submit', evt => {
            evt.preventDefault();
            hideBanner();
            if (window.FormError) window.FormError.clear(form);

            const body = new URLSearchParams(new FormData(form));
            fetch(form.action, {
                method: 'POST',
                headers: {'Accept': 'application/json'},
                body: body
            })
                .then(resp => resp.text().then(text => {
                    let parsed = null;
                    try {
                        parsed = text ? JSON.parse(text) : null;
                    } catch (_) { /* ignore */
                    }
                    return {status: resp.status, ok: resp.ok, body: parsed};
                }))
                .then(({status, ok, body}) => {
                    if (ok) {
                        window.location.href = (body && body.redirect) || '/management/board';
                        return;
                    }
                    if (status === 409 && body && body.code === 'NUDGE_REQUIRED' && body.nudgeId) {
                        window.NudgeModal.handle(body, {
                            baseUrl: '/management/board/nudge',
                            listUrl: '/management/board',
                            toastKey: 'board.toast',
                            onError: showBanner,
                            afterCancel: () => showBanner('등록을 취소했습니다.')
                        });
                        return;
                    }
                    if (body && window.FormError) {
                        window.FormError.renderResponse(body, {root: form});
                        return;
                    }
                    showBanner((body && body.message) || ('HTTP ' + status));
                })
                .catch(err => {
                    console.error(TAG, err);
                    showBanner(err.message || '요청 처리 중 오류가 발생했습니다.');
                });
        });
    });
})();
