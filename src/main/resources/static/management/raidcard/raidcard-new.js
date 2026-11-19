/**
 * MA7 — RAID 카드 신규 등록 폼의 XHR 제출 (board-new.js 선례).
 *
 * 흐름:
 *  - submit → fetch POST /management/raidcard (Accept: application/json)
 *  - 200 + { redirect } → 그 URL 로 navigate
 *  - 400 + ApiErrorResponse + fieldErrors → FormError.renderResponse 로 inline 메시지
 *  - 409 → 모델명 칸에 상태별 다음 행동 안내(HF30-1 — 메인보드 HF26 과 같은 규칙, 중복 확인 창은 없앴다)
 */
(function () {
    'use strict';
    const TAG = '[raidCardNew]';

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.getElementById('raidCardForm');
        if (!form) return;

        const banner = form.querySelector('.n-form-banner');
        const showBanner = msg => UiUtil.showBanner(banner, msg);
        const hideBanner = () => UiUtil.hideBanner(banner);

        form.addEventListener('submit', evt => {
            evt.preventDefault();
            send();
        });

        function send() {
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
                        window.location.href = (body && body.redirect) || '/management/raidcard';
                        return;
                    }
                    if (body && window.FormError) {
                        window.FormError.renderResponse(body, {root: form});
                        return;
                    }
                    showBanner((body && body.message) || '요청을 처리하지 못했습니다.');
                })
                .catch(err => {
                    console.error(TAG, err);
                    showBanner('서버와 통신할 수 없습니다. 잠시 후 다시 시도해야 합니다.');
                });
        }
    });
})();
