/* ============================================================
   ISO 수정 폼 — 디렉토리 탐색 패널 연결.
   탐색 로직 자체는 PathBrowser 공통 헬퍼에 위임 (iso-new.js 와 동일 진입점).
   SSR 폼이라 submit 은 Thymeleaf 가 처리하고, 본 스크립트는 탐색 UX 만 담당.
   ============================================================ */
(function () {
    'use strict';

    if (!window.PathBrowser) {
        console.error('[iso-edit] PathBrowser 미로드 — path-browser.js 가 먼저 로드되어야 합니다.');
        return;
    }

    window.PathBrowser.attach({
        inputId: 'isoPath',
        browseBtnId: 'browseBtn',
        panelPrefix: 'browse',
        includeFiles: true,
        fileHighlight: (entry) => entry.name.toLowerCase().endsWith('.iso')
    });
})();
