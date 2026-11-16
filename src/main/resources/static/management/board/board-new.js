/**
 * MK2 WAVE 1 — BoardModel 신규 등록 폼의 XHR 제출.
 *
 * 흐름:
 *  - submit → fetch POST /management/board (Accept: application/json)
 *  - 200 + { redirect } → 그 URL 로 navigate
 *  - 400 · 409 + ApiErrorResponse → FormError.renderResponse 로 inline 메시지
 *    (HF26 — 같은 (제조사, 모델명) 이 있으면 409 가 모델명 칸에 "복구 · Deprecated 해제" 안내를 싣는다. 중복 확인 창은 없앴다)
 */
(function () {
    'use strict';
    const TAG = '[boardNew]';

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.getElementById('boardModelForm');
        if (!form) return;

        const banner = form.querySelector('.n-form-banner');
        const showBanner = msg => UiUtil.showBanner(banner, msg);
        const hideBanner = () => UiUtil.hideBanner(banner);

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
