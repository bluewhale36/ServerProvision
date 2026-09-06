// 탭 그룹(n-tab) 전환 — 탭 클릭 시 같은 그룹의 aria-selected 를 토글하고 대응 패널(.n-tab-panel)만 표시한다.
// 마크업 계약: .n-tabs 안의 .n-tab[data-panel="패널id"], 패널은 .n-tab-panel#패널id. 패널은 탭 그룹의 부모
// 스코프에서 찾으므로 한 페이지에 여러 탭 그룹이 있어도 서로 간섭하지 않는다. 이벤트 위임이라 동적 추가에도 동작.
//
// HF9 — 활성 탭을 URL hash(#패널id)에 남기고 로드 시 복원한다. PRG 로 화면이 다시 그려져도(서버는 첫 탭을 그린다)
// 사용자가 보던 탭으로 돌아오며, 새로고침 · 주소 공유에도 같은 탭이 열린다. replaceState 라 히스토리 항목은
// 늘지 않는다. 맞지 않는 hash(다른 화면에서 온 값)는 무시하고 첫 탭을 그린다.
(function () {
    'use strict';

    function activate(tab, remember) {
        const bar = tab.closest('.n-tabs');
        if (!bar) {
            return;
        }
        const panelId = tab.getAttribute('data-panel');
        const scope = bar.parentElement || document;

        bar.querySelectorAll('.n-tab').forEach(function (t) {
            t.setAttribute('aria-selected', t === tab ? 'true' : 'false');
        });
        scope.querySelectorAll('.n-tab-panel').forEach(function (panel) {
            panel.classList.toggle('is-active', panel.id === panelId);
        });
        if (remember && panelId) {
            history.replaceState(null, '', '#' + panelId);
        }
    }

    document.addEventListener('click', function (e) {
        const tab = e.target.closest('.n-tab[data-panel]');
        if (!tab) {
            return;
        }
        activate(tab, true);
    });

    function restoreFromHash() {
        const panelId = decodeURIComponent((location.hash || '').slice(1));
        if (!panelId) {
            return;
        }
        const tab = Array.prototype.find.call(
            document.querySelectorAll('.n-tab[data-panel]'),
            function (t) { return t.getAttribute('data-panel') === panelId; });
        if (tab) {
            activate(tab, false);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', restoreFromHash);
    } else {
        restoreFromHash();
    }
})();
