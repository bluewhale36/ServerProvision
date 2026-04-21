/* ============================================================
   OS 메타데이터 관리 페이지 스크립트
   (admin/os/os-list.html 에서만 사용)
   ------------------------------------------------------------
   기능:
   1) 밀러 컬럼 클릭 상호작용 (OS → 버전 → 상세)
   2) 초기 선택 복원 (selectId 쿼리 파라미터)
   3) 환경 정보 자동 추출 — 다중 태스크 병렬 진행률 표시
   ============================================================ */

(function () {
    const miller = document.querySelector('.n-miller');
    if (!miller) return;

    const nameCol = miller.querySelector('.n-miller-col-names');
    const versionCol = miller.querySelector('.n-miller-col-versions');
    const detailCol = miller.querySelector('.n-miller-col-detail');
    const versionPanels = miller.querySelectorAll('.n-miller-version-panel');
    const detailPanels = miller.querySelectorAll('.n-miller-detail-panel');
    const emptyState = miller.querySelector('#millerEmpty');

    // 상세 섹션 빈 상태 문구 — 선택 단계에 따라 전환
    const EMPTY_OS_MSG = 'OS 를 선택하여 상세 사항 보기';
    const EMPTY_VERSION_MSG = '버전을 선택하여 상세 사항 보기';

    // OSName 그룹 선택 — 버전 패널 노출 + 상세 초기화
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

    // 버전(메타데이터) 선택 — 상세 패널 노출
    function selectOsId(osId) {
        versionCol.querySelectorAll('.n-miller-item').forEach(btn => {
            btn.classList.toggle('n-miller-selected', btn.dataset.osId === osId);
        });
        detailPanels.forEach(panel => {
            panel.classList.toggle('active', panel.dataset.osId === osId);
        });
        if (emptyState) emptyState.classList.add('hidden');
    }

    // 이벤트 위임 — 컬럼 단위로 클릭 리스너 부착
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

    // 초기 선택 (토글/추출 리다이렉트 후 selectId 가 전달된 경우)
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

    // ====== 환경 정보 자동 추출: 다중 태스크 지원 ======
    // 각 클릭마다 페이지 상단 태스크 리스트에 독립적인 카드를 생성하고,
    // 카드마다 자체 폴링 루프를 실행한다. 여러 OS 를 동시에 추출할 수 있다.
    const TASK_STATUS_URL = '/pxe/v1/admin/os/extract-packages/tasks/';
    const POLL_INTERVAL_MS = 500;
    const taskList = document.getElementById('extractTaskList');

    // HTML 이스케이프 (사용자 입력은 아니지만 OS 이름/버전에 특수문자가 섞일 수 있음)
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // 새 태스크 카드 DOM 생성
    function createTaskCard(osName, osVersion) {
        const card = document.createElement('div');
        card.className = 'n-extract-task';
        card.innerHTML =
            '<div class="n-extract-task-header">' +
                '<span class="n-extract-task-title">' +
                    escapeHtml(osName) + ' ' + escapeHtml(osVersion) + ' — 환경 정보 자동 추출' +
                '</span>' +
                '<button type="button" class="n-extract-task-close" aria-label="닫기" style="display:none;">&times;</button>' +
            '</div>' +
            '<div class="n-extract-task-body">' +
                '<div class="n-extract-stage">작업 시작 중...</div>' +
                '<div class="n-progress-bar"><div class="n-progress-bar-fill"></div></div>' +
            '</div>';

        // 닫기 버튼 — 완료/실패 상태에서만 표시되며, 클릭 시 카드 제거
        card.querySelector('.n-extract-task-close').addEventListener('click', () => card.remove());
        return card;
    }

    // 카드의 진행 단계·바 갱신
    function updateTaskProgress(card, task) {
        const stageEl = card.querySelector('.n-extract-stage');
        const barFill = card.querySelector('.n-progress-bar-fill');
        if (stageEl) stageEl.textContent = task.stage || '';
        if (barFill) barFill.style.width = (task.progress || 0) + '%';
    }

    // 카드 바디를 진행 바 → 결과 알림으로 교체하고 닫기 버튼 노출
    function showTaskResult(card, message, success) {
        const body = card.querySelector('.n-extract-task-body');
        const alertClass = success ? 'n-alert-success' : 'n-alert-danger';
        body.innerHTML =
            '<div class="n-alert ' + alertClass + '" style="margin:0;">' +
                escapeHtml(message || (success ? '완료' : '실패')) +
            '</div>';
        const closeBtn = card.querySelector('.n-extract-task-close');
        if (closeBtn) closeBtn.style.display = 'inline-block';
    }

    const INDEX_TASK_STATUS_URL = '/pxe/v1/admin/os/index-repo/tasks/';

    // 각 상세 패널 최초 렌더 시 현재 인덱스 상태(패키지/서비스 수)를 비동기 로드
    document.querySelectorAll('.n-index-btn').forEach(btn => {
        const statusUrl = btn.dataset.statusUrl;
        const badge = btn.querySelector('.n-index-status-badge');
        if (!statusUrl || !badge) return;
        fetch(statusUrl)
            .then(r => r.ok ? r.json() : null)
            .then(data => {
                if (!data) return;
                const p = data.packageCount || 0;
                const s = data.serviceCount || 0;
                if (p === 0 && s === 0) {
                    badge.textContent = '(미인덱싱)';
                } else {
                    badge.textContent = '(패키지 ' + p + ' · 서비스 ' + s + ')';
                }
            })
            .catch(() => { /* 조용히 무시 */ });
    });

    // 저장소 인덱싱 버튼 — 기존 태스크 카드 리스트를 공유해 진행률 표시
    document.querySelectorAll('.n-index-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const startUrl = btn.dataset.startUrl;
            const osName = btn.dataset.osName;
            const osVersion = btn.dataset.osVersion;

            const detailPanel = btn.closest('.n-miller-detail-panel');
            const spinner = btn.querySelector('.n-index-spinner');
            const editBtn = detailPanel ? detailPanel.querySelector('.n-edit-btn') : null;

            btn.disabled = true;
            if (editBtn) editBtn.classList.add('n-btn-disabled');
            spinner.style.display = 'inline-block';

            // 추출 태스크 카드와 동일 스타일 재사용 (타이틀만 수정)
            const card = createTaskCard(osName, osVersion);
            const title = card.querySelector('.n-extract-task-title');
            if (title) title.textContent = osName + ' ' + osVersion + ' — 저장소 인덱스 재생성';
            taskList.prepend(card);

            try {
                const startResp = await fetch(startUrl, {
                    method: 'POST',
                    headers: { 'Accept': 'application/json' }
                });
                if (!startResp.ok) {
                    throw new Error('요청 실패 (HTTP ' + startResp.status + ')');
                }
                const { taskId } = await startResp.json();
                card.dataset.taskId = taskId;

                while (true) {
                    await new Promise(r => setTimeout(r, POLL_INTERVAL_MS));
                    const statusResp = await fetch(INDEX_TASK_STATUS_URL + taskId);
                    if (!statusResp.ok) {
                        throw new Error('상태 조회 실패 (HTTP ' + statusResp.status + ')');
                    }
                    const task = await statusResp.json();
                    updateTaskProgress(card, task);
                    if (task.status === 'COMPLETED') {
                        showTaskResult(card, task.message, true);
                        break;
                    } else if (task.status === 'FAILED') {
                        showTaskResult(card, task.message, false);
                        break;
                    }
                }

                // 완료 후 상태 배지 갱신
                const badge = btn.querySelector('.n-index-status-badge');
                const statusUrl = btn.dataset.statusUrl;
                if (statusUrl && badge) {
                    const r = await fetch(statusUrl);
                    if (r.ok) {
                        const data = await r.json();
                        badge.textContent = '(패키지 ' + (data.packageCount || 0)
                            + ' · 서비스 ' + (data.serviceCount || 0) + ')';
                    }
                }
            } catch (err) {
                showTaskResult(card, '오류: ' + err.message, false);
            } finally {
                btn.disabled = false;
                if (editBtn) editBtn.classList.remove('n-btn-disabled');
                spinner.style.display = 'none';
            }
        });
    });

    // 추출 버튼 클릭 핸들러 — 태스크 시작 + 독립 폴링 루프
    document.querySelectorAll('.n-extract-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const startUrl = btn.dataset.startUrl;
            const osName = btn.dataset.osName;
            const osVersion = btn.dataset.osVersion;

            const detailPanel = btn.closest('.n-miller-detail-panel');
            const spinner = btn.querySelector('.n-extract-spinner');
            const editBtn = detailPanel ? detailPanel.querySelector('.n-edit-btn') : null;

            // 해당 OS 버전의 버튼만 비활성화 (다른 OS 는 계속 조작 가능)
            btn.disabled = true;
            if (editBtn) editBtn.classList.add('n-btn-disabled');
            spinner.style.display = 'inline-block';

            // 태스크 카드 생성 (최신 카드를 최상단에 배치)
            const card = createTaskCard(osName, osVersion);
            taskList.prepend(card);

            try {
                // 1. 태스크 시작 요청 → taskId 수신
                const startResp = await fetch(startUrl, {
                    method: 'POST',
                    headers: { 'Accept': 'application/json' }
                });
                if (!startResp.ok) {
                    throw new Error('요청 실패 (HTTP ' + startResp.status + ')');
                }
                const { taskId } = await startResp.json();
                card.dataset.taskId = taskId;

                // 2. 상태 폴링 — 각 카드가 독립적인 루프를 돌기 때문에 동시 실행에 간섭 없음
                while (true) {
                    await new Promise(r => setTimeout(r, POLL_INTERVAL_MS));
                    const statusResp = await fetch(TASK_STATUS_URL + taskId);
                    if (!statusResp.ok) {
                        throw new Error('상태 조회 실패 (HTTP ' + statusResp.status + ')');
                    }
                    const task = await statusResp.json();

                    updateTaskProgress(card, task);

                    if (task.status === 'COMPLETED') {
                        showTaskResult(card, task.message, true);
                        break;
                    } else if (task.status === 'FAILED') {
                        showTaskResult(card, task.message, false);
                        break;
                    }
                }
            } catch (err) {
                showTaskResult(card, '오류: ' + err.message, false);
            } finally {
                btn.disabled = false;
                if (editBtn) editBtn.classList.remove('n-btn-disabled');
                spinner.style.display = 'none';
            }
        });
    });
})();
