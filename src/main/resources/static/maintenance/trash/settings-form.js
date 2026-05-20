/* ============================================================
   S5-2-4 — 휴지통 운영 설정 폼 사용성 helper.
   ─────────────────────────────────────────────────────────────
   책임 :
     · cron preset select 와 advanced input 동기화
     · 현재 form value 가 preset 중 어디에 매칭되는지 페이지 load 시 자동 선택
     · "⚙ 직접 입력" 선택 시 advanced input 활성화
   ============================================================ */
(function () {
    'use strict';

    document.querySelectorAll('select[data-cron-target]').forEach(select => {
        const inputSelector = select.getAttribute('data-cron-target');
        const input = document.querySelector(inputSelector);
        const advancedWrap = select.parentElement.querySelector('[data-cron-advanced]');
        if (!input || !advancedWrap) return;

        // 현재 input value 가 preset 중 하나에 매칭되는지 → 그 옵션 선택, 아니면 "__custom__"
        const currentValue = input.value || input.defaultValue || '';
        const matchedOption = Array.from(select.options)
            .find(opt => opt.value === currentValue && opt.value !== '__custom__');
        if (matchedOption) {
            select.value = matchedOption.value;
            advancedWrap.hidden = true;
        } else if (currentValue) {
            // preset 외 값 → 직접 입력 모드
            select.value = '__custom__';
            advancedWrap.hidden = false;
        }

        // preset 변경 핸들러
        select.addEventListener('change', () => {
            if (select.value === '__custom__') {
                advancedWrap.hidden = false;
                input.focus();
                input.select();
            } else {
                advancedWrap.hidden = true;
                input.value = select.value;
            }
        });

        // 사용자가 advanced input 을 손대면 select 도 "__custom__" 으로 따라가게 + SSR stale 에러 정리
        input.addEventListener('input', () => {
            const matched = Array.from(select.options)
                .find(opt => opt.value === input.value && opt.value !== '__custom__');
            select.value = matched ? matched.value : '__custom__';
            // 입력이 바뀌면 SSR 에서 렌더된 invalid 상태/에러 메시지를 일단 제거 (submit 시 재검증).
            input.classList.remove('is-invalid');
            const errEl = advancedWrap.querySelector('.n-error');
            if (errEl) errEl.remove();
        });
    });
})();
