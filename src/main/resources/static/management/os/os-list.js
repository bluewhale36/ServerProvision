/* ============================================================
   management/os/list.html 전용 스크립트
   ─────────────────────────────────────────────────────────────
   1) Miller Columns 상호작용 (C1 → C2 → C3)
   2) 초기 선택 복원 (data-initial-select-id)
   3) A1-1 : 추출 시작은 이 파일이, 진행률·완료 감지는 Stage S1 알림 센터가 담당.
   4) COMPS_EXTRACTION 완료 이벤트 처리:
      - 완료 대상 OS 가 현재 active 면 env-groups fragment 를 즉시 부분 갱신.
      - active 가 아니면 stale 로 표시. 사용자가 그 OS 를 다시 클릭하면 select 시점에 refresh.

   ISO 등록 후처리는 ISO_REGISTRATION job 완료 시점에 반영된다.
   ============================================================ */

(function () {
    const miller = document.querySelector('.n-miller');
    if (!miller) return;

    const nameCol = miller.querySelector('.n-miller-col-names');
    const versionCol = miller.querySelector('.n-miller-col-versions');
    const versionPanels = miller.querySelectorAll('.n-miller-version-panel');
    const detailPanels = miller.querySelectorAll('.n-miller-detail-panel');
    const emptyState = miller.querySelector('#millerEmpty');

    // S5-5 — C3 안내 텍스트 2 상태. 도메인별로 data-empty-before / data-empty-after 가
    // miller-empty element 에 박혀 있어 EMPTY_VERSION_MSG 단일 상수가 더 이상 적합하지 않다.
    function emptyTextBefore() {
        return (emptyState && emptyState.dataset.emptyBefore) || '';
    }
    function emptyTextAfter() {
        return (emptyState && emptyState.dataset.emptyAfter) || '';
    }

    // S5-4 — 미러 선택 시 URL querystring 도 동기화한다. '삭제된 항목 포함' 토글 같은
    // 다른 쿼리 변경 동작이 선택 상태를 자연스럽게 보존하도록 하는 것이 목적.
    // BIOS / BMC 는 정적 data-board-aware="true" 마커로 식별. 동적 th:data-initial-select-board-id
    // 는 값이 null 일 때 Thymeleaf 가 속성을 emit 하지 않아 hasAttribute 로는 판별 불가.
    const boardAware = miller.dataset.boardAware === 'true';

    function updateUrl(mutate) {
        const params = new URLSearchParams(window.location.search);
        mutate(params);
        const q = params.toString();
        const next = window.location.pathname + (q ? '?' + q : '') + window.location.hash;
        history.replaceState(null, '', next);
    }

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

    function selectOsName(osKey, opts) {
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
            // S5-5 — C1 선택 후의 C3 메시지로 전환.
            emptyState.textContent = emptyTextAfter();
            emptyState.classList.remove('hidden');
        }
        // C1 선택은 C2 선택을 해제한다 → URL 의 selectId 제거.
        // board-aware (BIOS / BMC) 에서는 osKey 가 boardId → selectBoardId 로,
        // non-board-aware (OS / Board) 에서는 osKey 가 enum 문자열 → selectKey 로.
        if (!opts || !opts.skipUrl) {
            updateUrl(p => {
                p.delete('selectId');
                if (boardAware) p.set('selectBoardId', osKey);
                else p.set('selectKey', osKey);
            });
        }
    }

    function selectOsId(osId, opts) {
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

        if (!opts || !opts.skipUrl) {
            updateUrl(p => p.set('selectId', osId));
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
    const initialBoardId = miller.dataset.initialSelectBoardId;
    const initialKey = miller.dataset.initialSelectKey;
    // 초기 복원은 URL 갱신을 일으키지 않는다 — 이미 URL 이 진실.
    if (initialId) {
        const versionBtn = versionCol.querySelector(
            '.n-miller-item[data-os-id="' + initialId + '"]'
        );
        if (versionBtn) {
            const panel = versionBtn.closest('.n-miller-version-panel');
            if (panel) {
                selectOsName(panel.dataset.osKey, { skipUrl: true });
                selectOsId(initialId, { skipUrl: true });
            }
        }
    } else if (initialBoardId) {
        const boardBtn = nameCol.querySelector(
            '.n-miller-item[data-os-key="' + initialBoardId + '"]'
        );
        if (boardBtn) {
            selectOsName(initialBoardId, { skipUrl: true });
        }
    } else if (initialKey) {
        // S5-4 — C1 만 선택된 상태 (OS / Board 페이지). C2 는 비어있음.
        const c1Btn = nameCol.querySelector(
            '.n-miller-item[data-os-key="' + initialKey + '"]'
        );
        if (c1Btn) {
            selectOsName(initialKey, { skipUrl: true });
        }
    }

    try {
        const pendingToast = sessionStorage.getItem('os.isoRegistration.toast');
        if (pendingToast && window.bgjobToast) {
            window.bgjobToast(pendingToast, { variant: 'info' });
            sessionStorage.removeItem('os.isoRegistration.toast');
        }
    } catch (_) { /* ignore */ }

    // ---- 추출 시작 (A1-1) --------------------------------------

    const EXTRACT_URL = (osId, isoId) =>
        `/management/os/${osId}/iso/${isoId}/extract`;

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
            const resp = await fetch(`/management/os/${encodeURIComponent(osId)}/env-groups-fragment`, {
                headers: { 'Accept': 'text/html' }
            });
            if (!resp.ok) return;
            const html = await resp.text();
            const wrap = panel.querySelector('.n-env-groups-wrap');
            if (wrap) wrap.outerHTML = html;
        } catch (_) { /* 다음 select 시점에 재시도 */ }
    }

    async function refreshIsoSection(panel, osId) {
        const wrap = panel.querySelector('.n-iso-section-wrap');
        if (!wrap) return;

        const openIsoIds = Array.from(
            panel.querySelectorAll('.n-accordion-item[open][data-iso-id]')
        ).map(el => el.dataset.isoId);

        try {
            const resp = await fetch(`/management/os/${encodeURIComponent(osId)}/iso-section-fragment`, {
                headers: { 'Accept': 'text/html' }
            });
            if (!resp.ok) return;
            const html = await resp.text();
            wrap.outerHTML = html;

            for (const isoId of openIsoIds) {
                const item = panel.querySelector(`.n-accordion-item[data-iso-id="${CSS.escape(isoId)}"]`);
                if (item) item.setAttribute('open', 'open');
            }
            bindExtractButtons(panel);
        } catch (_) { /* 다음 이벤트까지 현 상태 유지 */ }
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
                    `/management/os/${encodeURIComponent(osId)}/iso/${encodeURIComponent(isoId)}/provisions`,
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
        if (d.type === 'ISO_REGISTRATION') {
            if (window.bgjobToast) {
                window.bgjobToast('ISO 등록이 완료되었습니다.', { variant: 'success' });
            }
            const osId = d.metadata && d.metadata.osId ? d.metadata.osId : null;
            const panel = osId ? findPanelByOsId(osId) : null;
            if (panel) refreshIsoSection(panel, osId);
            return;
        }
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
        if (d.type === 'ISO_REGISTRATION') {
            if (window.bgjobToast) {
                window.bgjobToast('ISO 등록 실패: ' + (d.subtitle || d.title || ''), { variant: 'error' });
            }
            return;
        }
        if (d.type !== 'COMPS_EXTRACTION') return;
        restoreExtractByIsoPath(d.subtitle);
        if (window.bgjobToast) {
            window.bgjobToast('ISO 추출 실패: ' + (d.subtitle || d.title || ''), { variant: 'error' });
        }
    });
})();
