(function () {
    function hasValueControl(node) {
        return !!node && typeof node.value !== 'undefined';
    }

    function joinPath(basePath, name) {
        const sep = basePath.endsWith('/') ? '' : '/';
        return basePath + sep + name;
    }

    function bindModeTabs(opts) {
        const {
            uploadModeInput, folderPane, zipPane, singlePane, existingPane,
            folderInput, zipInput, singleInput,
            tabFolder, tabZip, tabSingle, tabExisting,
            onModeChange
        } = opts;

        if (!uploadModeInput || !folderPane || !zipPane || !singlePane ||
            !tabFolder || !tabZip || !tabSingle) {
            return {
                setMode() {
                }
            };
        }

        function clearIfPresent(control) {
            if (hasValueControl(control)) control.value = '';
        }

        function setMode(mode) {
            const allowed = mode === 'ZIP' || mode === 'SINGLE_FILE' || mode === 'EXISTING_DIRECTORY';
            const normalized = allowed ? mode : 'FOLDER';
            uploadModeInput.value = normalized;
            const isFolder = normalized === 'FOLDER';
            const isZip = normalized === 'ZIP';
            const isSingle = normalized === 'SINGLE_FILE';
            const isExisting = normalized === 'EXISTING_DIRECTORY';

            folderPane.hidden = !isFolder;
            zipPane.hidden = !isZip;
            singlePane.hidden = !isSingle;
            if (existingPane) existingPane.hidden = !isExisting;

            const pairs = [[tabFolder, isFolder], [tabZip, isZip], [tabSingle, isSingle]];
            if (tabExisting) pairs.push([tabExisting, isExisting]);
            pairs.forEach(([btn, active]) => {
                btn.classList.toggle('n-btn-ghost', !active);
                btn.setAttribute('aria-selected', String(active));
            });

            if (!isFolder) clearIfPresent(folderInput);
            if (!isZip) clearIfPresent(zipInput);
            if (!isSingle) clearIfPresent(singleInput);

            if (typeof onModeChange === 'function') onModeChange(normalized);
        }

        tabFolder.addEventListener('click', () => setMode('FOLDER'));
        tabZip.addEventListener('click', () => setMode('ZIP'));
        tabSingle.addEventListener('click', () => setMode('SINGLE_FILE'));
        if (tabExisting) tabExisting.addEventListener('click', () => setMode('EXISTING_DIRECTORY'));
        setMode(uploadModeInput.value);

        return {setMode};
    }

    function bindDirectoryBrowse(opts) {
        const {
            targetInput, browseBtn, browsePanel, browseUpBtn, browseCurrent,
            browseStatus, browseEntries, browseCancelBtn, browseApplyBtn,
            browseUrl, includeFiles, onApplyPath, onFileClick
        } = opts;

        if (!browseBtn || !browsePanel || !browseCurrent || !browseStatus ||
            !browseEntries || !browseUrl) {
            return {
                loadDirectory: async function () {
                }
            };
        }
        let browseCurrentPath = '/';

        browseBtn.addEventListener('click', () => {
            if (browsePanel.hidden) {
                // S5-1 — 빈 input 시 fallback `'/'` 보내면 백엔드 assertReadablePath 가 allowed-roots 외라 403.
                // 빈 문자열 그대로 보내 백엔드 firstAllowedRoot 자동 치환에 위임.
                const seed = (targetInput && targetInput.value ? targetInput.value.trim() : '') || '';
                browsePanel.hidden = false;
                loadDirectory(seed);
            } else {
                browsePanel.hidden = true;
            }
        });
        if (browseCancelBtn) {
            browseCancelBtn.addEventListener('click', () => {
                browsePanel.hidden = true;
            });
        }
        if (browseUpBtn) {
            browseUpBtn.addEventListener('click', () => {
                // S5-1 — 서버 응답 parentPath=null (allowed-roots 경계 도달) 시 더 이상 상위 진입 금지.
                // path-browser.js 의 atTop 가드 (e3a33ae) 와 동일 패턴.
                if (browseUpBtn.dataset.atTop === 'true') return;
                const p = browseCurrentPath;
                if (!p || p === '/') return;
                const trimmed = p.replace(/\/+$/, '');
                const idx = trimmed.lastIndexOf('/');
                loadDirectory(idx <= 0 ? '/' : trimmed.slice(0, idx));
            });
        }
        if (browseApplyBtn) {
            browseApplyBtn.addEventListener('click', () => {
                const applied = browseCurrentPath.endsWith('/') ? browseCurrentPath : browseCurrentPath + '/';
                if (onApplyPath) {
                    onApplyPath(applied, browseCurrentPath);
                } else {
                    if (hasValueControl(targetInput)) targetInput.value = applied;
                }
                browsePanel.hidden = true;
            });
        }

        async function loadDirectory(pathStr) {
            browseStatus.textContent = '로딩…';
            browseEntries.innerHTML = '';
            try {
                const query = includeFiles ? `?path=${encodeURIComponent(pathStr)}&includeFiles=true`
                    : `?path=${encodeURIComponent(pathStr)}`;
                const resp = await fetch(browseUrl + query, {headers: {'Accept': 'application/json'}});
                if (!resp.ok) {
                    const body = await resp.json().catch(() => ({}));
                    browseStatus.textContent = '오류 : ' + (body.message || ('HTTP ' + resp.status));
                    return;
                }
                const data = await resp.json();
                browseCurrentPath = data.path;
                browseCurrent.textContent = data.path;
                // S5-1 — server parentPath=null 이면 allowed-roots 경계 → '상위' 버튼 비활성화 + 안내.
                if (browseUpBtn) {
                    const atTop = !data.parentPath;
                    browseUpBtn.dataset.atTop = atTop ? 'true' : 'false';
                    browseUpBtn.style.opacity = atTop ? '0.4' : '';
                    browseUpBtn.style.cursor = atTop ? 'not-allowed' : '';
                    browseUpBtn.title = atTop ? '더이상 상위 디렉토리로 진입할 수 없습니다.' : '상위 디렉토리로';
                }
                renderEntries(data.entries || []);
                const visibleCount = includeFiles
                    ? (data.entries || []).length
                    : (data.entries || []).filter(entry => entry.type === 'DIR').length;
                browseStatus.textContent = visibleCount + ' 개 항목';
            } catch (err) {
                browseStatus.textContent = '요청 실패 : ' + err.message;
            }
        }

        function renderEntries(entries) {
            browseEntries.innerHTML = '';
            const visible = includeFiles ? entries : entries.filter(entry => entry.type === 'DIR');
            if (visible.length === 0) {
                const li = document.createElement('li');
                li.style.padding = '8px 12px';
                li.style.color = 'var(--n-text-muted)';
                li.style.fontSize = '12px';
                li.textContent = includeFiles ? '(비어있음)' : '(하위 디렉토리 없음)';
                browseEntries.appendChild(li);
                return;
            }
            for (const entry of visible) {
                const li = document.createElement('li');
                li.style.padding = '6px 12px';
                li.style.cursor = 'pointer';
                li.style.borderBottom = '1px solid var(--n-border)';
                li.style.fontSize = '13px';
                const isDir = entry.type === 'DIR';
                // S5-9 회귀 : path-browser.js 와 같은 hidden 시각 처리. dot-prefix 또는 OS-level
                // hidden 속성 시 muted + opacity 0.55. BIOS / BMC / Subprogram 의 탐색도 OS ISO 와
                // 일관되게 시스템 / 메타 파일 시각 분리.
                const isHidden = entry.hidden === true;
                li.textContent = (isDir ? '📁 ' : '📄 ') + entry.name;
                if (isHidden) {
                    li.style.color = 'var(--n-text-muted)';
                    li.style.opacity = '0.55';
                }
                li.addEventListener('click', () => {
                    if (isDir) {
                        loadDirectory(joinPath(browseCurrentPath, entry.name));
                    } else if (onFileClick) {
                        onFileClick(entry, browseCurrentPath);
                        browsePanel.hidden = true;
                    }
                });
                li.addEventListener('mouseover', () => {
                    li.style.background = '#f0f0f0';
                });
                li.addEventListener('mouseout', () => {
                    li.style.background = '';
                });
                browseEntries.appendChild(li);
            }
        }

        return {loadDirectory};
    }

    window.BundleUploadBootstrap = {
        bindModeTabs,
        bindDirectoryBrowse
    };
})();
