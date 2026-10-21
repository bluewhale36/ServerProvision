/* S8-6 — 조회 조건 창의 검색 선택기(보드 · 소속 그룹 · ecount '창고검색' 방식).
   마크업은 fragments/ui/lookup-field. 줄 하나가 [data-lookup-field] 와 그 옆의 검색 팝업([data-lookup-panel], popover)이다.
     ① 돋보기 → 빈 검색으로 팝업을 연다. 줄 입력칸 Enter → 그 글자로 검색하고, 행이 정확히 1 개면 팝업 없이 바로 칩을
        더한다(0 개 · 여러 개면 걸러진 팝업을 연다).
     ② 팝업 안: 검색칸 Enter · Search · '비활성 포함' 토글 → 다시 조회. 이름 클릭 = 하나 더하고 닫음. 체크 + [적용] = 한꺼번에.
        이미 칩인 행은 체크된 채 비활성 · '선택됨'(서버는 선택 상태를 모르므로 여기서 칩과 대조한다).
     ③ 칩 ⊖ = 하나 해제 · 줄 × = 전부 해제.
   칩 추가 · 해제는 조건 창 안의 변경이라 곧 적용되지 않는다 — 조건 창 [검색] 으로 적용하고, 적용 없이 닫으면 list-query.js ⑩ 이
   칩 영역을 서버가 그린 마크업으로 되돌린다. 팝업과 줄 입력칸은 폼 안에 있으므로 Enter 의 암묵 제출을 여기서 막는다. */
(function () {
    'use strict';

    function fieldOf(el) {
        const row = el.closest('[data-filter-row]');
        return row ? row.querySelector('[data-lookup-field]') : null;
    }

    function panelOf(field) {
        return field.closest('[data-filter-row]').querySelector('[data-lookup-panel]');
    }

    function chipValues(field) {
        return Array.from(field.querySelectorAll('[data-lookup-chip]')).map(function (c) { return c.getAttribute('data-value'); });
    }

    function addChip(field, value, label) {
        if (chipValues(field).indexOf(value) >= 0) return;
        const chip = document.createElement('span');
        chip.className = 'n-lookup-chip';
        chip.setAttribute('data-lookup-chip', '');
        chip.setAttribute('data-value', value);
        const text = document.createElement('span');
        text.textContent = label;
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = field.getAttribute('data-lookup-name');
        input.value = value;
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.setAttribute('data-lookup-remove', '');
        remove.setAttribute('aria-label', label + ' ' + (field.getAttribute('data-lookup-remove-label') || ''));
        remove.textContent = '−';
        chip.append(text, input, remove);
        field.querySelector('[data-lookup-chips]').appendChild(chip);
    }

    // 같은 줄의 조회가 겹치면(Enter 연타 · 검색 연타) 마지막 요청의 응답만 쓴다 — 늦게 온 옛 응답이 칩 판정이나
    // 팝업 내용을 덮지 않게(CP5 O-1). 옛 응답의 promise 는 끝나지 않은 채 버려진다.
    const latest = new WeakMap();
    function load(field, q) {
        const ticket = (latest.get(field) || 0) + 1;
        latest.set(field, ticket);
        return fetchRows(field, q).then(function (html) {
            if (latest.get(field) !== ticket) return new Promise(function () {});
            return html;
        });
    }

    function fetchRows(field, q) {
        const panel = panelOf(field);
        const disabled = panel.querySelector('[data-lookup-disabled]');
        const url = new URL(field.getAttribute('data-lookup-url'), window.location.href);
        if (q) url.searchParams.set('q', q);
        if (disabled && disabled.checked) url.searchParams.set('includeDisabled', 'true');
        return fetch(url.pathname + url.search, {headers: {'Accept': 'text/html'}, credentials: 'same-origin'})
            .then(function (res) {
                if (!res.ok) throw new Error(String(res.status));
                return res.text();
            });
    }

    function render(field, html) {
        const panel = panelOf(field);
        const results = panel.querySelector('[data-lookup-results]');
        results.innerHTML = html;
        const chosen = chipValues(field);
        results.querySelectorAll('[data-lookup-row]').forEach(function (row) {
            if (chosen.indexOf(row.getAttribute('data-value')) < 0) return;
            row.classList.add('n-lookup-row-chosen');
            const check = row.querySelector('[data-lookup-check]');
            if (check) { check.checked = true; check.disabled = true; }
            const pick = row.querySelector('[data-lookup-pick]');
            if (pick) {
                pick.disabled = true;
                const mark = document.createElement('span');
                mark.className = 'n-badge n-badge-blue n-lookup-chosen';
                mark.textContent = field.getAttribute('data-lookup-chosen-label') || '';
                pick.after(mark);   // 이미 고른 값 — 줄의 칩에서 푼다(plan D6 · CP5 O-2)
            }
        });
        updateStatus(field);
    }

    function updateStatus(field) {
        const panel = panelOf(field);
        const status = panel.querySelector('[data-lookup-status]');
        const n = panel.querySelectorAll('[data-lookup-check]:checked:not(:disabled)').length;
        if (status) status.textContent = n > 0 ? '체크 ' + n + '개' : '';
    }

    function open(field, q, html) {
        const panel = panelOf(field);
        const search = panel.querySelector('[data-lookup-search]');
        search.value = q || '';
        if (!panel.matches(':popover-open')) panel.showPopover();
        const done = function (h) { render(field, h); search.focus(); };
        if (html !== undefined) done(html);
        else load(field, q).then(done).catch(function () { render(field, ''); });
    }

    function rowsIn(html) {
        const box = document.createElement('div');
        box.innerHTML = html;
        return Array.from(box.querySelectorAll('[data-lookup-row]'));
    }

    // 폼 안의 입력칸이라 Enter 가 조회 목록을 제출하지 않게 막는다(캡처 — list-query.js 의 처리보다 먼저).
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Enter') return;
        const el = e.target;
        if (!(el instanceof HTMLInputElement)) return;
        if (el.hasAttribute('data-lookup-input')) {
            e.preventDefault();
            if (e.isComposing || e.keyCode === 229) return;
            const field = fieldOf(el);
            const q = el.value.trim();
            el.value = q;   // 공백만 친 경우 빈칸으로(CP5 O-5)
            load(field, q).then(function (html) {
                const rows = rowsIn(html).filter(function (r) { return chipValues(field).indexOf(r.getAttribute('data-value')) < 0; });
                if (q && rows.length === 1) {
                    addChip(field, rows[0].getAttribute('data-value'), rows[0].getAttribute('data-label'));
                    el.value = '';
                } else {
                    open(field, q, html);
                }
            }).catch(function () { open(field, q); });
        } else if (el.hasAttribute('data-lookup-search')) {
            e.preventDefault();
            if (e.isComposing || e.keyCode === 229) return;
            const field = fieldOf(el);
            load(field, el.value.trim()).then(function (h) { render(field, h); });
        }
    }, true);

    document.addEventListener('click', function (e) {
        const t = e.target.closest('[data-lookup-open], [data-lookup-search-btn], [data-lookup-pick], [data-lookup-apply], [data-lookup-close], [data-lookup-remove], [data-lookup-clear]');
        if (!t) return;
        const field = fieldOf(t);
        if (!field) return;
        const panel = panelOf(field);
        if (t.hasAttribute('data-lookup-open')) {
            open(field, '');
        } else if (t.hasAttribute('data-lookup-search-btn')) {
            load(field, panel.querySelector('[data-lookup-search]').value.trim()).then(function (h) { render(field, h); });
        } else if (t.hasAttribute('data-lookup-pick')) {
            const row = t.closest('[data-lookup-row]');
            addChip(field, row.getAttribute('data-value'), row.getAttribute('data-label'));
            panel.hidePopover();
            field.querySelector('[data-lookup-input]').value = '';
        } else if (t.hasAttribute('data-lookup-apply')) {
            panel.querySelectorAll('[data-lookup-row]').forEach(function (row) {
                const check = row.querySelector('[data-lookup-check]');
                if (check && check.checked && !check.disabled) addChip(field, row.getAttribute('data-value'), row.getAttribute('data-label'));
            });
            panel.hidePopover();
            field.querySelector('[data-lookup-input]').value = '';
        } else if (t.hasAttribute('data-lookup-close')) {
            panel.hidePopover();
        } else if (t.hasAttribute('data-lookup-remove')) {
            t.closest('[data-lookup-chip]').remove();
        } else if (t.hasAttribute('data-lookup-clear')) {
            field.querySelectorAll('[data-lookup-chip]').forEach(function (c) { c.remove(); });
        }
    });

    document.addEventListener('change', function (e) {
        const el = e.target;
        if (!(el instanceof HTMLInputElement)) return;
        const field = fieldOf(el);
        if (!field) return;
        if (el.hasAttribute('data-lookup-check')) updateStatus(field);
        if (el.hasAttribute('data-lookup-disabled')) {
            load(field, panelOf(field).querySelector('[data-lookup-search]').value.trim()).then(function (h) { render(field, h); });
        }
    });

    // 팝업이 닫히면 포커스를 줄 입력칸으로 돌린다(조건 창은 남아 있다 · ecount ⑩).
    document.querySelectorAll('[data-lookup-panel]').forEach(function (panel) {
        panel.addEventListener('toggle', function (e) {
            if (e.newState !== 'closed') return;
            const input = panel.closest('[data-filter-row]').querySelector('[data-lookup-input]');
            if (input && panel.closest('[data-filter-panel]:popover-open')) input.focus({preventScroll: true});
        });
    });
})();
