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

    // S5-4 — '삭제된 항목 포함' 같은 다른 쿼리 토글이 선택 상태를 보존하도록
    // 미러 선택을 URL querystring 에 반영한다.
    function updateUrl(mutate) {
        const params = new URLSearchParams(window.location.search);
        mutate(params);
        const q = params.toString();
        const next = window.location.pathname + (q ? '?' + q : '') + window.location.hash;
        history.replaceState(null, '', next);
    }

    function bindMiller(miller) {
        if (!miller) return;
        const nameCol = miller.querySelector('.n-miller-col-names');
        const versionCol = miller.querySelector('.n-miller-col-versions');
        const detailCol = miller.querySelector('.n-miller-col-detail');
        if (!nameCol || !versionCol || !detailCol) return;

        const versionPanels = miller.querySelectorAll('.n-miller-version-panel');
        const detailPanels = miller.querySelectorAll('.n-miller-detail-panel');
        const emptyState = detailCol.querySelector('.n-miller-empty');

        // 본 미러의 kind ('driver' / 'utility') → URL selectKind = 대문자.
        const kind = miller.dataset.kind ? miller.dataset.kind.toUpperCase() : null;

        function selectScope(scopeKey, opts) {
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
            // C1 선택 → URL : selectKind = 본 미러 kind, selectKey = scope, selectId 제거.
            // 다른 미러의 URL 상태는 덮어쓴다 — 사용자의 마지막 상호작용을 반영.
            if (!opts || !opts.skipUrl) {
                updateUrl(p => {
                    if (kind) p.set('selectKind', kind);
                    p.set('selectKey', scopeKey);
                    p.delete('selectId');
                });
            }
        }

        function selectItem(itemId, opts) {
            versionCol.querySelectorAll('.n-miller-item').forEach(btn => {
                btn.classList.toggle('n-miller-selected', btn.dataset.osId === itemId);
            });
            detailPanels.forEach(panel => {
                panel.classList.toggle('active', panel.dataset.osId === itemId);
            });
            if (emptyState) emptyState.classList.add('hidden');

            // C2 선택 → URL : selectKind / selectKey (item 의 parent panel) / selectId 모두 정합화.
            if (!opts || !opts.skipUrl) {
                const target = versionCol.querySelector(`.n-miller-item[data-os-id="${itemId}"]`);
                const parentPanel = target ? target.closest('.n-miller-version-panel') : null;
                const scopeKey = parentPanel ? parentPanel.dataset.osKey : null;
                updateUrl(p => {
                    if (kind) p.set('selectKind', kind);
                    if (scopeKey) p.set('selectKey', scopeKey);
                    p.set('selectId', itemId);
                });
            }
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

        // 초기 선택 복원 — URL 은 이미 진실이므로 갱신하지 않는다.
        const initialId = miller.dataset.initialSelectId;
        const initialKey = miller.dataset.initialSelectKey;
        if (initialId) {
            const target = miller.querySelector(`.n-miller-item[data-os-id="${initialId}"]`);
            if (target) {
                const versionPanel = target.closest('.n-miller-version-panel');
                if (versionPanel) selectScope(versionPanel.dataset.osKey, { skipUrl: true });
                selectItem(initialId, { skipUrl: true });
            }
        } else if (initialKey) {
            // S5-4 — C1 만 선택된 상태.
            const c1Btn = miller.querySelector(`.n-miller-item[data-os-key="${initialKey}"]`);
            if (c1Btn) selectScope(initialKey, { skipUrl: true });
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.n-subprogram-miller').forEach(bindMiller);
    });
})();
