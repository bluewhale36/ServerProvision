/* ============================================================
   management/bios/list.html · management/os/list.html 공용 경량 스크립트
   ─────────────────────────────────────────────────────────────
   "지금 검증" 버튼 → BackgroundJob 으로 비동기 검증 트리거.
   Job 완료 후 해당 자원의 저장된 integrity snapshot 을 다시 조회해
   상세 패널 badge text / color 를 갱신한다.
   ============================================================ */
(function () {
    const TAG = '[bios-list]';
    const FEEDBACK_RESET_MS = 2500;
    const BADGE_CLASSES = ['n-badge-gray', 'n-badge-green', 'n-badge-orange', 'n-badge-red'];

    function badgeForMetadata(meta) {
        if (!meta || !meta.resourceType || !meta.resourceId) return null;
        const selectors = {
            OS_ISO: `[data-iso-id="${meta.resourceId}"][data-status-url]`,
            BIOS_BUNDLE: `[data-bios-id="${meta.resourceId}"][data-status-url]`,
            BMC_FIRMWARE: `[data-bmc-id="${meta.resourceId}"][data-status-url]`
        };
        const selector = selectors[meta.resourceType];
        return selector ? document.querySelector(selector) : null;
    }

    async function refreshBadge(meta) {
        const badge = badgeForMetadata(meta);
        if (!badge) return;
        const statusUrl = badge.dataset.statusUrl;
        if (!statusUrl) return;

        const resp = await fetch(statusUrl, { headers: { 'Accept': 'application/json' } });
        const body = await resp.json().catch(() => ({}));
        if (!resp.ok) {
            throw new Error(body.message || ('HTTP ' + resp.status));
        }

        BADGE_CLASSES.forEach(cls => badge.classList.remove(cls));
        if (body.badgeClass) {
            badge.classList.add(body.badgeClass);
        }
        badge.textContent = body.integrityStatus || 'NOT_VERIFIED';
    }

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

    document.addEventListener('bgjob:completed', async e => {
        const d = e.detail || {};
        if (d.type !== 'INTEGRITY_VERIFICATION') return;
        try {
            await refreshBadge(d.metadata || {});
            if (window.bgjobToast) {
                window.bgjobToast('무결성 검증이 완료되었습니다.', { variant: 'success' });
            }
        } catch (err) {
            console.error(TAG, err);
        }
    });

    document.addEventListener('bgjob:failed', async e => {
        const d = e.detail || {};
        if (d.type !== 'INTEGRITY_VERIFICATION') return;
        try {
            await refreshBadge(d.metadata || {});
        } catch (err) {
            console.error(TAG, err);
        }
    });
})();
