/* ============================================================
   PathBrowser — 서버 디렉토리 탐색 패널 공통 헬퍼.
   ─────────────────────────────────────────────────────────────
   사용처 :
     - iso-new : ISO 파일 클릭으로 isoPath 채움 + "이 경로로 적용" 으로 디렉토리 전송
     - iso-edit : 동일 패턴
     - 향후 path 입력이 있는 form 모두 동일 진입점 재사용
   필요한 fragment : `directory-browse-panel :: browsePanel(prefix, 'directory', label)`
   ============================================================ */
(function (global) {
    'use strict';

    /**
     * 디렉토리 탐색 패널을 input 에 연결한다.
     * @param {Object} opts
     *   - inputId        : path 입력 input element id (필수, dataset.browseUrl 보유)
     *   - browseBtnId    : 탐색 버튼 element id (필수)
     *   - panelPrefix    : fragment prefix (default 'browse')
     *   - includeFiles   : true 면 디렉토리 + 파일 / false 면 디렉토리만 (default true)
     *   - fileHighlight  : (entry) => bool — 파일 강조 여부 (예: ISO 만 파란색)
     *   - onApply        : (path) => void — directory 모드 적용 버튼 클릭 시. 미지정 시 input.value = path + '/'
     *   - onPickFile     : (path) => void — 파일 클릭 시. 미지정 시 input.value = path
     */
    function attach(opts) {
        const inputEl = document.getElementById(opts.inputId);
        const browseBtn = document.getElementById(opts.browseBtnId);
        const prefix = opts.panelPrefix || 'browse';
        const panel = document.getElementById(prefix + 'Panel');
        const upBtn = document.getElementById(prefix + 'UpBtn');
        const currentEl = document.getElementById(prefix + 'CurrentPath');
        const statusEl = document.getElementById(prefix + 'Status');
        const entriesEl = document.getElementById(prefix + 'Entries');
        const cancelBtn = document.getElementById(prefix + 'CancelBtn');
        const applyBtn = document.getElementById(prefix + 'ApplyBtn');
        const browseUrl = inputEl ? inputEl.dataset.browseUrl : null;
        const includeFiles = opts.includeFiles !== false;
        const fileHighlight = typeof opts.fileHighlight === 'function' ? opts.fileHighlight : () => false;
        const onApply = typeof opts.onApply === 'function'
            ? opts.onApply
            : (path) => {
                inputEl.value = path.endsWith('/') ? path : path + '/';
            };
        const onPickFile = typeof opts.onPickFile === 'function'
            ? opts.onPickFile
            : (path) => {
                inputEl.value = path;
            };

        if (!inputEl || !browseBtn || !panel || !browseUrl) return;

        let currentPath = '/';

        function open() {
            if (panel.hidden) {
                const seed = (inputEl.value || '').trim();
                // S5-1 — 빈 input 진입 시 fallback `'/'` 사용하면 백엔드 assertReadablePath 가
                // allowed-roots 외라 거절. 빈 문자열 그대로 보내고 백엔드의 firstAllowedRoot 자동 치환에 위임.
                let initial = '';
                if (seed) {
                    if (seed.endsWith('/')) initial = seed;
                    else {
                        const idx = seed.lastIndexOf('/');
                        initial = idx >= 0 ? (seed.substring(0, idx) || '/') : '/';
                    }
                }
                panel.hidden = false;
                load(initial);
            } else {
                panel.hidden = true;
            }
        }

        async function load(pathStr) {
            statusEl.textContent = '로딩…';
            entriesEl.innerHTML = '';
            try {
                const url = browseUrl + '?includeFiles=' + includeFiles + '&path=' + encodeURIComponent(pathStr);
                const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
                if (!resp.ok) {
                    const body = await resp.json().catch(() => ({}));
                    statusEl.textContent = '오류 : ' + (body.message || ('HTTP ' + resp.status));
                    return;
                }
                const data = await resp.json();
                currentPath = data.path;
                currentEl.textContent = data.path;
                // 서버가 parentPath=null 로 알려주면 allowed-roots 경계 도달 → '상위' 버튼 비활성화.
                if (upBtn) {
                    const atTop = !data.parentPath;
                    // disabled 속성을 쓰면 브라우저가 pointer-events 차단 → cursor / title hover 둘 다 안 뜸.
                    // dataset 으로 상태 표시 + 클릭 핸들러에서 가드 + 시각/툴팁만 부여.
                    upBtn.dataset.atTop = atTop ? 'true' : 'false';
                    upBtn.style.opacity = atTop ? '0.4' : '';
                    upBtn.style.cursor = atTop ? 'not-allowed' : '';
                    upBtn.title = atTop ? '더이상 상위 디렉토리로 진입할 수 없습니다.' : '상위 디렉토리로';
                }
                renderEntries(data);
                const dirCount = data.entries.filter(e => e.type === 'DIR').length;
                const fileCount = data.entries.length - dirCount;
                statusEl.textContent = `${dirCount} 개 디렉토리, ${fileCount} 개 파일`;
            } catch (err) {
                statusEl.textContent = '요청 실패 : ' + err.message;
            }
        }

        function renderEntries(data) {
            entriesEl.innerHTML = '';
            if (!data.entries || data.entries.length === 0) {
                const li = document.createElement('li');
                li.style.padding = '8px 12px';
                li.style.color = 'var(--n-text-muted)';
                li.style.fontSize = '12px';
                li.textContent = '(비어있음)';
                entriesEl.appendChild(li);
                return;
            }
            for (const e of data.entries) {
                const li = document.createElement('li');
                li.style.padding = '6px 12px';
                li.style.cursor = 'pointer';
                li.style.borderBottom = '1px solid var(--n-border)';
                li.style.fontSize = '13px';
                const isDir = e.type === 'DIR';
                const highlight = !isDir && fileHighlight(e);
                const icon = isDir ? '📁' : (highlight ? '💿' : '📄');
                const sizeText = !isDir && typeof e.size === 'number' && e.size >= 0 ? '  · ' + formatBytes(e.size) : '';
                li.innerHTML = `${icon} ${escapeHtml(e.name)}<span style="color: var(--n-text-muted); font-size: 11px;">${escapeHtml(sizeText)}</span>`;
                if (highlight) li.style.background = '#f2f9ff';
                li.addEventListener('click', () => {
                    if (isDir) {
                        const sep = currentPath.endsWith('/') ? '' : '/';
                        load(currentPath + sep + e.name);
                    } else {
                        const sep = currentPath.endsWith('/') ? '' : '/';
                        onPickFile(currentPath + sep + e.name);
                        panel.hidden = true;
                    }
                });
                li.addEventListener('mouseover', () => {
                    li.style.background = highlight ? '#e1f1fe' : '#f0f0f0';
                });
                li.addEventListener('mouseout', () => {
                    li.style.background = highlight ? '#f2f9ff' : '';
                });
                entriesEl.appendChild(li);
            }
        }

        browseBtn.addEventListener('click', open);
        if (cancelBtn) cancelBtn.addEventListener('click', () => {
            panel.hidden = true;
        });
        if (upBtn) {
            upBtn.addEventListener('click', () => {
                if (upBtn.dataset.atTop === 'true') return;
                if (!currentPath || currentPath === '/') return;
                const trimmed = currentPath.endsWith('/') ? currentPath.slice(0, -1) : currentPath;
                const idx = trimmed.lastIndexOf('/');
                load(idx <= 0 ? '/' : trimmed.substring(0, idx));
            });
        }
        if (applyBtn) {
            applyBtn.addEventListener('click', () => {
                if (!currentPath) return;
                onApply(currentPath);
                panel.hidden = true;
            });
        }
    }

    function formatBytes(n) {
        if (!Number.isFinite(n) || n <= 0) return '0 B';
        if (n < 1024) return n.toFixed(0) + ' B';
        if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
        if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
        return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, c => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        }[c]));
    }

    global.PathBrowser = {attach};
})(window);
