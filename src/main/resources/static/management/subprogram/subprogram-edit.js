/* ============================================================
   management/subprogram/subprogram-edit.html 전용 스크립트
   ─────────────────────────────────────────────────────────────
   진입점 상대경로 입력 옆 [탐색] 버튼 → 트리 루트 안만 탐색.
   파일 클릭 시 trim 한 상대경로를 입력에 채우고 패널 닫기.
   상위 버튼은 트리 루트 밖으로 이동 불가.
   ============================================================ */
(function () {
    const TAG = '[subprogram-edit]';

    const form = document.getElementById('subprogramEditForm');
    if (!form) return;

    const treeRootRaw = form.dataset.treeRoot;
    const browseUrl = form.dataset.browseUrl;
    if (!treeRootRaw || !browseUrl) return;

    const treeRoot = normalizeAbs(treeRootRaw);

    const input = document.getElementById('entrypointRelativePath');
    const triggerBtn = document.getElementById('entrypointBrowseBtn');
    const panel = document.getElementById('entrypointBrowsePanel');
    const upBtn = document.getElementById('entrypointBrowseUpBtn');
    const currentPathEl = document.getElementById('entrypointBrowseCurrentPath');
    const statusEl = document.getElementById('entrypointBrowseStatus');
    const entriesEl = document.getElementById('entrypointBrowseEntries');
    const cancelBtn = document.getElementById('entrypointBrowseCancelBtn');
    const clearBtn = document.getElementById('entrypointBrowseClearBtn');

    if (!triggerBtn || !panel) return;

    let currentAbs = treeRoot;

    triggerBtn.addEventListener('click', () => {
        if (panel.hidden) {
            panel.hidden = false;
            currentAbs = treeRoot;
            load(currentAbs);
        } else {
            panel.hidden = true;
        }
    });

    cancelBtn.addEventListener('click', () => { panel.hidden = true; });

    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            input.value = '';
            panel.hidden = true;
        });
    }

    upBtn.addEventListener('click', () => {
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

        let body;
        try {
            const resp = await fetch(browseUrl + '?path=' + encodeURIComponent(absPath), {
                headers: { 'Accept': 'application/json' }
            });
            body = await resp.json().catch(() => ({}));
            if (!resp.ok) {
                statusEl.textContent = '오류 : ' + (body.message || ('HTTP ' + resp.status));
                return;
            }
        } catch (err) {
            console.error(TAG, err);
            statusEl.textContent = '네트워크 오류 : ' + err.message;
            return;
        }

        statusEl.textContent = '';
        currentPathEl.textContent = displayRelative(body.path || absPath);
        upBtn.disabled = (currentAbs === treeRoot);

        const entries = body.entries || [];
        if (entries.length === 0) {
            const li = document.createElement('li');
            li.className = 'n-empty-desc';
            li.style.padding = '8px 12px';
            li.textContent = '비어있는 디렉토리입니다.';
            entriesEl.appendChild(li);
            return;
        }

        for (const entry of entries) {
            const li = document.createElement('li');
            li.style.padding = '6px 12px';
            li.style.cursor = 'pointer';
            li.style.borderBottom = '1px solid var(--n-border)';
            li.style.display = 'flex';
            li.style.alignItems = 'center';
            li.style.gap = '6px';

            const icon = document.createElement('span');
            icon.textContent = (entry.type === 'DIR') ? '📁' : '📄';
            li.appendChild(icon);

            const name = document.createElement('span');
            name.textContent = entry.name + ((entry.type === 'DIR') ? '/' : '');
            li.appendChild(name);

            if (!(entry.type === 'DIR') && typeof entry.size === 'number' && entry.size >= 0) {
                const size = document.createElement('span');
                size.style.marginLeft = 'auto';
                size.style.fontSize = '11px';
                size.style.color = 'var(--n-text-muted)';
                size.textContent = formatBytes(entry.size);
                li.appendChild(size);
            }

            li.addEventListener('click', () => {
                const child = joinPath(currentAbs, entry.name);
                if ((entry.type === 'DIR')) {
                    if (!isWithinTreeRoot(child)) return;
                    currentAbs = child;
                    load(currentAbs);
                } else {
                    // 파일 클릭 → 진입점 적용 + 패널 닫기
                    const rel = toRelative(child, treeRoot);
                    if (rel === null) {
                        statusEl.textContent = '트리 루트 밖의 파일은 진입점으로 사용할 수 없습니다.';
                        return;
                    }
                    input.value = rel;
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
        if (parent.endsWith('/')) return parent + name;
        return parent + '/' + name;
    }

    function normalizeAbs(p) {
        if (!p) return p;
        // 후행 슬래시 제거 (단, 루트 "/" 는 유지)
        if (p.length > 1 && p.endsWith('/')) return p.slice(0, -1);
        return p;
    }

    function formatBytes(n) {
        if (!Number.isFinite(n) || n <= 0) return '0 B';
        if (n < 1024) return n.toFixed(0) + ' B';
        if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
        if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
        return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    }
})();
