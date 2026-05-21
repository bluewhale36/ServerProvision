/**
 * S5-4 — '삭제된 항목 포함' 체크박스 공통 토글.
 *
 * 5 도메인 (OS / Board / BIOS / BMC / Subprogram) list 페이지의 inline
 * onchange 핸들러 ("?includeDeleted=true 또는 pathname") 를 대체한다.
 *
 * 기존 핸들러는 querystring 을 통째로 버려 selectId / selectBoardId /
 * selectKind 등의 miller 선택 상태가 휘발됐다. 본 모듈은 현재 URL 의
 * querystring 을 URLSearchParams 로 보존한 채 includeDeleted 키만 토글한다.
 *
 * 사용 :
 *   <input type="checkbox"
 *          th:checked="${includeDeleted}"
 *          data-include-deleted-toggle>
 *   <script th:src="@{/global/include-deleted-toggle.js}"></script>
 */
(function () {
    'use strict';

    function buildNextUrl(checked) {
        const params = new URLSearchParams(window.location.search);
        if (checked) params.set('includeDeleted', 'true');
        else params.delete('includeDeleted');
        const q = params.toString();
        return window.location.pathname + (q ? '?' + q : '');
    }

    function attachAll() {
        const checkboxes = document.querySelectorAll(
            'input[type="checkbox"][data-include-deleted-toggle]'
        );
        checkboxes.forEach(cb => {
            cb.addEventListener('change', () => {
                window.location.href = buildNextUrl(cb.checked);
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', attachAll);
    } else {
        attachAll();
    }

    // unit test / 다른 모듈 재사용 export
    window.IncludeDeletedToggle = {buildNextUrl: buildNextUrl};
})();
