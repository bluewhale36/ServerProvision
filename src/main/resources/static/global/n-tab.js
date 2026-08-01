// 탭 그룹(n-tab) 전환 — 탭 클릭 시 같은 그룹의 aria-selected 를 토글하고 대응 패널(.n-tab-panel)만 표시한다.
// 마크업 계약: .n-tabs 안의 .n-tab[data-panel="패널id"], 패널은 .n-tab-panel#패널id. 패널은 탭 그룹의 부모
// 스코프에서 찾으므로 한 페이지에 여러 탭 그룹이 있어도 서로 간섭하지 않는다. 이벤트 위임이라 동적 추가에도 동작.
document.addEventListener('click', function (e) {
    const tab = e.target.closest('.n-tab[data-panel]');
    if (!tab) {
        return;
    }
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
});
