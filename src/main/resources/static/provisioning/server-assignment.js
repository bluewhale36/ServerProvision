/* ============================================================
   서버 상세 — 세팅 정의서 할당 · 재할당 폼(U3-1 · U3-2-a, XHR JSON).
   ─────────────────────────────────────────────────────────
   POST {data-action} 으로 {definitionId} 를 보내고, 성공(2xx)이면 상세를 새로 고쳐
   스냅샷·계획 rail 을 반영한다. 할당(assignment-form)과 재할당(reassignment-form)은
   엔드포인트만 다르고 제출 흐름이 동일하므로 form[data-action] 셀렉터로 함께 처리한다
   (복붙 대신 단일 핸들러 — 다른 폼은 th:action(=action 속성) 이라 매칭되지 않는다).
   실패는 window.FormError(단일 진입점)로 배너/인라인 필드 에러에 매핑한다(alert 미사용).
   ============================================================ */
(function () {
    'use strict';

    document.querySelectorAll('form[data-action]').forEach((form) => {
        const action = form.dataset.action;

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (window.FormError) {
                window.FormError.clear(form);
            }
            const select = form.querySelector('select[name="definitionId"]');
            const raw = select ? select.value : '';
            const definitionId = raw ? Number(raw) : null;
            const submitBtn = form.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.disabled = true;
            }
            try {
                const response = await fetch(action, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                    body: JSON.stringify({definitionId: definitionId})
                });
                if (response.ok) {
                    window.location.reload();
                    return;
                }
                if (window.FormError) {
                    await window.FormError.renderFromResponse(response, {root: form});
                }
            } catch (err) {
                if (window.FormError) {
                    window.FormError.renderResponse(
                        {message: '요청을 보내지 못했습니다. 잠시 후 다시 시도해주세요.'}, {root: form});
                }
            } finally {
                if (submitBtn) {
                    submitBtn.disabled = false;
                }
            }
        });
    });
})();
