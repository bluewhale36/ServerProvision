// 통합 시스템 자산 대시보드 — 행 전체 클릭 시 data-href 로 상세 이동.
// 여러 영역 표가 한 화면에 오므로 단일 테이블 id 가 아니라 data-href 를 가진 클릭 가능 행에 위임한다.
// 진단 영역 행만 data-href 를 갖고(상세 보유), TFTP 등 읽기 전용 영역 행은 data-href 가 없어 무시된다.
// 셀 내부 링크·버튼·폼 클릭은 자체 동작하므로 중복 내비게이션을 막는다.
document.addEventListener('click', function (e) {
    const row = e.target.closest('.n-row-clickable[data-href]');
    if (!row || e.target.closest('a, button, input, form')) {
        return;
    }
    const href = row.getAttribute('data-href');
    if (href) {
        window.location.href = href;
    }
});
