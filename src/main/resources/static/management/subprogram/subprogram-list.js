/* ============================================================
   management/subprogram/list.html 전용 스크립트
   ─────────────────────────────────────────────────────────────
   페이지에 Miller 두 벌 (Driver / Utility) 이 동시에 떠 있다.
   os-list.js 가 단일 Miller 가정으로 동작하므로 본 파일이 두 Miller 를
   독립 인스턴스로 바인딩한다.

   verify 버튼 클릭 / bgjob 후처리는 bios-list.js 가 처리 (data-subprogram-id 추가).
   ============================================================ */
(function () {
    const EMPTY_VERSION_MSG = '버전을 선택하여 상세 사항 보기';

    function bindMiller(miller) {
        if (!miller) return;
        const nameCol = miller.querySelector('.n-miller-col-names');
        const versionCol = miller.querySelector('.n-miller-col-versions');
        const detailCol = miller.querySelector('.n-miller-col-detail');
        if (!nameCol || !versionCol || !detailCol) return;

        const versionPanels = miller.querySelectorAll('.n-miller-version-panel');
        const detailPanels = miller.querySelectorAll('.n-miller-detail-panel');
        const emptyState = detailCol.querySelector('.n-miller-empty');

        function selectScope(scopeKey) {
            nameCol.querySelectorAll('.n-miller-item').forEach(btn => {
                btn.classList.toggle('n-miller-selected', btn.dataset.osKey === scopeKey);
            });
            versionPanels.forEach(panel => {
                panel.classList.toggle('active', panel.dataset.osKey === scopeKey);
            });
            versionCol.querySelectorAll('.n-miller-item').forEach(btn => {
                btn.classList.remove('n-miller-selected');
            });
            detailPanels.forEach(panel => panel.classList.remove('active'));
            if (emptyState) {
                emptyState.textContent = EMPTY_VERSION_MSG;
                emptyState.classList.remove('hidden');
            }
        }

        function selectItem(itemId) {
            versionCol.querySelectorAll('.n-miller-item').forEach(btn => {
                btn.classList.toggle('n-miller-selected', btn.dataset.osId === itemId);
            });
            detailPanels.forEach(panel => {
                panel.classList.toggle('active', panel.dataset.osId === itemId);
            });
            if (emptyState) emptyState.classList.add('hidden');
        }

        nameCol.addEventListener('click', e => {
            const btn = e.target.closest('.n-miller-item[data-os-key]');
            if (!btn) return;
            selectScope(btn.dataset.osKey);
        });

        versionCol.addEventListener('click', e => {
            const btn = e.target.closest('.n-miller-item[data-os-id]');
            if (!btn) return;
            selectItem(btn.dataset.osId);
        });

        // 초기 선택 복원
        const initialId = miller.dataset.initialSelectId;
        if (initialId) {
            const target = miller.querySelector(`.n-miller-item[data-os-id="${initialId}"]`);
            if (target) {
                const versionPanel = target.closest('.n-miller-version-panel');
                if (versionPanel) selectScope(versionPanel.dataset.osKey);
                selectItem(initialId);
            }
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.n-subprogram-miller').forEach(bindMiller);
    });
})();
