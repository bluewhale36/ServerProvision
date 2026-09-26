/* ============================================================
   row-link.js — 행 전체가 상세로 가는 목록의 클릭 위임 (S17-4)
   ─────────────────────────────────────────────────────────────
   세팅 정의서 · BIOS 세팅 · 시스템 자산 목록이 같은 8 줄(행마다 리스너 · 셀 안 링크는 제외 · data-href 로
   이동)을 각자 들고 있었다(둘은 템플릿 인라인, 하나는 asset.js). 문서 한 곳에서 위임하면 표가 몇 개든 ·
   행이 나중에 그려지든 같은 규칙이 선다. 셀 안의 링크 · 버튼 · 입력 · 폼은 자기 동작이 있으므로 건드리지
   않는다. 마크업 규약은 하나 — `tr.n-row-clickable[data-href]`.

   S8-1 — 행 안에 이름 링크(a.n-row-link)가 있으면 그 링크를 대신 누른다. 화면 이력 스택(nav-stack.js)은 링크
   클릭에서 "이 목록의 이 행에서 떠났다" 를 쌓으므로, 행을 눌러 곧장 이동하면 돌아올 쪽과 짚을 행을 잃는다.
   ============================================================ */
document.addEventListener('click', function (e) {
    const row = e.target.closest('.n-row-clickable[data-href]');
    if (!row || e.target.closest('a, button, input, select, textarea, form, label')) return;
    const link = row.querySelector('a.n-row-link[href]');
    if (link) { link.click(); return; }
    const href = row.getAttribute('data-href');
    if (href) window.location.href = href;
});
