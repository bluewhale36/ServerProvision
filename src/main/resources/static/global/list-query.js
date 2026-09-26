/* S8-1 — 조회 띠(GET 폼) 공통 동작.
   상태(검색 · 필터 · 정렬 · 표시 개수)는 폼 필드가 조립하고 URL 에 실린다. 이 스크립트는 제출 시점만 맡는다.
     ① form[data-list-query] 의 select · radio · checkbox 가 바뀌면 곧 제출한다. 검색 입력은 손대지 않는다
        (네이티브 Enter · 검색 버튼). HTML form= 속성으로 폼 밖에 선 컨트롤(쪽 이동 줄의 표시 개수)도
        target.form 이 그 폼을 가리키므로 같이 잡힌다.
     ② formdata 에서 빈 값과 기본값(data-default · 정렬 항목의 data-default-dir)을 빼 URL 을 깨끗이 둔다.
        스크립트가 없으면 남아 있을 뿐 서버는 같은 기본값으로 받는다.
     ③ [data-chip-clear="이름"] 버튼("전체")은 같은 이름의 체크박스를 전부 해제하고 제출한다.
     ④ 정렬 항목을 바꾸면 방향 셀렉트를 그 항목의 기본 방향(data-default-dir)으로 맞춘 뒤 제출한다 — 항목만
        골라도 "최근 생성순" · "멤버 많은 순" 이 나오게(CP5 D-2). 방향을 따로 바꾸는 것은 그다음 선택이다.
     ⑤ 제출은 스크립트가 URL 을 조립해 이동한다 — 남은 필드가 없을 때 주소 끝에 "?" 가 붙지 않게(CP5 O-5).
     ⑥ 검색 입력칸의 Enter 는 스크립트가 받아 한 번에 제출한다(S8-1 CP7). 한글처럼 조합 중인 글자가 있으면
        브라우저가 첫 Enter 를 "조합 확정" 으로 써 버려 제출이 일어나지 않는다 — 그때는 조합이 끝나는 순간
        (compositionend) 제출한다. 옆의 '검색' 버튼은 지금도 제출하지만, 후속 단계(S8-4)에서 상세 조회 창을 여는
        자리로 바뀔 예정이라 Enter 가 검색의 주 경로가 된다.
   폼 제출은 전부 page 를 버린다 — page 는 폼 필드가 아니다. 필터 · 정렬 · 크기를 바꾸면 첫 쪽으로 돌아가는
   규칙(plan R4)이 여기서 나온다. 쪽 이동만 링크(ListLinks)라 나머지 상태를 그대로 든다. */
(function () {
    'use strict';

    function listFormOf(el) {
        const form = el && el.form;
        return form && form.hasAttribute('data-list-query') ? form : null;
    }

    document.addEventListener('change', function (e) {
        const el = e.target;
        const isSelect = el instanceof HTMLSelectElement;
        const isToggle = el instanceof HTMLInputElement && (el.type === 'radio' || el.type === 'checkbox');
        if (!isSelect && !isToggle) return;
        const form = listFormOf(el);
        if (!form) return;
        if (isSelect && el.name === 'sort') {
            const dir = form.elements.namedItem('dir');
            const def = el.selectedOptions[0] && el.selectedOptions[0].getAttribute('data-default-dir');
            if (dir && def) dir.value = def;
        }
        form.requestSubmit();
    });

    document.addEventListener('submit', function (e) {
        const form = e.target;
        if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-list-query')) return;
        e.preventDefault();
        const qs = new URLSearchParams(new FormData(form)).toString();   // formdata 이벤트가 먼저 정리한다
        const action = form.getAttribute('action') || window.location.pathname;
        window.location.assign(action + (qs ? '?' + qs : ''));
    });

    // ⑥ 검색 입력칸 Enter — 조합 중이면 조합이 끝날 때, 아니면 바로 제출한다.
    let submitAfterComposition = null;
    document.addEventListener('keydown', function (e) {
        const el = e.target;
        if (e.key !== 'Enter' || !(el instanceof HTMLInputElement) || el.type !== 'search') return;
        const form = listFormOf(el);
        if (!form) return;
        e.preventDefault();
        if (e.isComposing || e.keyCode === 229) {
            submitAfterComposition = form;
            return;
        }
        form.requestSubmit();
    });
    document.addEventListener('compositionend', function () {
        if (!submitAfterComposition) return;
        const form = submitAfterComposition;
        submitAfterComposition = null;
        // compositionend 직후에는 입력칸 값이 아직 확정 전일 수 있어 한 틱 뒤에 제출한다.
        setTimeout(function () { form.requestSubmit(); }, 0);
    });

    document.addEventListener('click', function (e) {
        const btn = e.target.closest('[data-chip-clear]');
        if (!btn) return;
        const form = listFormOf(btn);
        if (!form) return;
        const name = btn.getAttribute('data-chip-clear');
        form.querySelectorAll('input[type="checkbox"][name="' + name + '"]').forEach(function (cb) {
            cb.checked = false;
        });
        form.requestSubmit();
    });

    document.addEventListener('formdata', function (e) {
        const form = e.target;
        if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-list-query')) return;
        const fd = e.formData;
        const sortSelect = form.elements.namedItem('sort');
        const dirDefault = sortSelect && sortSelect.selectedOptions && sortSelect.selectedOptions[0]
            ? sortSelect.selectedOptions[0].getAttribute('data-default-dir') : null;

        Array.from(new Set(fd.keys())).forEach(function (key) {
            const all = fd.getAll(key);
            const values = all.filter(function (v) { return typeof v !== 'string' || v.trim() !== ''; });   // 공백만 친 검색어도 빈 값(CP5 O-6)
            if (values.length === 0) { fd.delete(key); return; }
            const control = form.elements.namedItem(key);
            const def = control && typeof control.getAttribute === 'function' ? control.getAttribute('data-default') : null;
            if (values.length === 1 && ((key === 'dir' && dirDefault && values[0] === dirDefault) || (def !== null && values[0] === def))) {
                fd.delete(key);
                return;
            }
            if (values.length !== all.length) {
                fd.delete(key);
                values.forEach(function (v) { fd.append(key, v); });
            }
        });
    });
})();
