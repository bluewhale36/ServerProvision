// 진단 자산 대시보드 — 행 전체 클릭 시 그 자산 상세 페이지로 이동 (provisioning/server-list.js 미러).
// document 위임 방식. 셀 내부 링크·버튼·폼 클릭은 자체 동작하므로 중복 내비게이션을 막는다.
document.addEventListener('click', function (e) {
    const row = e.target.closest('#diagnosticAssetTable .n-row-clickable');
    if (!row || e.target.closest('a, button, input, form')) {
        return;
    }
    const href = row.getAttribute('data-href');
    if (href) {
        window.location.href = href;
    }
});
