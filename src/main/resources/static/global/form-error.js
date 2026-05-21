/* ============================================================
   FormError — JSON XHR 폼의 에러 응답을 폼 마크업에 일관 매핑.
   ─────────────────────────────────────────────────────────────
   서버 ApiErrorResponse 형식
       {
         "message"      : "사람이 읽을 메시지",
         "fieldErrors"  : [{ "field": "version", "message": "..." }]   // optional / nullable
       }
   를 받아 :
     - fieldErrors 가 있으면 data-error-field="<DTO 필드명>" 요소에 .has-error 토글 +
       .field-error-message 자식 div 를 주입
     - message 는 폼 상단 .n-form-banner 에 노출 (없으면 무시)
   alert() 사용을 모든 폼에서 제거하고 본 헬퍼를 단일 진입점으로 쓴다.
   ============================================================ */
(function (global) {
    'use strict';

    const ERROR_CLASS = 'has-error';
    const MESSAGE_CLASS = 'field-error-message';
    const BANNER_SELECTOR = '.n-form-banner';

    /**
     * 모든 .has-error / .field-error-message 흔적을 제거한다.
     * @param {HTMLElement} root - 정리 범위 (보통 form). 미지정 시 document.
     */
    function clear(root) {
        const scope = root || document;
        scope.querySelectorAll('.' + ERROR_CLASS).forEach(el => el.classList.remove(ERROR_CLASS));
        scope.querySelectorAll('.' + MESSAGE_CLASS).forEach(el => el.remove());
        scope.querySelectorAll(BANNER_SELECTOR).forEach(el => {
            el.textContent = '';
            el.hidden = true;
        });
    }

    /**
     * fieldErrors[] 를 마크업에 매핑한다. 매칭 안되는 필드는 banner 로 폴백.
     * @param {Array} fieldErrors - [{field, message}, ...]
     * @param {HTMLElement} root - 매핑 범위 (보통 form)
     * @returns {{overflow: Array<string>, mappedCount: number}}
     *   overflow : banner 로 폴백할 메시지. mappedCount : 인라인 매핑 성공 건수.
     */
    function applyFieldErrors(fieldErrors, root) {
        const scope = root || document;
        const overflow = [];
        let mappedCount = 0;
        for (const fe of fieldErrors || []) {
            if (!fe || !fe.field) {
                if (fe && fe.message) overflow.push(fe.message);
                continue;
            }
            const target = scope.querySelector('[data-error-field="' + cssEscape(fe.field) + '"]');
            if (!target) {
                overflow.push((fe.field ? fe.field + ': ' : '') + (fe.message || ''));
                continue;
            }
            target.classList.add(ERROR_CLASS);
            // 메시지 anchor : input 이 flex / grid 컨테이너 안일 경우 input afterend 로 넣으면
            // 컨테이너 폭을 잠식한다. .n-form-group 을 stable anchor 로 사용해 항상 form-group 끝에 부착.
            const formGroup = target.closest('.n-form-group');
            const anchor = formGroup || target.parentElement || target;
            // 동일 필드의 기존 메시지 제거 후 새 메시지 1건 부착.
            anchor.querySelectorAll(':scope > .' + MESSAGE_CLASS).forEach(el => el.remove());
            const note = document.createElement('div');
            note.className = MESSAGE_CLASS;
            note.textContent = fe.message || '';
            if (formGroup) {
                formGroup.appendChild(note);
            } else {
                target.insertAdjacentElement('afterend', note);
            }
            mappedCount++;
        }
        return { overflow: overflow, mappedCount: mappedCount };
    }

    /**
     * banner 슬롯에 message + 매핑 폴백된 overflow 를 노출한다.
     * @param {HTMLElement} root
     * @param {string} message
     * @param {Array<string>} overflow
     */
    function showBanner(root, message, overflow) {
        const scope = root || document;
        const banner = scope.querySelector(BANNER_SELECTOR);
        const lines = [];
        if (message) lines.push(message);
        for (const m of overflow || []) {
            if (m && lines.indexOf(m) === -1) lines.push(m);
        }
        if (!banner) {
            // banner 가 없다면 console 만 찍고 끝낸다 (alert 금지).
            if (lines.length) console.warn('[FormError]', lines.join(' / '));
            return;
        }
        if (lines.length === 0) {
            banner.textContent = '';
            banner.hidden = true;
            return;
        }
        banner.textContent = lines.join(' · ');
        banner.hidden = false;
    }

    /**
     * 응답 본문을 받아 폼에 일관 렌더한다.
     * @param {Object} body - {message, fieldErrors?} 형태. null/undefined 안전.
     * @param {Object} [options] - {root: HTMLElement} 매핑 스코프.
     */
    function renderResponse(body, options) {
        const opts = options || {};
        const root = opts.root || document;
        clear(root);
        const data = body || {};
        const result = applyFieldErrors(data.fieldErrors, root);
        // 모든 fieldErrors 가 인라인 매핑에 성공했고 overflow 도 없으면 banner 의 data.message 를
        // 노출하지 않는다 (필드 직결 충돌 예외처럼 message 와 fieldErrors[0].message 가 동일한
        // 경우의 중복 표시 회피). overflow 가 있으면 message + overflow 를 banner 로.
        const bannerMessage = (result.mappedCount > 0 && result.overflow.length === 0)
                ? null
                : data.message;
        showBanner(root, bannerMessage, result.overflow);
    }

    /**
     * fetch Response → renderResponse 의 편의 진입점. body 가 JSON 이 아니면 status 를 banner 로.
     * @param {Response} response
     * @param {Object} [options]
     */
    async function renderFromResponse(response, options) {
        const opts = options || {};
        let body = null;
        try {
            body = await response.clone().json();
        } catch (_) {
            body = { message: '서버 응답을 해석할 수 없습니다 (HTTP ' + response.status + ').' };
        }
        renderResponse(body, opts);
        return body;
    }

    /**
     * CSS.escape 폴백 (구형 브라우저 안전망).
     */
    function cssEscape(value) {
        if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
            return CSS.escape(value);
        }
        return String(value).replace(/[^a-zA-Z0-9_-]/g, ch => '\\' + ch);
    }

    global.FormError = {
        clear,
        renderResponse,
        renderFromResponse
    };
})(window);
