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
     ⑥ 검색 입력칸(input[data-list-search])의 Enter 는 스크립트가 받아 한 번에 제출한다(S8-1 CP7). 한글처럼 조합 중인 글자가 있으면
        브라우저가 첫 Enter 를 "조합 확정" 으로 써 버려 제출이 일어나지 않는다 — 그때는 조합이 끝나는 순간
        (compositionend) 제출한다. 옆의 Search 버튼은 제출하지 않고 조회 조건 창을 연다(S8-4) — 띠에서 검색어를
        제출하는 길은 Enter 하나다.
     ⑧ 자원 관리 목록(S8-3 · Miller) — 선택은 클릭 때 history.replaceState 로 주소만 바뀐다. 그래서
        (a) data-list-keep 에 적힌 선택 파라미터는 제출 순간의 주소에서 읽어 싣는다(서버가 그린 값은 낡는다).
        (b) [data-list-reset] 초기화는 누른 순간의 주소에서 적힌 조건만 빼고 이동한다(선택 · 삭제 포함은 남긴다).
        (c) data-list-return 이 있는 화면에서 Miller 안의 상태 변경 폼(POST)이 제출되면 지금 주소를
            returnQuery 로 싣는다 — 서버(ReturnQueryInterceptor)가 리다이렉트에 조건 · 묶음 선택을 되붙인다.
            확인 모달을 거치는 폼은 대상에서 전파를 끊으므로 캡처 단계에서 싣는다.
     ⑩ 조회 조건 창(S8-4 · [data-filter-panel] popover) — 창 안 컨트롤은 ① · ③ · ⑦ 의 즉시 제출에서 빠진다. 값만 바뀌고
        하단 [검색](폼의 첫 submit 버튼 · 창 안 Enter 의 암묵 제출도 여기로)이 한 번에 적용한다. 적용 없이 닫히면
        (닫기 · Esc · 바깥 클릭) 창 안 컨트롤을 서버가 그린 값(defaultChecked · defaultValue · defaultSelected = 지금 주소의
        조건)으로 되돌린다 — 다시 열었을 때 창과 목록이 어긋나지 않게. 열리면 첫 컨트롤로 포커스를 옮긴다.
     ⑪ 걸린 조건 토큰 — 창의 줄([data-filter-row])마다 기본값과 다른 컨트롤이 있으면 "줄 라벨: 선택값" 토큰을 띠에
        그린다. × 는 그 줄만 기본값으로 되돌리고 제출한다. 문구는 줄 마크업에서 읽으므로 서버가 따로 그리지 않는다.
     ⑫ 쪽 번호 입력(쪽 이동 줄 · S8-4) — Enter 로 그 쪽으로 간다.
     ⑬ 빠른 기간 칩(S8-5 · 기간 줄) — 두 날짜를 채우기만 한다(창 안이라 [검색] 으로 적용). 지금 두 날짜와 같은 칩은 눌린 모양.
   폼 제출은 전부 page 를 버린다 — page 는 폼 필드가 아니다. 필터 · 정렬 · 크기를 바꾸면 첫 쪽으로 돌아가는
   규칙(plan R4)이 여기서 나온다. 쪽 이동만 링크(ListLinks)라 나머지 상태를 그대로 든다. */
(function () {
    'use strict';

    // 필드의 기본값 — 셀렉트는 자기 data-default, 라디오 묶음(RadioNodeList)은 data-default 를 가진 항목의 값(S8-2 보기 전환).
    function defaultOf(control) {
        if (!control) return null;
        if (typeof control.getAttribute === 'function') return control.getAttribute('data-default');
        const marked = Array.from(control).find(function (el) { return el.hasAttribute && el.hasAttribute('data-default'); });
        return marked ? marked.getAttribute('data-default') : null;
    }

    function listFormOf(el) {
        const form = el && el.form;
        return form && form.hasAttribute('data-list-query') ? form : null;
    }

    // ⑩ 창 안의 컨트롤인가 — 그렇다면 값만 바꾸고 제출하지 않는다.
    function inPanel(el) {
        return !!(el && el.closest && el.closest('[data-filter-panel]'));
    }

    document.addEventListener('change', function (e) {
        const el = e.target;
        const isSelect = el instanceof HTMLSelectElement;
        const isToggle = el instanceof HTMLInputElement && (el.type === 'radio' || el.type === 'checkbox');
        if (!isSelect && !isToggle) return;
        const form = listFormOf(el);
        if (!form || inPanel(el)) return;
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
        // ⑩ 창 밖에서 일어난 제출(띠의 칩 · 정렬 · 검색칸 Enter · 토큰 ×)이면 열린 창을 먼저 닫아 미적용 값을 버린다(CP5 D-1).
        //    닫힘 처리(beforetoggle)는 동기라 FormData 를 만들기 전에 값이 되돌아가 있다. 창의 [검색] · 창 안 Enter 는
        //    submitter 가 창 안 버튼이므로 그대로 싣는다.
        const openPanel = form.querySelector('[data-filter-panel]:popover-open');
        if (openPanel && !(e.submitter && inPanel(e.submitter))) openPanel.hidePopover();
        form.dataset.submitting = '1';
        const qs = new URLSearchParams(new FormData(form)).toString();   // formdata 이벤트가 먼저 정리한다
        const action = form.getAttribute('action') || window.location.pathname;
        window.location.assign(action + (qs ? '?' + qs : ''));
    });

    // ⑦ 기간 입력(S8-2 · CP5 D-1 · D-2) — 달력에서 고르면 곧 제출하고, 키보드로 칸을 고치는 중이면 Enter 나 칸을
    //    벗어날 때 한 번 제출한다. Chrome 은 연 · 월 · 일 칸 하나가 바뀔 때마다 change 를 쏘므로, 그대로 제출하면
    //    연도 첫 글자에서 0002 년으로 조회된다. 값이 바뀌면 상대 칸의 min/max 를 다시 맞춰 거꾸로 고를 수 없게 한다
    //    (서버는 거꾸로 상태에서 min/max 를 그리지 않는다 — 그리면 두 칸이 서로를 가둬 어떤 제출도 못 한다).
    // 상한이 없으면 Chrome 의 연도 칸이 6 자리까지 받아 "202608-02-05" 같은 값이 된다(CP5 R-1) — 기본 상한을 둔다.
    const RANGE_MAX = '9999-12-31';
    // 속성은 값이 달라질 때만, 편집 중인 칸은 건너뛰고 대입한다(CP5 D-3). Chrome 은 min/max 가 대입되는 순간
    // 그 칸의 진행 중 편집을 초기화해, 치던 연도가 빈 값으로 사라진다(같은 값을 다시 넣어도).
    function setAttr(el, name, value, editing) {
        if (el === editing) return;
        if (value == null) { if (el.hasAttribute(name)) el.removeAttribute(name); return; }
        if (el.getAttribute(name) !== value) el.setAttribute(name, value);
    }
    function syncRange(form, editing) {
        const start = form.querySelector('input[type="date"][data-range-start]');
        const end = form.querySelector('input[type="date"][data-range-end]');
        if (!start || !end) return;
        // 비어 있는 끝은 서버가 기본값(data-default — 시작 1 년 전 · 끝 오늘)으로 채우므로 그 값으로 막는다(S8-5 CP5 D-2).
        // 기본값이 없는 칸(옛 화면)은 종전대로 막지 않는다.
        const startValue = start.value || start.getAttribute('data-default') || '';
        const endValue = end.value || end.getAttribute('data-default') || '';
        const reversed = startValue && endValue && startValue > endValue;
        setAttr(start, 'max', reversed ? RANGE_MAX : (endValue || RANGE_MAX), editing);
        setAttr(end, 'min', reversed ? null : (startValue || null), editing);
        setAttr(end, 'max', RANGE_MAX, editing);
    }
    document.addEventListener('keydown', function (e) {
        const el = e.target;
        if (el instanceof HTMLInputElement && el.type === 'date' && e.key !== 'Enter' && e.key !== 'Tab') {
            el.dataset.typing = '1';
        }
    }, true);
    document.addEventListener('change', function (e) {
        const el = e.target;
        if (!(el instanceof HTMLInputElement) || el.type !== 'date') return;
        const form = listFormOf(el);
        if (!form) return;
        if (el.dataset.typing === '1') {
            el.dataset.dirty = '1';   // 치는 중 — min/max 도 건드리지 않고, Enter 또는 칸을 벗어날 때 제출
            return;
        }
        // 검증이 먼저다(CP5 D-4) — 맞추기(syncRange)가 거꾸로를 보고 min 을 풀면 이른 날짜가 막히지 않는다.
        if (!el.checkValidity()) { el.reportValidity(); return; }
        syncRange(form, null);
        if (!inPanel(el)) form.requestSubmit();
    });
    document.addEventListener('focusout', function (e) {
        const el = e.target;
        if (!(el instanceof HTMLInputElement) || el.type !== 'date') return;
        const dirty = el.dataset.dirty === '1';
        delete el.dataset.typing;
        delete el.dataset.dirty;
        const form = listFormOf(el);
        if (!form || !dirty) return;
        // 검증이 먼저다(CP5 D-4). 막히면 브라우저 안내를 띄우고 제출하지 않는다 — 통과했을 때만 두 칸을 맞추고 제출.
        if (!el.checkValidity()) { el.reportValidity(); return; }
        syncRange(form, null);
        if (!inPanel(el)) form.requestSubmit();
    });

    // ⑥ 검색 입력칸 Enter — 조합 중이면 조합이 끝날 때, 아니면 바로 제출한다.
    let submitAfterComposition = null;
    document.addEventListener('keydown', function (e) {
        const el = e.target;
        if (e.key !== 'Enter' || !(el instanceof HTMLInputElement) || !el.hasAttribute('data-list-search')) return;
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
        if (!inPanel(btn)) form.requestSubmit();
    });

    // ⑨ 진입하면 검색 입력칸에 커서를 둔다(S8-3 CP6) — 값이 있으면 끝으로. 입력칸은 type="text"(data-list-search)다:
    //    type="search" 는 브라우저가 지우기(x) 버튼과 Esc 지우기를 붙이는데, 이 목록들에서는 쓰지 않는다.
    const searchInput = document.querySelector('form[data-list-query] input[data-list-search]');
    if (searchInput) {
        searchInput.focus({preventScroll: true});
        const end = searchInput.value.length;
        searchInput.setSelectionRange(end, end);
    }

    // ⑧-b 초기화 — 누른 순간의 주소에서 조건만 뺀다.
    document.addEventListener('click', function (e) {
        const link = e.target.closest('a[data-list-reset]');
        if (!link) return;
        e.preventDefault();
        const params = new URLSearchParams(window.location.search);
        link.getAttribute('data-list-reset').split(/\s+/).forEach(function (k) { if (k) params.delete(k); });
        const qs = params.toString();
        window.location.assign(window.location.pathname + (qs ? '?' + qs : ''));
    });

    // ⑧-c 상태 변경 폼에 지금 주소를 싣는다(캡처 단계 — 확인 모달이 전파를 끊기 전).
    const RETURN_PARAM = 'returnQuery';
    document.addEventListener('submit', function (e) {
        const form = e.target;
        if (!(form instanceof HTMLFormElement) || form.hasAttribute('data-list-query')) return;
        if ((form.getAttribute('method') || 'GET').toUpperCase() !== 'POST') return;
        if (!form.closest('.n-miller') || !document.querySelector('form[data-list-return]')) return;
        let input = form.querySelector('input[name="' + RETURN_PARAM + '"]');
        if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.name = RETURN_PARAM;
            form.appendChild(input);
        }
        input.value = window.location.pathname + window.location.search;
    }, true);

    // ⑩ 조회 조건 창 — 열리면 첫 컨트롤로, 적용 없이 닫히면 서버가 그린 값으로 되돌린다.
    function controlsIn(root) {
        return Array.from(root.querySelectorAll('input, select'));
    }
    function restore(control) {
        if (control instanceof HTMLSelectElement) {
            Array.from(control.options).forEach(function (o) { o.selected = o.defaultSelected; });
        } else if (control.type === 'radio' || control.type === 'checkbox') {
            control.checked = control.defaultChecked;
        } else {
            control.value = control.defaultValue;
        }
    }
    const panel = document.querySelector('[data-filter-panel]');
    if (panel) {
        panel.addEventListener('toggle', function (e) {
            if (e.newState !== 'open') return;
            const first = panel.querySelector('input:not([type="hidden"]), select');
            if (first) first.focus({preventScroll: true});
        });
        // 되돌리기는 beforetoggle(동기)에서 한다 — toggle 은 비동기라, 같은 클릭 · 키 안에서 창 밖 제출이 먼저 일어나면
        // 미적용 값이 FormData 에 실린다(CP5 D-1).
        panel.addEventListener('beforetoggle', function (e) {
            const form = panel.closest('form');
            if (e.newState !== 'closed' || (form && form.dataset.submitting === '1')) return;
            controlsIn(panel).forEach(restore);
            if (form) syncRange(form, null);
            panel.querySelectorAll('.n-filter-range').forEach(markPresets);
        });
    }

    // ⑪ 걸린 조건 토큰 — 줄마다 기본값과 다른 컨트롤의 글자를 모은다(서버가 그린 값 = 지금 주소의 조건).
    function labelText(control) {
        const label = control.closest('label');
        return label ? label.textContent.replace(/\s+/g, ' ').trim() : '';
    }
    function rowValues(row) {
        const values = [];
        const dates = [];
        controlsIn(row).forEach(function (c) {
            if (c instanceof HTMLSelectElement) {
                const o = Array.from(c.options).find(function (opt) { return opt.defaultSelected; });
                if (o && o.value !== '' && o.value !== c.getAttribute('data-default')) values.push(o.textContent.trim());
            } else if (c.type === 'radio') {
                if (c.defaultChecked && c.value !== '' && !c.hasAttribute('data-default')) values.push(labelText(c));
            } else if (c.type === 'checkbox') {
                if (c.defaultChecked) values.push(labelText(c));
            } else if (c.type === 'date') {
                dates.push({value: c.defaultValue, base: c.getAttribute('data-default') || ''});
            }
        });
        // 기간은 기본 범위(data-default)와 하나라도 다를 때만 토큰이 된다 — 양 끝을 함께 보여 범위가 읽히게(S8-4).
        if (dates.some(function (d) { return d.value !== d.base; })) {
            values.push(dates.map(function (d) { return d.value; }).join(' ~ ').trim());
        }
        return values;
    }
    function clearRow(row) {
        controlsIn(row).forEach(function (c) {
            if (c instanceof HTMLSelectElement) {
                c.value = c.getAttribute('data-default') || '';
            } else if (c.type === 'radio') {
                c.checked = c.value === '' || c.hasAttribute('data-default');
            } else if (c.type === 'checkbox') {
                c.checked = false;
            } else {
                c.value = c.getAttribute('data-default') || '';
            }
        });
    }
    const tokens = document.querySelector('[data-filter-tokens]');
    if (panel && tokens) {
        const clearLabel = tokens.getAttribute('data-token-clear-label') || '';
        panel.querySelectorAll('[data-filter-row]').forEach(function (row) {
            const values = rowValues(row);
            if (values.length === 0) return;
            const name = row.querySelector('.n-filter-row-label');
            const title = name ? name.textContent.trim() : '';
            const token = document.createElement('span');
            token.className = 'n-filter-token';
            token.textContent = title + ': ' + values.join(', ');
            const x = document.createElement('button');
            x.type = 'button';
            x.textContent = '×';
            x.setAttribute('aria-label', title + ' ' + clearLabel);
            x.addEventListener('click', function () {
                const form = panel.closest('form');
                // 창이 열려 있으면 먼저 닫아 미적용 값을 버린 뒤(beforetoggle) 이 줄만 푼다 — 순서가 바뀌면 되돌리기가 푼 값을 덮는다.
                if (panel.matches(':popover-open')) panel.hidePopover();
                clearRow(row);
                if (form) form.requestSubmit();
            });
            token.appendChild(x);
            tokens.appendChild(token);
        });
        tokens.hidden = tokens.childElementCount === 0;
    }

    // ⑫ 쪽 번호 입력(S8-4 · 쪽 이동 줄) — Enter 로 그 쪽으로 간다. 폼 필드가 아니므로 첫 쪽 주소(data-page-base)에
    //    page 만 바꿔 이동한다. 쪽 번호는 1 부터, page 파라미터는 0 부터다. 범위 밖 값은 1 · 전체 쪽 수로 맞춘다.
    document.addEventListener('keydown', function (e) {
        const el = e.target;
        if (e.key !== 'Enter' || !(el instanceof HTMLInputElement) || !el.hasAttribute('data-page-jump')) return;
        e.preventDefault();
        const typed = parseInt(el.value, 10);
        if (isNaN(typed)) return;
        const total = parseInt(el.getAttribute('data-page-total'), 10) || 1;
        const number = Math.min(Math.max(typed, 1), total);
        const url = new URL(el.getAttribute('data-page-base'), window.location.href);
        if (number > 1) url.searchParams.set('page', String(number - 1)); else url.searchParams.delete('page');
        window.location.assign(url.pathname + url.search);
    });

    // ⑬ 빠른 기간 칩 — 기준일은 서버의 오늘(data-range-today)이다. 1 년 전은 Java Period.ofYears(1) 와 같게
    //    2 월 29 일이면 2 월 28 일로 맞춘다(JS Date 는 3 월 1 일로 넘긴다).
    function shiftDays(iso, days) {
        const d = new Date(iso + 'T00:00:00Z');
        d.setUTCDate(d.getUTCDate() + days);
        return d.toISOString().slice(0, 10);
    }
    function minusOneYear(iso) {
        const d = new Date(iso + 'T00:00:00Z');
        const month = d.getUTCMonth();
        d.setUTCFullYear(d.getUTCFullYear() - 1);
        if (d.getUTCMonth() !== month) d.setUTCDate(0);
        return d.toISOString().slice(0, 10);
    }
    function presetRange(code, today) {
        switch (code) {
            case '0d': return [today, today];
            case '7d': return [shiftDays(today, -7), today];
            case '30d': return [shiftDays(today, -30), today];
            case '1y': return [minusOneYear(today), today];
            case 'ytd': return [today.slice(0, 4) + '-01-01', today];
            default: return null;
        }
    }
    function markPresets(group) {
        const holder = group.querySelector('[data-range-today]');
        if (!holder) return;
        const start = group.querySelector('input[data-range-start]');
        const end = group.querySelector('input[data-range-end]');
        const today = holder.getAttribute('data-range-today');
        holder.querySelectorAll('[data-range-preset]').forEach(function (chip) {
            const r = presetRange(chip.getAttribute('data-range-preset'), today);
            chip.setAttribute('aria-pressed', String(!!r && start.value === r[0] && end.value === r[1]));
        });
    }
    document.querySelectorAll('[data-range-today]').forEach(function (holder) { markPresets(holder.parentElement); });
    document.addEventListener('click', function (e) {
        const chip = e.target.closest('[data-range-preset]');
        if (!chip) return;
        const group = chip.closest('.n-filter-range');
        const holder = chip.closest('[data-range-today]');
        const r = presetRange(chip.getAttribute('data-range-preset'), holder.getAttribute('data-range-today'));
        if (!group || !r) return;
        group.querySelector('input[data-range-start]').value = r[0];
        group.querySelector('input[data-range-end]').value = r[1];
        const form = listFormOf(group.querySelector('input[data-range-start]'));
        if (form) syncRange(form, null);
        markPresets(group);
    });
    document.addEventListener('input', function (e) {
        const group = e.target instanceof HTMLInputElement && e.target.type === 'date' ? e.target.closest('.n-filter-range') : null;
        if (group) markPresets(group);
    });

    document.addEventListener('formdata', function (e) {
        const form = e.target;
        if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-list-query')) return;
        const fd = e.formData;
        // ⑧-a 선택 파라미터 — 제출 순간의 주소에서 옮겨 싣는다.
        const keep = form.getAttribute('data-list-keep');
        if (keep) {
            const current = new URLSearchParams(window.location.search);
            keep.split(/\s+/).forEach(function (k) {
                if (!k || fd.has(k)) return;
                current.getAll(k).forEach(function (v) { fd.append(k, v); });
            });
        }
        const sortSelect = form.elements.namedItem('sort');
        const dirDefault = sortSelect && sortSelect.selectedOptions && sortSelect.selectedOptions[0]
            ? sortSelect.selectedOptions[0].getAttribute('data-default-dir') : null;

        Array.from(new Set(fd.keys())).forEach(function (key) {
            const all = fd.getAll(key);
            const values = all.filter(function (v) { return typeof v !== 'string' || v.trim() !== ''; });   // 공백만 친 검색어도 빈 값(CP5 O-6)
            if (values.length === 0) { fd.delete(key); return; }
            const def = defaultOf(form.elements.namedItem(key));
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
