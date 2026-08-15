/* ============================================================
   서버 상세 — 세팅 정의서 할당 · 재할당 제출(U3-1 · U3-2-a, XHR JSON).
   ─────────────────────────────────────────────────────────
   POST {data-action} 으로 {definitionId} 를 보내고, 성공(2xx)이면 상세를 새로 고쳐
   스냅샷·계획 rail 을 반영한다. 할당과 재할당은 엔드포인트만 다르고 제출 흐름이 동일하므로
   form[data-action] 셀렉터로 함께 처리한다
   (복붙 대신 단일 핸들러 — 다른 폼은 th:action(=action 속성) 이라 매칭되지 않는다).
   실패는 window.FormError(단일 진입점)로 배너/인라인 필드 에러에 매핑한다(alert 미사용).

   U3-5-b — 정의서를 고르는 자리가 <select> 에서 모달로 옮겨갔다. 이 파일은 여전히 제출만 맡되
   두 가지를 느슨하게 잡는다 :
     · data-action 을 제출 시점에 읽는다 — 모달 하나를 할당 · 재할당이 함께 쓰므로 대상 URL 이
       열 때마다 바뀐다. 바인딩 시점에 고정하면 처음 연 흐름의 URL 로 계속 보내게 된다.
     · 고른 값은 select 또는 hidden input 어느 쪽에서든 읽는다 — 고르는 UI 가 무엇이든
       [name=definitionId] 라는 계약만 지키면 이 파일은 바뀌지 않는다.
   ============================================================ */
(function () {
    'use strict';

    document.querySelectorAll('form[data-action]').forEach((form) => {
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const action = form.dataset.action;
            if (window.FormError) {
                window.FormError.clear(form);
            }
            const field = form.querySelector('[name="definitionId"]');
            const raw = field ? field.value : '';
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
