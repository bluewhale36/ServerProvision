/* ============================================================
   서버 상세 — 정의서 선택 모달의 열기 · 고른 뒤 처리 (U3-5-b → R20).
   ─────────────────────────────────────────────────────────
   받기 · 검색 · 행 고르기 · 내용 펼치기는 공용 definition-search-picker.js 가 하고, 이 파일은 이 모달만의 몫을 한다.
   제출은 server-assignment.js 가 맡는다 — 모달 폼도 data-action 을 가진 form 이라 그 파일의 단일 핸들러가 그대로 잡는다.

   할당과 재할당이 모달 하나를 함께 쓴다(DEC-E). 다른 것은 셋뿐이라 여는 버튼이 data-* 로 넘긴다 :
     · data-picker-title        — 모달 제목
     · data-picker-submit-label — 제출 버튼 문구
     · data-submit-url          — form 의 data-action 에 실릴 대상

   잠긴 정의서도 고를 수 있다 — 고르면 사유와 내용이 함께 펼쳐지고, [할당] 만 열리지 않는다.
   ============================================================ */
(function () {
    'use strict';

    const PROMPT = '표에서 정의서를 고르세요';

    function wire() {
        const overlay = document.getElementById('definitionPicker');
        if (!overlay) return;   // 회수된 서버 — 모달 자체가 렌더되지 않는다(DEC-D)

        const form = document.getElementById('definition-picker-form');
        const body = document.getElementById('definitionPickerBody');
        const title = document.getElementById('definitionPickerTitle');
        const status = document.getElementById('definitionPickerStatus');
        const submit = document.getElementById('definitionPickerSubmit');
        const choice = document.getElementById('definitionPickerChoice');
        if (!form || !body || !window.DefinitionSearchPicker) return;

        /* 고른 행이 바뀔 때마다 — 잠긴 것을 골랐을 때도 선택 표시는 남기되 값은 싣지 않아 제출할 수 없게 한다.
           문구는 서버가 만든 사유 그대로다(화면과 서버 가드가 같은 문자열). */
        const picker = window.DefinitionSearchPicker.attach(body, function (row) {
            const reason = row ? row.getAttribute('data-block-reason') : null;
            if (!row || reason) {
                choice.value = '';
                status.textContent = row ? reason : PROMPT;
                status.classList.toggle('n-picker-count-blocked', !!row);
                submit.disabled = true;
                if (row) submit.setAttribute('title', reason); else submit.removeAttribute('title');
                return;
            }
            choice.value = row.getAttribute('data-definition-id');
            status.textContent = '';   // 고른 정의서는 펼침 머리가 이미 적는다 — 하단에서 되풀이하지 않는다
            status.classList.remove('n-picker-count-blocked');
            submit.disabled = false;
            submit.removeAttribute('title');
        });

        // 제출이 409 로 거절되면(모달을 연 뒤 하드웨어가 바뀌는 등) 판정을 새로 받는다 — 고른 행이 잠겼으면
        // onSelect 가 사유를 보이고 [할당] 을 닫는다. 맨 위 배너(서버 사유)는 그대로 남는다(R20 CP5 O1).
        form.addEventListener('assignment-rejected', function (e) {
            e.preventDefault();   // 제출 버튼은 재조회 뒤 onSelect 가 판정대로 연다 · 닫는다
            picker.refresh();
        });

        function close() {
            overlay.hidden = true;
            document.removeEventListener('keydown', onKeydown);
        }

        function onKeydown(e) {
            // 조회 조건 창이 열려 있으면 Esc 는 그 창만 닫는다(브라우저 popover 기본 동작) — 모달은 남는다
            if (e.key === 'Escape' && !overlay.querySelector(':popover-open')) close();
        }

        /* 여는 버튼은 문서 위임으로 듣는다(S21-1). 개요의 '다음 행동' 은 실시간 교체 영역이라 직접 바인딩하면
           신호 한 번에 버튼이 벙어리가 된다. 계획 판의 #openAssignPicker · #openReassignPicker 와 개요의
           [data-picker-opener] 가 같은 모달을 연다. */
        document.addEventListener('click', function (e) {
            const opener = e.target.closest('#openAssignPicker, #openReassignPicker, [data-picker-opener]');
            if (!opener) return;
            // 흐름마다 다른 셋을 갈아끼운다 — 이 세 줄이 모달을 한 벌로 유지하는 값이다
            title.textContent = opener.getAttribute('data-picker-title');
            submit.textContent = opener.getAttribute('data-picker-submit-label');
            form.dataset.action = opener.getAttribute('data-submit-url');

            overlay.hidden = false;
            document.addEventListener('keydown', onKeydown);
            if (window.FormError) window.FormError.clear(form);
            // 목록은 흐름과 무관하게 같으므로 주소는 모달이 갖는다. 열 때마다 처음 상태(검색 없음 · 기본 기간)로 받는다.
            picker.open(overlay.getAttribute('data-fetch-url'));
        });

        overlay.addEventListener('click', function (e) {
            if (e.target.closest('[data-picker-close]')) close();
        });
    }

    document.addEventListener('DOMContentLoaded', wire);
})();
