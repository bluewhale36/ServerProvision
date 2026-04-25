/* ============================================================
   management/bios/bios-new.html 전용 스크립트 (v3 — 번들 업로드)
   ─────────────────────────────────────────────────────────────
   폴더/zip 탭 전환 · Intent 사전검증 · XHR foreground 업로드.
   iso-new.js 와 공통 뼈대를 공유하지만 번들 스키마에 맞춰 재구성.
   3회 반복(A3/A4/A5) 확보 후 `static/global/file-upload-foreground.js` 로 승격 예정.
   ============================================================ */
(function () {
    const TAG = '[bios-new]';
    console.log(TAG, 'script loaded');

    const form = document.getElementById('biosForm');
    if (!form) return;

    const uploadUrl = form.dataset.uploadUrl;
    const intentUrl = form.dataset.intentUrl;
    const listUrl   = form.dataset.listUrl;

    const submitBtn = document.getElementById('submitBtn');
    const cancelLink = document.getElementById('cancelLink');
    const backLink = document.getElementById('backLink');

    const progressBox  = document.getElementById('uploadProgress');
    const progressBar  = document.getElementById('uploadBar');
    const progressText = document.getElementById('uploadText');
    const errorBox     = document.getElementById('uploadError');

    const uploadModeInput = document.getElementById('uploadMode');
    const folderPane = document.getElementById('folderPane');
    const zipPane    = document.getElementById('zipPane');
    const singlePane = document.getElementById('singlePane');
    const folderInput = document.getElementById('folderFiles');
    const zipInput    = document.getElementById('zipFile');
    const singleInput = document.getElementById('singleFile');
    const tabFolder = document.getElementById('modeTabFolder');
    const tabZip    = document.getElementById('modeTabZip');
    const tabSingle = document.getElementById('modeTabSingle');

    const targetDirInput = document.getElementById('targetDirectory');
    const browseBtn       = document.getElementById('browseBtn');
    const browsePanel     = document.getElementById('browsePanel');
    const browseUpBtn     = document.getElementById('browseUpBtn');
    const browseCurrent   = document.getElementById('browseCurrentPath');
    const browseStatus    = document.getElementById('browseStatus');
    const browseEntries   = document.getElementById('browseEntries');
    const browseCancelBtn = document.getElementById('browseCancelBtn');
    const browseApplyBtn  = document.getElementById('browseApplyBtn');
    const browseUrl       = targetDirInput ? targetDirInput.dataset.browseUrl : null;

    // 브라우저 패널 내부에서 이동 중인 경로 (확정 전)
    let browseCurrentPath = '/';

    if (!form || !uploadUrl || !intentUrl || !listUrl || !submitBtn) return;

    let activeXhr = null;
    let uploading = false;

    const cancelOriginal = cancelLink ? {
        tag: cancelLink.tagName,
        text: cancelLink.textContent,
        href: cancelLink.getAttribute('href')
    } : null;

    window.addEventListener('beforeunload', e => {
        if (!uploading) return;
        e.preventDefault();
        e.returnValue = '';
    });

    // ---- 탭 전환 ------------------------------------------------------

    function setMode(mode) {
        uploadModeInput.value = mode;
        const isFolder = mode === 'FOLDER';
        const isZip    = mode === 'ZIP';
        const isSingle = mode === 'SINGLE_FILE';

        folderPane.hidden = !isFolder;
        zipPane.hidden    = !isZip;
        singlePane.hidden = !isSingle;

        [[tabFolder, isFolder], [tabZip, isZip], [tabSingle, isSingle]].forEach(([btn, active]) => {
            btn.classList.toggle('n-btn-ghost', !active);
            btn.setAttribute('aria-selected', String(active));
        });

        // 선택되지 않은 입력 초기화 — 잘못 섞여 전송되는 것 방지
        if (!isFolder) folderInput.value = '';
        if (!isZip)    zipInput.value    = '';
        if (!isSingle) singleInput.value = '';
    }
    tabFolder.addEventListener('click', () => setMode('FOLDER'));
    tabZip.addEventListener('click',    () => setMode('ZIP'));
    tabSingle.addEventListener('click', () => setMode('SINGLE_FILE'));

    // ---- 경로 브라우저 -----------------------------------------------

    if (browseBtn && browseUrl) {
        browseBtn.addEventListener('click', () => {
            if (browsePanel.hidden) {
                const seed = targetDirInput.value.trim() || '/';
                browsePanel.hidden = false;
                loadDirectory(seed);
            } else {
                browsePanel.hidden = true;
            }
        });
    }
    if (browseCancelBtn) {
        browseCancelBtn.addEventListener('click', () => { browsePanel.hidden = true; });
    }
    if (browseUpBtn) {
        browseUpBtn.addEventListener('click', () => {
            // 현재 path 의 상위. 서버에서 정규화해 돌려주므로 직접 계산.
            const p = browseCurrentPath;
            if (!p || p === '/') return;
            const trimmed = p.replace(/\/+$/, '');
            const idx = trimmed.lastIndexOf('/');
            const up = idx <= 0 ? '/' : trimmed.slice(0, idx);
            loadDirectory(up);
        });
    }
    if (browseApplyBtn) {
        browseApplyBtn.addEventListener('click', () => {
            if (!browseCurrentPath) return;
            // 끝에 슬래시 1개로 정규화 — 서버는 양쪽 모두 수용하지만 UX 일관성
            const applied = browseCurrentPath.endsWith('/') ? browseCurrentPath : browseCurrentPath + '/';
            targetDirInput.value = applied;
            browsePanel.hidden = true;
        });
    }

    async function loadDirectory(pathStr) {
        browseStatus.textContent = '로딩…';
        browseEntries.innerHTML = '';
        try {
            const url = browseUrl + '?path=' + encodeURIComponent(pathStr);
            const resp = await fetch(url, { headers: { 'Accept': 'application/json' } });
            if (!resp.ok) {
                const body = await resp.json().catch(() => ({}));
                browseStatus.textContent = '오류 : ' + (body.message || ('HTTP ' + resp.status));
                return;
            }
            const data = await resp.json();
            browseCurrentPath = data.path;
            browseCurrent.textContent = data.path;
            renderEntries(data);
            browseStatus.textContent = data.entries.length + ' 개 하위 디렉토리';
        } catch (err) {
            browseStatus.textContent = '요청 실패 : ' + err.message;
        }
    }

    function renderEntries(data) {
        browseEntries.innerHTML = '';
        if (!data.entries || data.entries.length === 0) {
            const li = document.createElement('li');
            li.style.padding = '8px 12px';
            li.style.color = 'var(--n-text-muted)';
            li.style.fontSize = '12px';
            li.textContent = '(하위 디렉토리 없음)';
            browseEntries.appendChild(li);
            return;
        }
        for (const e of data.entries) {
            const li = document.createElement('li');
            li.style.padding = '6px 12px';
            li.style.cursor = 'pointer';
            li.style.borderBottom = '1px solid var(--n-border)';
            li.textContent = '📁 ' + e.name;
            li.addEventListener('click', () => {
                const sep = browseCurrentPath.endsWith('/') ? '' : '/';
                loadDirectory(browseCurrentPath + sep + e.name);
            });
            li.addEventListener('mouseover', () => { li.style.background = '#f0f0f0'; });
            li.addEventListener('mouseout',  () => { li.style.background = ''; });
            browseEntries.appendChild(li);
        }
    }

    // ---- Submit ------------------------------------------------------

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const mode = uploadModeInput.value;
        const { fileCount, totalBytes } = collectSizeInfo(mode);
        if (fileCount === 0) {
            showError('업로드할 파일이 없습니다.');
            return;
        }

        // 폴더 모드 가드 : 파일이 모두 "단일 최상위 폴더" 로 감싸진 형태여야 함 (요구사항 케이스 2).
        // webkitdirectory 는 파일 선택 대화상자에서 폴더를 고르도록 강제하므로 정상 경로면 여기 걸리지 않는다.
        // 드래그 앤 드롭 등 일부 경우에 webkitRelativePath 가 비어있거나 여러 prefix 가 섞이면 거절.
        if (mode === 'FOLDER') {
            const err = validateFolderWrapping(Array.from(folderInput.files));
            if (err) { showError(err); return; }
        }

        const targetDirectory = form.querySelector('input[name="targetDirectory"]').value.trim();
        const version = form.querySelector('input[name="version"]').value.trim();
        const allowCreateDirectory = form.querySelector('input[name="allowCreateDirectory"]').checked;
        const entrypointOverride = (form.querySelector('input[name="entrypointRelativePath"]').value || '').trim();

        submitBtn.disabled = true;
        submitBtn.textContent = '사전 검증 중…';

        let intent;
        try {
            intent = await requestIntent({
                targetDirectory,
                uploadMode: mode,
                fileCount,
                totalBytes,
                version,
                allowCreateDirectory,
                entrypointRelativePath: entrypointOverride
            });
        } catch (err) {
            console.error(TAG, 'intent 실패', err);
            showError(err.message);
            submitBtn.disabled = false;
            submitBtn.textContent = '번들 등록';
            return;
        }

        if (intent.warnings && intent.warnings.length) {
            const msg = intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?';
            if (!confirm(msg)) {
                submitBtn.disabled = false;
                submitBtn.textContent = '번들 등록';
                return;
            }
        }

        startXhrUpload(intent.uploadToken);
    });

    function collectSizeInfo(mode) {
        if (mode === 'FOLDER') {
            const files = folderInput.files ? Array.from(folderInput.files) : [];
            const totalBytes = files.reduce((sum, f) => sum + f.size, 0);
            return { fileCount: files.length, totalBytes };
        } else if (mode === 'ZIP') {
            const f = zipInput.files && zipInput.files[0];
            return { fileCount: f ? 1 : 0, totalBytes: f ? f.size : 0 };
        } else { // SINGLE_FILE
            const f = singleInput.files && singleInput.files[0];
            return { fileCount: f ? 1 : 0, totalBytes: f ? f.size : 0 };
        }
    }

    /**
     * 요구사항 케이스 1 (개별 파일 여러 개 업로드) 을 UI·네트워크 단에서 거절.
     * webkitdirectory 가 정상 동작하면 모든 파일의 webkitRelativePath 는
     * "<wrappingFolder>/..." 공통 prefix 로 시작한다. 그렇지 않으면 거절.
     * 반환값 : 오류 메시지 or null(통과).
     */
    function validateFolderWrapping(files) {
        if (files.length === 0) return '업로드할 파일이 없습니다.';
        const prefixes = new Set();
        for (const f of files) {
            const rel = f.webkitRelativePath || '';
            if (!rel) {
                return '여러 개의 개별 파일 업로드는 거절됩니다. 관련 파일들을 하나의 폴더로 묶어 업로드하세요.';
            }
            const firstSeg = rel.split('/')[0];
            if (!firstSeg) {
                return '파일 상대경로 형식이 올바르지 않습니다.';
            }
            prefixes.add(firstSeg);
            if (prefixes.size > 1) {
                return '여러 폴더가 섞여 있습니다. 단일 폴더만 선택해 업로드하세요.';
            }
        }
        return null;
    }

    async function requestIntent(body) {
        const resp = await fetch(intentUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!resp.ok) {
            let msg = null;
            try {
                const body = await resp.json();
                if (body && body.message) msg = body.message;
            } catch (_) { /* ignore */ }
            if (!msg) msg = intentFallbackMessage(resp.status);
            throw new Error(msg);
        }
        return resp.json();
    }

    function intentFallbackMessage(status) {
        switch (status) {
            case 400: return '입력값이 올바르지 않습니다. 대상 디렉토리와 업로드 방식을 다시 확인해주세요.';
            case 404: return '대상 메인보드 모델을 찾을 수 없습니다. 목록에서 다시 선택해주세요.';
            case 409: return '사전 검증 조건에 어긋났습니다 (디렉토리 점유 · marker 충돌 · 버전 중복 등).';
            case 500: return '서버 내부 오류로 사전 검증에 실패했습니다. 잠시 후 다시 시도해주세요.';
            default:  return '사전 검증 실패 (HTTP ' + status + ')';
        }
    }

    function startXhrUpload(uploadToken) {
        // FormData 수동 구성 — `new FormData(form)` 은 각 File 의 webkitRelativePath 를 소실시킨다.
        // 폴더 모드에서는 파일별로 Blob 자체 + 3번째 인자(filename) 에 상대경로를 명시적으로 실어
        // 서버의 MultipartFile.getOriginalFilename() 이 "BiosPkg/SPI_UPD/image.bin" 처럼 받도록 한다.
        const fd = new FormData();
        const mode = uploadModeInput.value;
        fd.append('uploadMode', mode);
        fd.append('name', form.querySelector('input[name="name"]').value);
        fd.append('version', form.querySelector('input[name="version"]').value);
        fd.append('targetDirectory', form.querySelector('input[name="targetDirectory"]').value);
        fd.append('description', form.querySelector('textarea[name="description"]').value || '');
        fd.append('allowCreateDirectory',
            form.querySelector('input[name="allowCreateDirectory"]').checked ? 'true' : 'false');
        fd.append('entrypointRelativePath',
            form.querySelector('input[name="entrypointRelativePath"]').value || '');

        if (mode === 'FOLDER') {
            const files = Array.from(folderInput.files || []);
            for (const f of files) {
                // 3번째 인자(filename)에 웹킷 상대경로 주입. 서버 unwrap 은 공통 prefix 제거로 처리.
                fd.append('folderFiles', f, f.webkitRelativePath || f.name);
            }
        } else if (mode === 'ZIP') {
            const f = zipInput.files && zipInput.files[0];
            if (f) fd.append('zipFile', f, f.name);
        } else { // SINGLE_FILE
            const f = singleInput.files && singleInput.files[0];
            if (f) fd.append('singleFile', f, f.name);
        }

        resetError();
        lockPage(true);
        showProgress('시작 중…', 0);

        const xhr = new XMLHttpRequest();
        activeXhr = xhr;
        uploading = true;

        xhr.open('POST', uploadUrl);
        if (uploadToken) xhr.setRequestHeader('X-Upload-Token', uploadToken);

        const REPORT_INTERVAL_MS = 100;
        const UPLOAD_END_PCT = 90;
        const SERVER_END_PCT = 99;
        let lastUiUpdate = 0;
        let serverTimer = null;

        xhr.upload.addEventListener('progress', ev => {
            if (!ev.lengthComputable) {
                showProgress('전송 중…', null);
                return;
            }
            const now = Date.now();
            if (now - lastUiUpdate < REPORT_INTERVAL_MS) return;
            lastUiUpdate = now;
            const ratio = ev.total > 0 ? (ev.loaded / ev.total) : 0;
            const pct = Math.floor(ratio * UPLOAD_END_PCT);
            showProgress(`${pct}%  ${formatBytes(ev.loaded)} / ${formatBytes(ev.total)}`, pct);
        });

        xhr.upload.addEventListener('load', () => {
            // 서버 저장 · 트리 전개 · manifestHash · marker 기록 단계 — 시간 tween
            const tweenStart = Date.now();
            serverTimer = setInterval(() => {
                const elapsedSec = (Date.now() - tweenStart) / 1000;
                const r = 1 - Math.exp(-elapsedSec / 3);
                const pct = UPLOAD_END_PCT + (SERVER_END_PCT - UPLOAD_END_PCT) * Math.min(1, r);
                showProgress(`${Math.floor(pct)}%  서버 저장 · 전개 · marker 기록 중…`, pct);
            }, 150);
        });

        xhr.addEventListener('load', () => {
            uploading = false;
            activeXhr = null;
            if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
            if (xhr.status >= 200 && xhr.status < 300) {
                showProgress('100%  완료', 100);
                window.location.href = listUrl;
                return;
            }
            let msg = 'HTTP ' + xhr.status;
            try {
                const body = JSON.parse(xhr.responseText);
                if (body && body.message) msg = body.message;
            } catch (_) { /* ignore */ }
            showError(msg);
            hideProgress();
            lockPage(false);
        });
        xhr.addEventListener('error', () => {
            uploading = false;
            activeXhr = null;
            if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
            showError('네트워크 오류로 업로드가 중단되었습니다.');
            hideProgress();
            lockPage(false);
        });
        xhr.addEventListener('abort', () => {
            uploading = false;
            activeXhr = null;
            if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
            showError('업로드를 취소했습니다.');
            hideProgress();
            lockPage(false);
        });

        xhr.send(fd);
    }

    // ---- 페이지 잠금 (iso-new 와 동일 패턴) ---------------------------

    function lockPage(lock) {
        submitBtn.disabled = lock;
        submitBtn.textContent = lock ? '업로드 중…' : '번들 등록';
        Array.from(form.querySelectorAll('input, textarea, button[role="tab"]')).forEach(el => {
            if (el === submitBtn) return;
            el.disabled = lock;
        });
        if (backLink) setLinkDisabled(backLink, lock);
        if (cancelLink) toggleCancelControl(lock);
        lockNavbar(lock);
    }

    function setLinkDisabled(el, disabled) {
        if (disabled) {
            el.setAttribute('aria-disabled', 'true');
            el.style.pointerEvents = 'none';
            el.style.opacity = '0.5';
            el.addEventListener('click', preventClick, true);
        } else {
            el.removeAttribute('aria-disabled');
            el.style.pointerEvents = '';
            el.style.opacity = '';
            el.removeEventListener('click', preventClick, true);
        }
    }
    function preventClick(e) { e.preventDefault(); e.stopPropagation(); }

    function lockNavbar(lock) {
        document.querySelectorAll('.n-nav a.n-nav-link').forEach(a => setLinkDisabled(a, lock));
        document.querySelectorAll('.n-nav a.n-nav-brand').forEach(a => setLinkDisabled(a, lock));
    }

    function toggleCancelControl(uploadingNow) {
        if (!cancelLink || !cancelOriginal) return;
        if (uploadingNow) {
            cancelLink.textContent = '업로드 취소';
            cancelLink.setAttribute('role', 'button');
            cancelLink.removeAttribute('href');
            cancelLink.style.cursor = 'pointer';
            cancelLink.addEventListener('click', onCancelClick, true);
        } else {
            cancelLink.textContent = cancelOriginal.text;
            cancelLink.removeAttribute('role');
            if (cancelOriginal.href) cancelLink.setAttribute('href', cancelOriginal.href);
            cancelLink.style.cursor = '';
            cancelLink.removeEventListener('click', onCancelClick, true);
        }
    }
    function onCancelClick(e) {
        e.preventDefault();
        e.stopPropagation();
        if (!uploading) return;
        if (!confirm('업로드를 취소하시겠습니까? 지금까지 전송된 내용은 파기됩니다.')) return;
        if (activeXhr) activeXhr.abort();
    }

    // ---- UI 유틸 -----------------------------------------------------

    function showProgress(text, pct) {
        if (!progressBox) return;
        progressBox.classList.add('is-visible');
        if (progressText) progressText.textContent = text;
        if (progressBar && pct != null) {
            progressBar.style.width = Math.max(0, Math.min(100, pct)) + '%';
        }
    }
    function hideProgress() {
        if (progressBox) progressBox.classList.remove('is-visible');
    }
    function showError(msg) {
        if (!errorBox) { alert(msg); return; }
        errorBox.textContent = msg;
        errorBox.classList.add('is-visible');
    }
    function resetError() {
        if (!errorBox) return;
        errorBox.textContent = '';
        errorBox.classList.remove('is-visible');
    }

    function formatBytes(n) {
        if (!Number.isFinite(n) || n <= 0) return '0 B';
        if (n < 1024) return n.toFixed(0) + ' B';
        if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
        if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
        return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    }
})();
