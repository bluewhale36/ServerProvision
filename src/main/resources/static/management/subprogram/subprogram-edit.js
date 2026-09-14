/* ============================================================
   management/subprogram/subprogram-edit.html 전용 스크립트 (R15-1 개정)
   ─────────────────────────────────────────────────────────────
   변형 표 편집: [행 추가] · 행별 [삭제] · 행별 [탐색]. 탐색 패널은 하나(entrypointBrowse)이고
   마지막으로 [탐색] 을 누른 행의 진입점 입력에 파일 상대경로를 채운다. 트리 루트 밖은 진입 불가.
   행 인덱스는 제출 직전 0 부터 다시 매긴다(variants[i].*) — 서버는 제출 순서로 sortOrder 를 부여한다.
   구조 규칙(확장자 · 버전 중복)은 서버(SubprogramVariantRules)가 폼에 되돌려 준다 — 여기서 복제하지 않는다.
   ============================================================ */
(function () {
    const TAG = '[subprogram-edit]';

    const form = document.getElementById('subprogramEditForm');
    if (!form) return;

    const treeRootRaw = form.dataset.treeRoot;
    const browseUrl = form.dataset.browseUrl;
    if (!treeRootRaw || !browseUrl) return;
    const treeRoot = normalizeAbs(treeRootRaw);

    // ── 변형 표 ────────────────────────────────────────────────
    const body = document.getElementById('variantsBody');
    const tmpl = document.getElementById('variantRowTemplate');
    const addBtn = document.getElementById('variantAddBtn');
    const emptyEl = document.getElementById('variantsEmpty');

    function renumber() {
        const rows = body.querySelectorAll('tr.variant-row');
        rows.forEach(function (row, i) {
            row.dataset.index = String(i);
            row.querySelectorAll('input').forEach(function (input) {
                if (input.name) input.name = input.name.replace(/variants\[\d+\]/, 'variants[' + i + ']');
                if (input.id) input.id = input.id.replace(/variants\d+\./, 'variants' + i + '.');
                const ef = input.getAttribute('data-error-field');
                if (ef) input.setAttribute('data-error-field', ef.replace(/variants\[\d+\]/, 'variants[' + i + ']'));
            });
        });
        if (emptyEl) emptyEl.hidden = rows.length > 0;
    }

    if (addBtn && tmpl && body) {
        addBtn.addEventListener('click', function () {
            const index = body.querySelectorAll('tr.variant-row').length;
            const html = tmpl.innerHTML.replace(/__INDEX__/g, String(index));
            body.insertAdjacentHTML('beforeend', html);
            renumber();
            const last = body.querySelector('tr.variant-row:last-child input');
            if (last) last.focus();
        });
    }

    body.addEventListener('click', function (event) {
        const removeBtn = event.target.closest('.variant-remove');
        if (removeBtn) {
            const row = removeBtn.closest('tr.variant-row');
            if (row) row.remove();
            renumber();
            return;
        }
        const browseBtn = event.target.closest('.variant-browse');
        if (browseBtn) {
            const row = browseBtn.closest('tr.variant-row');
            activeInput = row ? row.querySelector('.variant-entrypoint') : null;
            togglePanel();
        }
    });
    renumber();

    // ── 탐색 패널(한 개 · 활성 행에 적용) ───────────────────────
    const panel = document.getElementById('entrypointBrowsePanel');
    const upBtn = document.getElementById('entrypointBrowseUpBtn');
    const currentPathEl = document.getElementById('entrypointBrowseCurrentPath');
    const statusEl = document.getElementById('entrypointBrowseStatus');
    const entriesEl = document.getElementById('entrypointBrowseEntries');
    const cancelBtn = document.getElementById('entrypointBrowseCancelBtn');
    const clearBtn = document.getElementById('entrypointBrowseClearBtn');
    if (!panel) return;

    let activeInput = null;
    let currentAbs = treeRoot;

    function togglePanel() {
        if (panel.hidden || !activeInput) {
            panel.hidden = false;
            currentAbs = treeRoot;
            load(currentAbs);
        } else {
            panel.hidden = true;
        }
    }

    cancelBtn.addEventListener('click', function () { panel.hidden = true; });
    if (clearBtn) {
        clearBtn.addEventListener('click', function () {
            if (activeInput) activeInput.value = '';
            panel.hidden = true;
        });
    }
    upBtn.addEventListener('click', function () {
        if (currentAbs === treeRoot) return; // 루트 밖 진입 금지
        const parent = parentOf(currentAbs);
        if (!parent || !isWithinTreeRoot(parent)) return;
        currentAbs = parent;
        load(currentAbs);
    });

    async function load(absPath) {
        statusEl.textContent = '불러오는 중…';
        entriesEl.innerHTML = '';
        currentPathEl.textContent = displayRelative(absPath);

        let data;
        try {
            // R8-2 — 통합 browse 는 기본 includeFiles=false. 진입점 선택엔 파일 표시가 필수라 명시한다.
            const resp = await fetch(browseUrl + '?path=' + encodeURIComponent(absPath) + '&includeFiles=true', {
                headers: {'Accept': 'application/json'}
            });
            data = await resp.json().catch(function () { return {}; });
            if (!resp.ok) {
                statusEl.textContent = '오류 : ' + (data.message || ('HTTP ' + resp.status));
                return;
            }
        } catch (err) {
            console.error(TAG, err);
            statusEl.textContent = '네트워크 오류 : ' + err.message;
            return;
        }

        statusEl.textContent = '';
        currentPathEl.textContent = displayRelative(data.path || absPath);
        upBtn.disabled = (currentAbs === treeRoot);

        const entries = data.entries || [];
        if (entries.length === 0) {
            const li = document.createElement('li');
            li.className = 'n-empty-desc';
            li.textContent = '비어있는 디렉토리입니다.';
            entriesEl.appendChild(li);
            return;
        }
        for (const entry of entries) {
            const li = document.createElement('li');
            li.className = 'n-browse-entry';
            const icon = document.createElement('span');
            icon.textContent = (entry.type === 'DIR') ? '📁' : '📄';
            li.appendChild(icon);
            const name = document.createElement('span');
            name.textContent = entry.name + ((entry.type === 'DIR') ? '/' : '');
            li.appendChild(name);
            if (entry.type !== 'DIR' && typeof entry.size === 'number' && entry.size >= 0) {
                const size = document.createElement('span');
                size.className = 'n-browse-entry-size';
                size.textContent = UiUtil.formatBytes(entry.size);
                li.appendChild(size);
            }
            li.addEventListener('click', function () {
                const child = joinPath(currentAbs, entry.name);
                if (entry.type === 'DIR') {
                    if (!isWithinTreeRoot(child)) return;
                    currentAbs = child;
                    load(currentAbs);
                } else {
                    const rel = toRelative(child, treeRoot);
                    if (rel === null) {
                        statusEl.textContent = '트리 루트 밖의 파일은 진입점으로 사용할 수 없습니다.';
                        return;
                    }
                    if (activeInput) activeInput.value = rel;
                    panel.hidden = true;
                }
            });
            entriesEl.appendChild(li);
        }
    }

    function isWithinTreeRoot(absPath) {
        const p = normalizeAbs(absPath);
        return p === treeRoot || p.startsWith(treeRoot + '/');
    }
    function toRelative(absPath, root) {
        const p = normalizeAbs(absPath);
        if (!p.startsWith(root)) return null;
        let rel = p.slice(root.length);
        if (rel.startsWith('/')) rel = rel.slice(1);
        return rel;
    }
    function displayRelative(absPath) {
        const rel = toRelative(absPath, treeRoot);
        return rel === null || rel === '' ? '<트리 루트>' : rel;
    }
    function parentOf(absPath) {
        const p = normalizeAbs(absPath);
        const idx = p.lastIndexOf('/');
        if (idx <= 0) return '/';
        return p.slice(0, idx);
    }
    function joinPath(parent, name) {
        return parent.endsWith('/') ? parent + name : parent + '/' + name;
    }
    function normalizeAbs(p) {
        if (!p) return p;
        if (p.length > 1 && p.endsWith('/')) return p.slice(0, -1);
        return p;
    }
})();
