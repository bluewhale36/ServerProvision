/* 세팅 정의서 목록 — 휴지통 토글(U3-2-b): querystring 만 갈아끼워 재조회. 행 클릭은 global/row-link.js. */
document.addEventListener('DOMContentLoaded', function () {
    const toggle = document.getElementById('settingIncludeDeletedToggle');
    if (!toggle) return;
    toggle.addEventListener('change', function () {
        const url = new URL(window.location.href);
        if (this.checked) url.searchParams.set('includeDeleted', 'true');
        else url.searchParams.delete('includeDeleted');
        window.location.href = url.toString();
    });
});
