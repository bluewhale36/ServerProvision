/*
 * E2-1-a — 펌웨어 버전 순위 드래그 (BIOS · BMC 목록 화면 공용).
 * 목록 맨 위 = 최신(순위 1위). 드래그 자체의 조작감은 공용 모듈(global/row-drag.js)이 갖고 —
 * 세팅 정의서의 표 드래그와 같은 구현이다 — 이 파일은 그 위에 "놓으면 저장" 만 얹는다.
 * 저장 성공은 204 로 조용히 끝난다(바뀐 순서가 곧 화면). 실패는 원래 순서로 되돌리고 힌트 자리에
 * 문구를 잠시 띄운다(문구는 MessageSource 소유, data-rank-error 로 전달 — new-user-copy 규약).
 * 휴지통 겸용 보기(includeDeleted)에서는 루트 게이트로 드래그 전체가 꺼진다.
 */
(function () {
    'use strict';

    var ITEM = ':scope > button[data-os-id]';
    var root = document.querySelector('.n-miller[data-rank-enabled="true"]');
    if (!root || !window.RowDrag) return;

    var hint = document.getElementById('fwRankHint');
    var hintDefault = hint ? hint.textContent : '';
    var snapshot = null;

    document.querySelectorAll('.n-miller-version-panel[data-rank-url]').forEach(function (panel) {
        liveItems(panel).forEach(function (item) {
            window.RowDrag.bind({
                item: item,
                container: panel,
                itemSelector: 'button[data-os-id]',
                handle: '.n-rank-handle',
                payload: 'firmware-rank',
                onStart: function () { snapshot = orderOf(panel); },
                onDrop: function () { save(panel); }
            });
        });
    });

    /** 순위를 가진 항목(= 핸들이 붙은 살아있는 버전). 삭제 행은 핸들이 없어 자연히 빠진다. */
    function liveItems(panel) {
        return Array.prototype.filter.call(
            panel.querySelectorAll(ITEM),
            function (el) { return el.querySelector('.n-rank-handle') !== null; }
        );
    }

    function orderOf(panel) {
        return liveItems(panel).map(function (el) { return el.dataset.osId; });
    }

    function save(panel) {
        var prev = snapshot;
        snapshot = null;
        if (!prev || orderOf(panel).join(',') === prev.join(',')) return;   // 제자리에 놓았으면 저장하지 않는다
        fetch(panel.dataset.rankUrl, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ orderedIds: orderOf(panel).map(Number) })
        }).then(function (resp) {
            if (!resp.ok) { throw new Error('HTTP ' + resp.status); }
            moveLatestMark(panel);
        }).catch(function () {
            restore(panel, prev);
            if (hint) {
                hint.textContent = root.dataset.rankError || hintDefault;
                setTimeout(function () { hint.textContent = hintDefault; }, 4000);
            }
        });
    }

    function restore(panel, order) {
        var byId = {};
        liveItems(panel).forEach(function (el) { byId[el.dataset.osId] = el; });
        var anchor = liveItems(panel)[0];
        order.forEach(function (id) {
            panel.insertBefore(byId[id], anchor);
            anchor = byId[id].nextSibling;
        });
    }

    /*
     * 'latest' 표시 이동 — 술어(순위 1위 enabled)는 서버가 소유하고(BiosService.latestOf), 여기서는
     * 새 규칙을 만들지 않고 서버가 렌더해 둔 상태 태그('활성화')를 읽어 첫 활성 항목으로 표시를 옮긴다.
     */
    function moveLatestMark(panel) {
        var mark = panel.querySelector('[data-latest-mark]');
        var firstEnabled = liveItems(panel).find(function (el) {
            var st = el.querySelector('.n-miller-status-tag');
            return st && st.classList.contains('n-miller-status-tag-on');
        });
        if (mark) mark.remove();
        if (!firstEnabled) return;
        var next = document.createElement('span');
        next.className = 'n-miller-latest';
        next.setAttribute('data-latest-mark', '');
        next.textContent = 'latest';
        firstEnabled.querySelector('.n-miller-version-label').appendChild(next);
    }
})();
