// 게스트 서버 목록 — 행 전체 클릭 시 상세 페이지로 이동.
// 셀 내부 <a>(이름 링크) 클릭은 자체 동작하므로 중복 내비게이션을 막는다.
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('#serverTable .n-row-clickable').forEach(function (row) {
        row.addEventListener('click', function (e) {
            if (e.target.closest('a')) return;
            const href = row.getAttribute('data-href');
            if (href) window.location.href = href;
        });
    });
});
