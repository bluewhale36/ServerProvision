/* ============================================================
   management/bios/list.html · management/os/list.html ·
   management/bmc/list.html · management/subprogram/list.html 공용 경량 스크립트
   ─────────────────────────────────────────────────────────────
   "검증" 버튼 → BackgroundJob 으로 비동기 검증 트리거.
   Job 완료 (bgjob:completed/failed) 까지 버튼은 비활성 + "검증 중…" 텍스트 유지.
   Job 종료 시 해당 자원의 저장된 integrity snapshot 을 다시 조회해
   상세 패널 badge text / color 를 갱신하고 버튼 상태 복원.
   ============================================================ */
(function () {
    const TAG = '[bios-list]';
    const BADGE_CLASSES = ['n-badge-gray', 'n-badge-green', 'n-badge-orange', 'n-badge-red'];
    const BUTTON_LABEL_IDLE = '검증';
    const BUTTON_LABEL_BUSY = '검증 중…';

    function badgeForMetadata(meta) {
        if (!meta || !meta.resourceType || !meta.resourceId) return null;
        const selectors = {
            OS_ISO: `[data-iso-id="${meta.resourceId}"][data-status-url]`,
            BIOS_BUNDLE: `[data-bios-id="${meta.resourceId}"][data-status-url]`,
            BMC_FIRMWARE: `[data-bmc-id="${meta.resourceId}"][data-status-url]`,
            SUBPROGRAM: `[data-subprogram-id="${meta.resourceId}"][data-status-url]`
        };
        const selector = selectors[meta.resourceType];
        return selector ? document.querySelector(selector) : null;
    }

    /**
     * badge 와 같은 컨테이너(<dd>) 안의 verify 버튼들을 찾는다. 하나의 자원에 보통 1개.
     */
    function verifyButtonsForBadge(badge) {
        if (!badge) return [];
        const container = badge.closest('dd') || badge.parentElement;
        if (!container) return [];
        return Array.from(container.querySelectorAll('button[data-verify-url]'));
    }

    function setButtonBusy(btn) {
        btn.disabled = true;
        btn.textContent = BUTTON_LABEL_BUSY;
    }

    function setButtonIdle(btn) {
        btn.disabled = false;
        btn.textContent = BUTTON_LABEL_IDLE;
    }

    async function refreshBadge(meta) {
        const badge = badgeForMetadata(meta);
        if (!badge) return;
        const statusUrl = badge.dataset.statusUrl;
        if (!statusUrl) return;

        const resp = await fetch(statusUrl, {headers: {'Accept': 'application/json'}});
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

    /**
     * bgjob 종료 시 같은 자원의 verify 버튼 상태 복원.
     */
    function restoreButton(meta) {
        const badge = badgeForMetadata(meta);
        verifyButtonsForBadge(badge).forEach(setButtonIdle);
    }

    document.addEventListener('click', async e => {
        const btn = e.target.closest('button[data-verify-url]');
        if (!btn || btn.disabled) return;
        const url = btn.dataset.verifyUrl;
        if (!url) return;

        setButtonBusy(btn);

        try {
            const resp = await fetch(url, {method: 'POST', headers: {'Accept': 'application/json'}});
            if (!resp.ok) {
                await ErrorModal.fromResponse(resp, {title: '검증 시작 실패'});
                setButtonIdle(btn);
                return;
            }
            // 성공 시에는 disabled 유지. bgjob:completed/failed 도착 시 restoreButton 으로 풀린다.
        } catch (err) {
            console.error(TAG, err);
            ErrorModal.show({message: '검증 요청 중 오류 : ' + err.message, status: 0});
            setButtonIdle(btn);
        }
    });

    document.addEventListener('bgjob:completed', async e => {
        const d = e.detail || {};
        if (d.type !== 'INTEGRITY_VERIFICATION') return;
        try {
            await refreshBadge(d.metadata || {});
            if (window.bgjobToast) {
                window.bgjobToast('무결성 검증이 완료되었습니다.', {variant: 'success'});
            }
        } catch (err) {
            console.error(TAG, err);
        } finally {
            restoreButton(d.metadata || {});
        }
    });

    document.addEventListener('bgjob:failed', async e => {
        const d = e.detail || {};
        if (d.type !== 'INTEGRITY_VERIFICATION') return;
        try {
            await refreshBadge(d.metadata || {});
        } catch (err) {
            console.error(TAG, err);
        } finally {
            restoreButton(d.metadata || {});
        }
    });

    // S5-2 — purge 확인은 confirm-purge fragment + JS 모듈 (form[data-confirm-purge]) 로 일원화.
    // 기존 MK2 의 data-typed-confirm + window.prompt() 핸들러는 이 자리에서 제거됨.
})();
