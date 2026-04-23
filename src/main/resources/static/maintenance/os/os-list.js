/* ============================================================
   maintenance/os/list.html 전용 스크립트
   ─────────────────────────────────────────────────────────────
   1) Miller Columns 상호작용 (C1 → C2 → C3)
   2) 초기 선택 복원 (data-initial-select-id)
   3) A1-1 : 추출 시작은 이 파일이, 진행률·완료 감지는 Stage S1 알림 센터가 담당.
   4) COMPS_EXTRACTION 완료 이벤트 처리:
      - 완료 대상 OS 가 현재 active 면 env-groups fragment 를 즉시 부분 갱신.
      - active 가 아니면 stale 로 표시. 사용자가 그 OS 를 다시 클릭하면 select 시점에 refresh.

   ISO 업로드는 foreground XHR 로 수행되며 완료 시 iso-new.js 가 직접 목록 페이지로
   redirect 하므로 여기서 별도 이벤트 처리를 하지 않는다.
   ============================================================ */

(function () {
    const miller = document.querySelector('.n-miller');
    if (!miller) return;

    const nameCol = miller.querySelector('.n-miller-col-names');
    const versionCol = miller.querySelector('.n-miller-col-versions');
    const versionPanels = miller.querySelectorAll('.n-miller-version-panel');
    const detailPanels = miller.querySelectorAll('.n-miller-detail-panel');
    const emptyState = miller.querySelector('#millerEmpty');

    const EMPTY_VERSION_MSG = '버전을 선택하여 상세 사항 보기';

    // ---- Stale 추적 --------------------------------------------
    // key: osId (string), value: Set<string>   (refresh 해야 할 fragment 타입: 현재 'env' 만 사용)
    const stalePanels = new Map();

    function markStale(osId, kind) {
        if (!osId) return;
        const key = String(osId);
        if (!stalePanels.has(key)) stalePanels.set(key, new Set());
        stalePanels.get(key).add(kind);
    }

    function clearStale(osId, kind) {
        const key = String(osId);
        const set = stalePanels.get(key);
        if (!set) return;
        set.delete(kind);
        if (set.size === 0) stalePanels.delete(key);
    }

    function findPanelByOsId(osId) {
        if (!osId) return null;
        return miller.querySelector(`.n-miller-detail-panel[data-os-id="${osId}"]`);
    }

    function activeOsId() {
        const p = miller.querySelector('.n-miller-detail-panel.active');
        return p ? p.dataset.osId : null;
    }

    // ---- Miller 상호작용 ---------------------------------------

    function selectOsName(osKey) {
        nameCol.querySelectorAll('.n-miller-item').forEach(btn => {
            btn.classList.toggle('n-miller-selected', btn.dataset.osKey === osKey);
        });
        versionPanels.forEach(panel => {
            panel.classList.toggle('active', panel.dataset.osKey === osKey);
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

    function selectOsId(osId) {
        versionCol.querySelectorAll('.n-miller-item').forEach(btn => {
            btn.classList.toggle('n-miller-selected', btn.dataset.osId === osId);
        });
        detailPanels.forEach(panel => {
            panel.classList.toggle('active', panel.dataset.osId === osId);
        });
        if (emptyState) emptyState.classList.add('hidden');

        // 선택 시점에 stale 이 있으면 refresh — 다른 OS 를 보는 동안 완료된 추출 결과를 이 시점에 반영.
        const panel = findPanelByOsId(osId);
        const set = stalePanels.get(String(osId));
        if (panel && set) {
            if (set.has('env')) { refreshEnvGroups(panel, osId); clearStale(osId, 'env'); }
        }
    }

    nameCol.addEventListener('click', e => {
        const btn = e.target.closest('.n-miller-item');
        if (!btn) return;
        selectOsName(btn.dataset.osKey);
    });

    versionCol.addEventListener('click', e => {
        const btn = e.target.closest('.n-miller-item');
        if (!btn) return;
        selectOsId(btn.dataset.osId);
    });

    const initialId = miller.dataset.initialSelectId;
    if (initialId) {
        const versionBtn = versionCol.querySelector(
            '.n-miller-item[data-os-id="' + initialId + '"]'
        );
        if (versionBtn) {
            const panel = versionBtn.closest('.n-miller-version-panel');
            if (panel) {
                selectOsName(panel.dataset.osKey);
                selectOsId(initialId);
            }
        }
    }

    // ---- 추출 시작 (A1-1) --------------------------------------

    const EXTRACT_URL = (osId, isoId) =>
        `/pxe/v1/maintenance/os/${osId}/iso/${isoId}/extract`;

    const EXTRACT_DEFAULT_LABEL = '추출';
    const EXTRACT_RUNNING_LABEL = '추출 중';

    async function startExtract(btn) {
        const osId = btn.dataset.osId;
        const isoId = btn.dataset.isoId;
        if (!osId || !isoId) return;

        // 원래 라벨 보존 후 "추출 중" 으로 고정 — 완료/실패 이벤트 수신 시 복원된다.
        if (!btn.dataset.originalLabel) btn.dataset.originalLabel = btn.textContent;
        btn.disabled = true;
        btn.textContent = EXTRACT_RUNNING_LABEL;

        try {
            const resp = await fetch(EXTRACT_URL(osId, isoId), {
                method: 'POST',
                headers: { 'Accept': 'application/json' }
            });
            if (!resp.ok) {
                // 409 = 이미 추출된 ISO 등 의미 있는 충돌. 이 경우 버튼을 "추출됨" 으로 굳혀둔다.
                if (resp.status === 409) {
                    markExtractedByIsoPath(findIsoPathFromButton(btn));
                    if (window.bgjobToast) {
                        window.bgjobToast('이미 추출된 ISO 입니다.', { variant: 'info' });
                    }
                    return;
                }
                throw new Error('요청 실패 (HTTP ' + resp.status + ')');
            }
            await resp.json();
            if (window.bgjobToast) {
                window.bgjobToast('ISO 추출 진행 확인…', { variant: 'info' });
            }
            // 버튼은 이 상태로 유지. 완료 / 실패 감지 시 mark/restore 가 정리.
        } catch (err) {
            if (window.bgjobToast) {
                window.bgjobToast('추출 시작 실패: ' + err.message, { variant: 'error' });
            }
            restoreExtractButton(btn);
        }
    }

    function findIsoPathFromButton(btn) {
        const item = btn.closest('.n-accordion-item');
        return item ? item.dataset.isoPath : null;
    }

    function restoreExtractButton(btn) {
        btn.disabled = false;
        btn.textContent = btn.dataset.originalLabel || EXTRACT_DEFAULT_LABEL;
        delete btn.dataset.originalLabel;
    }

    function findAccItemsByIsoPath(isoPath) {
        if (!isoPath) return [];
        return Array.from(document.querySelectorAll(
            `.n-accordion-item[data-iso-path="${CSS.escape(isoPath)}"]`
        ));
    }

    function restoreExtractByIsoPath(isoPath) {
        for (const item of findAccItemsByIsoPath(isoPath)) {
            const extractBtn = item.querySelector('.n-btn-extract');
            if (extractBtn) restoreExtractButton(extractBtn);
        }
    }

    /** 추출이 완료된 ISO 의 버튼을 "추출됨" + 비활성 상태로 굳혀둔다. */
    function markExtractedByIsoPath(isoPath) {
        for (const item of findAccItemsByIsoPath(isoPath)) {
            const extractBtn = item.querySelector('.n-btn-extract');
            if (!extractBtn) continue;
            extractBtn.disabled = true;
            extractBtn.textContent = '추출됨';
            extractBtn.classList.remove('n-btn-primary');
            extractBtn.classList.add('n-btn-outline-success');
            delete extractBtn.dataset.originalLabel;
        }
    }

    function bindExtractButtons(scope) {
        (scope || document).querySelectorAll('.n-btn-extract').forEach(btn => {
            if (btn.dataset.bound === '1') return;
            btn.dataset.bound = '1';
            btn.addEventListener('click', () => startExtract(btn));
        });
    }
    bindExtractButtons();

    // ---- Fragment 부분 갱신 ------------------------------------

    function findOsIdByIsoPath(isoPath) {
        const item = findAccItemsByIsoPath(isoPath)[0];
        return item ? item.dataset.osId : null;
    }

    async function refreshEnvGroups(panel, osId) {
        try {
            const resp = await fetch(`/pxe/v1/maintenance/os/${encodeURIComponent(osId)}/env-groups-fragment`, {
                headers: { 'Accept': 'text/html' }
            });
            if (!resp.ok) return;
            const html = await resp.text();
            const wrap = panel.querySelector('.n-env-groups-wrap');
            if (wrap) wrap.outerHTML = html;
        } catch (_) { /* 다음 select 시점에 재시도 */ }
    }

    // 추출 완료 시 해당 ISO 아코디언 행의 "설치 환경" / "패키지 그룹" dd 셀만 교체한다.
    // 전체 아코디언을 재렌더하면 사용자가 열어둔 다른 행이 닫히므로 최소 단위로 끊는다.
    async function refreshIsoProvisions(isoPath) {
        const items = findAccItemsByIsoPath(isoPath);
        if (items.length === 0) return;
        for (const item of items) {
            const osId = item.dataset.osId;
            const isoId = item.dataset.isoId;
            if (!osId || !isoId) continue;
            try {
                const resp = await fetch(
                    `/pxe/v1/maintenance/os/${encodeURIComponent(osId)}/iso/${encodeURIComponent(isoId)}/provisions`,
                    { headers: { 'Accept': 'application/json' } }
                );
                if (!resp.ok) continue;
                const data = await resp.json();
                applyProvisionsToItem(item, data);
            } catch (_) { /* 다음 select 시점의 env-groups refresh 에 맡긴다 */ }
        }
    }

    function applyProvisionsToItem(item, data) {
        const envCodes = Array.isArray(data.providedEnvironmentCodes) ? data.providedEnvironmentCodes : [];
        const groupCount = typeof data.providedPackageGroupCount === 'number' ? data.providedPackageGroupCount : 0;

        const envDd = item.querySelector('dd[data-field="env-codes"]');
        if (envDd) {
            envDd.innerHTML = '';
            const span = document.createElement('span');
            if (envCodes.length === 0) {
                span.className = 'n-accordion-muted';
                span.textContent = '—';
            } else {
                span.textContent = envCodes.join(' · ');
            }
            envDd.appendChild(span);
        }

        const grpDd = item.querySelector('dd[data-field="package-group-count"]');
        if (grpDd) {
            grpDd.innerHTML = '';
            const span = document.createElement('span');
            if (groupCount === 0) {
                span.className = 'n-accordion-muted';
                span.textContent = '—';
            } else {
                span.textContent = groupCount + ' 개';
            }
            grpDd.appendChild(span);
        }
    }

    // ---- 알림 센터 완료 / 실패 이벤트 수신 -----------------------

    document.addEventListener('bgjob:completed', e => {
        const d = e.detail || {};
        if (d.type !== 'COMPS_EXTRACTION') return;

        // 1) 추출 버튼을 "추출됨" 상태로 굳힘 (어느 OS 가 focused 든 무관)
        markExtractedByIsoPath(d.subtitle);

        // 2) 해당 ISO 아코디언 행의 설치 환경·패키지 그룹 값 갱신.
        //    active OS 와 무관하게 DOM 은 항상 존재하므로 즉시 교체. 다른 OS 의 ISO 행도 정확히 반영된다.
        refreshIsoProvisions(d.subtitle);

        // 3) 완료 토스트
        if (window.bgjobToast) {
            window.bgjobToast('ISO 추출이 완료되었습니다.', { variant: 'success' });
        }

        // 4) 환경·패키지 그룹 섹션(아코디언 하단) 갱신 — active 일 때만, 아니면 stale 마킹
        const targetOsId = findOsIdByIsoPath(d.subtitle);
        if (!targetOsId) return;
        const active = activeOsId();
        if (active === String(targetOsId)) {
            const panel = findPanelByOsId(targetOsId);
            if (panel) refreshEnvGroups(panel, targetOsId);
        } else {
            markStale(targetOsId, 'env');
        }
    });

    document.addEventListener('bgjob:failed', e => {
        const d = e.detail || {};
        if (d.type !== 'COMPS_EXTRACTION') return;
        restoreExtractByIsoPath(d.subtitle);
        if (window.bgjobToast) {
            window.bgjobToast('ISO 추출 실패: ' + (d.subtitle || d.title || ''), { variant: 'error' });
        }
    });
})();
