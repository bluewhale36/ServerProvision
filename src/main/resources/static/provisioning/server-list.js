// 게스트 서버 목록 — 행 전체 클릭 시 상세 페이지로 이동.
// document 위임 방식: 실시간 스트림(S7)이 테이블 영역을 통째로 교체해도 리스너가 살아남는다.
// 셀 내부 <a>(이름 링크) 클릭은 자체 동작하므로 중복 내비게이션을 막는다.
document.addEventListener('click', function (e) {
    const row = e.target.closest('#serverTable .n-row-clickable');
    if (!row || e.target.closest('a')) return;
    const href = row.getAttribute('data-href');
    if (href) window.location.href = href;
});
