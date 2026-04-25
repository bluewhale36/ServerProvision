/* ============================================================
   management/bios/list.html · management/os/list.html 공용 경량 스크립트
   ─────────────────────────────────────────────────────────────
   "지금 검증" 버튼 → BackgroundJob 으로 비동기 검증 트리거.
   결과(서명/해시 통과 여부) 는 알림 센터(서류가방) 의 작업 카드
   색상으로 확인한다 — 페이지 행에는 별도 표시하지 않는다.
   ============================================================ */
(function () {
    const TAG = '[bios-list]';
    const FEEDBACK_RESET_MS = 2500;

    document.addEventListener('click', async e => {
        const btn = e.target.closest('button[data-verify-url]');
        if (!btn) return;
        const url = btn.dataset.verifyUrl;
        if (!url) return;

        const originalLabel = btn.textContent;
        btn.disabled = true;
        btn.textContent = '검증 요청 중…';

        try {
            const resp = await fetch(url, { method: 'POST', headers: { 'Accept': 'application/json' } });
            const body = await resp.json().catch(() => ({}));
            if (!resp.ok) {
                alert('검증 시작 실패 : ' + (body.message || ('HTTP ' + resp.status)));
                btn.textContent = originalLabel;
                return;
            }
            // jobId 는 background-jobs.js 의 폴링 사이클이 곧바로 알림 카드에 노출시킨다.
            btn.textContent = '알림에서 확인';
            setTimeout(() => { btn.textContent = originalLabel; }, FEEDBACK_RESET_MS);
        } catch (err) {
            console.error(TAG, err);
            alert('검증 요청 중 오류 : ' + err.message);
            btn.textContent = originalLabel;
        } finally {
            btn.disabled = false;
        }
    });
})();
