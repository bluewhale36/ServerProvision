/* ============================================================
   서버 상세 — 정의서 선택 모달의 열기 · 고르기 (U3-5-b).
   ─────────────────────────────────────────────────────────
   이 파일은 고르는 일까지만 한다. 제출은 server-assignment.js 가 맡는다 — 모달 폼도
   data-action 을 가진 form 이라 그 파일의 단일 핸들러가 그대로 잡는다. 여기서 fetch 를 한 번 더
   쓰면 에러 배너 · 성공 후 리로드가 두 곳으로 갈라진다.

   할당과 재할당이 모달 하나를 함께 쓴다(DEC-E). 다른 것은 셋뿐이라 여는 버튼이 data-* 로 넘긴다 :
     · data-picker-title        — 모달 제목
     · data-picker-submit-label — 제출 버튼 문구
     · data-submit-url          — form 의 data-action 에 실릴 대상

   좌측 목록의 클릭은 컨테이너 위임으로 듣는다. 내용이 나중에 채워지므로 직접 바인딩하면
   교체된 행을 놓친다(U3-4 서버 넣기 모달과 같은 이유).

   잠긴 정의서도 클릭된다 — 이것이 요구의 핵심이다. 잠긴 것은 [할당] 을 열지 않을 뿐,
   고르면 우측에 사유와 내용이 함께 뜬다.
   ============================================================ */
(function () {
    'use strict';

    const PROMPT = '왼쪽에서 정의서를 고르세요';

    function wire() {
        const overlay = document.getElementById('definitionPicker');
        if (!overlay) return;   // 회수된 서버 — 모달 자체가 렌더되지 않는다(DEC-D)

        const form = document.getElementById('definition-picker-form');
        const body = document.getElementById('definitionPickerBody');
        const title = document.getElementById('definitionPickerTitle');
        const status = document.getElementById('definitionPickerStatus');
        const submit = document.getElementById('definitionPickerSubmit');
        const choice = document.getElementById('definitionPickerChoice');
        const openers = document.querySelectorAll('#openAssignPicker, #openReassignPicker');
        if (!form || !body || !openers.length) return;

        let loaded = false;

        /* 고른 것이 없는 상태로 되돌린다 — 모달을 다시 열 때 지난 선택이 남아 있으면
           눈에는 아무것도 안 골라 보이는데 [할당] 만 열려 있는 어긋남이 생긴다. */
        function clearSelection() {
            body.querySelectorAll('.n-miller-selected')
                .forEach((el) => el.classList.remove('n-miller-selected'));
            body.querySelectorAll('.n-miller-detail-panel.active')
                .forEach((el) => el.classList.remove('active'));
            const empty = body.querySelector('.n-miller-empty');
            if (empty) empty.classList.remove('hidden');
            choice.value = '';
            status.textContent = PROMPT;
            status.classList.remove('n-picker-count-blocked');
            submit.disabled = true;
            submit.removeAttribute('title');
        }

        function select(item) {
            const id = item.getAttribute('data-definition-id');
            const name = item.getAttribute('data-definition-name');
            const reason = item.getAttribute('data-block-reason');

            body.querySelectorAll('.n-miller-selected')
                .forEach((el) => el.classList.remove('n-miller-selected'));
            item.classList.add('n-miller-selected');

            body.querySelectorAll('.n-miller-detail-panel.active')
                .forEach((el) => el.classList.remove('active'));
            const panel = document.getElementById('definition-panel-' + id);
            if (panel) panel.classList.add('active');
            const empty = body.querySelector('.n-miller-empty');
            if (empty) empty.classList.add('hidden');

            /* 잠긴 것을 골랐을 때도 선택 표시는 남긴다 — 어느 것을 보고 있는지가 사라지면
               사유를 읽는 동안 맥락을 잃는다. 다만 값은 싣지 않아 제출할 수 없게 한다.
               문구는 서버가 만든 사유 그대로다(화면과 서버 가드가 같은 문자열). */
            if (reason) {
                choice.value = '';
                status.textContent = reason;
                status.classList.add('n-picker-count-blocked');
                submit.disabled = true;
                submit.setAttribute('title', reason);
                return;
            }
            choice.value = id;
            status.textContent = '고른 정의서 — ' + name;
            status.classList.remove('n-picker-count-blocked');
            submit.disabled = false;
            submit.removeAttribute('title');
        }

        function close() {
            overlay.hidden = true;
            document.removeEventListener('keydown', onKeydown);
        }

        function onKeydown(e) {
            if (e.key === 'Escape') close();
        }

        openers.forEach((opener) => opener.addEventListener('click', async function () {
            // 흐름마다 다른 셋을 갈아끼운다 — 이 세 줄이 모달을 한 벌로 유지하는 값이다
            title.textContent = opener.getAttribute('data-picker-title');
            submit.textContent = opener.getAttribute('data-picker-submit-label');
            form.dataset.action = opener.getAttribute('data-submit-url');

            overlay.hidden = false;
            document.addEventListener('keydown', onKeydown);
            if (window.FormError) window.FormError.clear(form);

            if (!loaded) {
                try {
                    // 목록은 흐름과 무관하게 같으므로 주소는 모달이 갖는다(여는 버튼마다 되풀이하지 않는다)
                    const resp = await fetch(overlay.getAttribute('data-fetch-url'), {
                        headers: {'Accept': 'text/html'}, credentials: 'same-origin'
                    });
                    body.innerHTML = resp.ok
                        ? await resp.text()
                        : '<p class="n-detail-empty">정의서를 불러오지 못했습니다. 다시 시도해주세요.</p>';
                    loaded = resp.ok;
                } catch (e) {
                    body.innerHTML = '<p class="n-detail-empty">서버와 통신할 수 없습니다.</p>';
                }
            }
            clearSelection();
        }));

        body.addEventListener('click', function (e) {
            const item = e.target.closest('.n-miller-item');
            if (item) select(item);
        });
        overlay.addEventListener('click', function (e) {
            if (e.target.closest('[data-picker-close]')) close();
        });
    }

    document.addEventListener('DOMContentLoaded', wire);
})();
