/**
 * MK2 WAVE 1 — OSMetadata 신규 등록 폼의 XHR 제출.
 *
 * 흐름:
 *  - submit → fetch POST /management/os (Accept: application/json)
 *  - 200 + { redirect } → 그 URL 로 navigate
 *  - 400 + ApiErrorResponse + fieldErrors → FormError.renderResponse 로 inline 메시지
 *  - 409 → 배너에 상태별 다음 행동 안내(HF30-1 — 같은 OS 버전이 휴지통 · Deprecated 에 있으면 복구 · 해제 안내, 중복 확인 창은 없앴다)
 */
(function () {
    'use strict';
    const TAG = '[osMetadataNew]';

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.getElementById('osMetadataForm');
        if (!form) return;

        const banner = form.querySelector('.n-form-banner');

        // E4-1-b-2 D-21 — 고른 줄의 지원 메이저를 숨은 rhelRelease 로 싣고, 버전 칸에 앞자리를 채운다.
        const osSelect = document.getElementById('osName');
        const releaseInput = document.getElementById('rhelRelease');
        const versionInput = document.getElementById('osVersion');
        const versionHint = document.getElementById('osVersionHint');
        const versionExample = document.getElementById('osVersionExample');
        osSelect.addEventListener('change', () => {
            const opt = osSelect.selectedOptions[0];
            const release = opt ? (opt.getAttribute('data-rhel-release') || '') : '';
            const prefix = opt ? (opt.getAttribute('data-version-prefix') || '') : '';
            releaseInput.value = release;
            versionHint.hidden = !prefix;
            if (prefix) {
                versionExample.textContent = prefix + '0';
                if (!versionInput.value || /^\d+\.$/.test(versionInput.value)) versionInput.value = prefix;
            }
        });
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
                    return {status: resp.status, ok: resp.ok, body: parsed, raw: text};
                }))
                .then(({status, ok, body}) => {
                    if (ok) {
                        if (body && body.redirect) {
                            window.location.href = body.redirect;
                        } else {
                            window.location.href = '/management/os';
                        }
                        return;
                    }
                    // 400 fieldErrors
                    if (body && window.FormError) {
                        window.FormError.renderResponse(body, {root: form});
                        return;
                    }
                    showBanner((body && body.message) || '요청을 처리하지 못했습니다. 다시 시도해야 합니다.');
                })
                .catch(err => {
                    console.error(TAG, err);
                    showBanner('요청을 처리하지 못했습니다. 다시 시도해야 합니다.');
                });
        });
    });
})();
