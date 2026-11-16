/* ============================================================
   정의서 고르기 모달 공용 동작 (R20) — 서버 단건(definition-picker.js) · 그룹 일괄(group-definition-picker.js).
   ─────────────────────────────────────────────────────────
   두 모달이 같은 것을 여기서 한다 : 내용 조각 받기 · 검색(이름 · 등록 시각 기간) · 행 고르기 · 고른 행의 내용 펼치기.
   고른 뒤에 무엇이 열리고 닫히는지(잠김 · 적용 대상 0)는 모달마다 달라 onSelect 콜백이 맡는다.

   · 검색은 같은 조각 엔드포인트를 q · from · to 로 다시 받아 결과 영역([data-picker-results])만 갈아끼운다. 검색 줄은
     그대로라 입력이 유지된다. 겹친 요청은 마지막 응답만 쓴다(S8-6 CP5 O-1 과 같은 규칙).
   · 새 결과에 고른 정의서가 있으면 선택을 유지하고, 없으면 푼다.
   · 검색칸은 이름만 · Enter 로 검색한다. 옆 [Search] 는 조회 조건 창(popover)을 열고, 창 하단 [검색] 이 이름 · 대상 보드 ·
     단계 구성 · 할당 가능 · 등록 시각을 한 번에 적용한다(목록 화면의 조회 띠와 같은 쓰임). 창은 갈아끼우지 않아 고른 조건이 남는다.
   · 이름 입력 · 날짜 칸의 Enter 는 검색이다 — 모달 폼 안이라 막지 않으면 제출로 번진다.
   · 기간의 빠른 칩(오늘 · 최근 7일 …)은 list-query.js ⑬ 이 두 날짜를 채운다. 내용을 받은 뒤 눌린 모양을 맞추고(기본 = 최근 1년),
     날짜 두 칸의 min/max 는 여기서 맞춘다.
   ============================================================ */
window.DefinitionSearchPicker = (function () {
    'use strict';

    const LOAD_FAILED = '<p class="n-detail-empty">정의서를 불러오지 못했습니다. 다시 시도해주세요.</p>';
    const SEARCH_FAILED = '<p class="n-detail-empty">검색하지 못했습니다. 다시 시도해주세요.</p>';

    async function fetchHtml(url) {
        const resp = await fetch(url, {headers: {'Accept': 'text/html'}, credentials: 'same-origin'});
        if (!resp.ok) throw new Error(String(resp.status));
        return resp.text();
    }

    /**
     * body : 모달의 내용 컨테이너. onSelect(row | null) : 고른 행이 바뀔 때마다(풀릴 때 null).
     * 반환 { open(url), refresh() } — open 은 모달을 열 때마다 부른다(매번 처음 상태로). refresh 는 지금 조건 그대로
     * 결과만 다시 받는다 — 서버가 제출을 거절해 화면 판정이 낡았을 때 쓴다.
     */
    function attach(body, onSelect) {
        let baseUrl = null;
        let ticket = 0;
        let selectedId = null;

        function select(row) {
            selectedId = row ? row.getAttribute('data-definition-id') : null;
            body.querySelectorAll('[data-picker-row]').forEach(function (r) {
                const on = r === row;
                r.classList.toggle('n-lookup-row-selected', on);
                r.setAttribute('aria-selected', String(on));
            });
            body.querySelectorAll('[data-picker-panel]').forEach(function (panel) {
                panel.hidden = panel.getAttribute('data-picker-panel') !== selectedId;
            });
            const empty = body.querySelector('[data-picker-detail-empty]');
            if (empty) empty.hidden = !!row;
            onSelect(row);
        }

        function params() {
            const sp = new URLSearchParams();
            const q = body.querySelector('[data-picker-q]');
            if (q && q.value.trim()) sp.set('q', q.value.trim());
            const panel = body.querySelector('[data-picker-filter]');
            if (panel) {
                panel.querySelectorAll('input[type="checkbox"]:checked').forEach(function (box) {
                    sp.append(box.name, box.value);
                });
                const from = panel.querySelector('input[data-range-start]');
                const to = panel.querySelector('input[data-range-end]');
                if (from && from.value) sp.set('from', from.value);
                if (to && to.value) sp.set('to', to.value);
            }
            return sp.toString();
        }

        function markPresets() {
            if (!window.ListRangePresets) return;
            body.querySelectorAll('[data-range-today]').forEach(function (holder) {
                window.ListRangePresets.mark(holder.parentElement);
            });
        }

        function hidePanel() {
            const panel = body.querySelector('[data-picker-filter]');
            if (panel && panel.matches(':popover-open')) panel.hidePopover();
        }

        async function open(url) {
            baseUrl = url;
            ticket++;
            try {
                body.innerHTML = await fetchHtml(url);
            } catch (e) {
                body.innerHTML = LOAD_FAILED;
            }
            markPresets();
            select(null);
        }

        async function search() {
            if (!baseUrl) return;
            const mine = ++ticket;
            const query = params();
            hidePanel();
            const results = body.querySelector('[data-picker-results]');
            let html;
            try {
                html = await fetchHtml(baseUrl + (query ? (baseUrl.indexOf('?') >= 0 ? '&' : '?') + query : ''));
            } catch (e) {
                if (mine === ticket && results) results.innerHTML = SEARCH_FAILED;
                return;
            }
            if (mine !== ticket) return;   // 늦게 온 옛 응답
            const fresh = new DOMParser().parseFromString(html, 'text/html').querySelector('[data-picker-results]');
            if (fresh && results) results.innerHTML = fresh.innerHTML;
            const kept = selectedId
                ? body.querySelector('[data-picker-row][data-definition-id="' + CSS.escape(selectedId) + '"]')
                : null;
            select(kept);
        }

        function syncRange() {
            const from = body.querySelector('input[data-range-start]');
            const to = body.querySelector('input[data-range-end]');
            if (!from || !to) return;
            if (to.value) from.max = to.value; else from.removeAttribute('max');
            if (from.value) to.min = from.value; else to.removeAttribute('min');
        }

        body.addEventListener('click', function (e) {
            if (e.target.closest('[data-picker-search]')) { search(); return; }
            if (e.target.closest('[data-range-preset]')) { setTimeout(function () { syncRange(); markPresets(); }, 0); return; }
            const row = e.target.closest('[data-picker-row]');
            if (row) select(row);
        });
        body.addEventListener('keydown', function (e) {
            if (e.key !== 'Enter' && e.key !== ' ') return;
            if (e.target.matches('[data-picker-q], input[data-range-start], input[data-range-end]')) {
                if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); search(); }
                return;
            }
            const row = e.target.closest('[data-picker-row]');
            if (row) { e.preventDefault(); select(row); }
        });
        body.addEventListener('change', function (e) {
            if (e.target.matches('input[data-range-start], input[data-range-end]')) { syncRange(); markPresets(); }
        });

        return {open: open, refresh: search};
    }

    return {attach: attach};
})();
