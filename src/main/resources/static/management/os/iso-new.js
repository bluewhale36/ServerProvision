/* ============================================================
   management/os/iso-new.html 전용 스크립트
   ─────────────────────────────────────────────────────────────
   ISO 생성 정책 :
     · 파일 있음  → foreground XHR 로 바이트를 먼저 올린다.
     · 파일 없음 → 기존 경로만 서버에 넘긴다.
     · 두 케이스 모두 해시 계산 + marker 발급 + DB 등록은 background job 으로 후행 처리한다.
       화면은 job 시작만 확인한 뒤 목록 페이지로 이동하고, 완료/실패는 알림 센터가 담당한다.
   ============================================================ */
(function () {
    const TAG = '[iso-new]';
    console.log(TAG, 'script loaded');

    const form = document.querySelector('form[data-upload-url]');
    if (!form) return;

    const uploadUrl = form.dataset.uploadUrl;
    const listUrl   = form.dataset.listUrl;
    const submitBtn = document.getElementById('submitBtn');
    const cancelLink = document.getElementById('cancelLink');
    const backLink = document.getElementById('backLink');

    const progressBox  = document.getElementById('uploadProgress');
    const progressBar  = document.getElementById('uploadBar');
    const progressText = document.getElementById('uploadText');
    const errorBox     = document.getElementById('uploadError');

    if (!uploadUrl || !listUrl || !submitBtn) return;

    let activeXhr = null;
    let uploading = false;

    // ---- 서버 경로 탐색 (BIOS 의 bios-new.js 의 browse UI 와 동일 패턴) ------
    const isoPathInput        = document.getElementById('isoPath');
    const browseBtn           = document.getElementById('browseBtn');
    const browsePanel         = document.getElementById('browsePanel');
    const browseUpBtn         = document.getElementById('browseUpBtn');
    const browseCurrent       = document.getElementById('browseCurrentPath');
    const browseStatus        = document.getElementById('browseStatus');
    const browseEntries       = document.getElementById('browseEntries');
    const browseCancelBtn     = document.getElementById('browseCancelBtn');
    const browseApplyDirBtn   = document.getElementById('browseApplyDirBtn');
    const browseUrl           = isoPathInput ? isoPathInput.dataset.browseUrl : null;
    let browseCurrentPath = '/';

    function openBrowsePanel() {
        if (!browsePanel) return;
        if (browsePanel.hidden) {
            const seed = (isoPathInput.value || '').trim();
            // 경로 입력이 파일 경로(.iso)면 그 부모 디렉토리부터, 디렉토리면 그대로, 비어있으면 / 부터.
            let initial = '/';
            if (seed) {
                if (seed.endsWith('/')) initial = seed;
                else {
                    const idx = seed.lastIndexOf('/');
                    initial = idx >= 0 ? (seed.substring(0, idx) || '/') : '/';
                }
            }
            browsePanel.hidden = false;
            loadDirectory(initial);
        } else {
            browsePanel.hidden = true;
        }
    }

    if (browseBtn && browseUrl) {
        browseBtn.addEventListener('click', openBrowsePanel);
    }
    if (browseCancelBtn) {
        browseCancelBtn.addEventListener('click', () => { browsePanel.hidden = true; });
    }
    if (browseUpBtn) {
        browseUpBtn.addEventListener('click', () => {
            if (!browseCurrentPath || browseCurrentPath === '/') return;
            const p = browseCurrentPath;
            const trimmed = p.endsWith('/') ? p.slice(0, -1) : p;
            const idx = trimmed.lastIndexOf('/');
            loadDirectory(idx <= 0 ? '/' : trimmed.substring(0, idx));
        });
    }
    if (browseApplyDirBtn) {
        browseApplyDirBtn.addEventListener('click', () => {
            // "이 디렉토리에 저장" — 디렉토리 + '/' 로 끝나게 하면 서버가 multipart 파일명을 자동 append.
            if (!browseCurrentPath) return;
            const applied = browseCurrentPath.endsWith('/') ? browseCurrentPath : browseCurrentPath + '/';
            isoPathInput.value = applied;
            browsePanel.hidden = true;
        });
    }

    async function loadDirectory(pathStr) {
        browseStatus.textContent = '로딩…';
        browseEntries.innerHTML = '';
        try {
            // includeFiles=true 로 ISO 파일까지 받아옴 — 경로만 등록 케이스 지원.
            const url = browseUrl + '?includeFiles=true&path=' + encodeURIComponent(pathStr);
            const resp = await fetch(url, { headers: { 'Accept': 'application/json' } });
            if (!resp.ok) {
                const body = await resp.json().catch(() => ({}));
                browseStatus.textContent = '오류 : ' + (body.message || ('HTTP ' + resp.status));
                return;
            }
            const data = await resp.json();
            browseCurrentPath = data.path;
            browseCurrent.textContent = data.path;
            renderBrowseEntries(data);
            const dirCount = data.entries.filter(e => e.type === 'DIR').length;
            const fileCount = data.entries.length - dirCount;
            browseStatus.textContent = `${dirCount} 개 디렉토리, ${fileCount} 개 파일`;
        } catch (err) {
            browseStatus.textContent = '요청 실패 : ' + err.message;
        }
    }

    function renderBrowseEntries(data) {
        browseEntries.innerHTML = '';
        if (!data.entries || data.entries.length === 0) {
            const li = document.createElement('li');
            li.style.padding = '8px 12px';
            li.style.color = 'var(--n-text-muted)';
            li.style.fontSize = '12px';
            li.textContent = '(비어있음)';
            browseEntries.appendChild(li);
            return;
        }
        for (const e of data.entries) {
            const li = document.createElement('li');
            li.style.padding = '6px 12px';
            li.style.cursor = 'pointer';
            li.style.borderBottom = '1px solid var(--n-border)';
            li.style.fontSize = '13px';
            const isDir = e.type === 'DIR';
            const isIso = !isDir && e.name.toLowerCase().endsWith('.iso');
            const icon = isDir ? '📁' : (isIso ? '💿' : '📄');
            const sizeText = !isDir && typeof e.size === 'number' && e.size >= 0 ? '  · ' + formatBytes(e.size) : '';
            li.innerHTML = `${icon} ${escapeHtml(e.name)}<span style="color: var(--n-text-muted); font-size: 11px;">${escapeHtml(sizeText)}</span>`;
            if (isIso) li.style.background = '#f2f9ff';
            li.addEventListener('click', () => {
                if (isDir) {
                    const sep = browseCurrentPath.endsWith('/') ? '' : '/';
                    loadDirectory(browseCurrentPath + sep + e.name);
                } else {
                    // 파일 클릭 → 절대 경로로 isoPath 입력에 자동 채움 + 패널 닫음 (경로만 등록 흐름).
                    const sep = browseCurrentPath.endsWith('/') ? '' : '/';
                    isoPathInput.value = browseCurrentPath + sep + e.name;
                    browsePanel.hidden = true;
                }
            });
            li.addEventListener('mouseover', () => { li.style.background = isIso ? '#e1f1fe' : '#f0f0f0'; });
            li.addEventListener('mouseout',  () => { li.style.background = isIso ? '#f2f9ff' : ''; });
            browseEntries.appendChild(li);
        }
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    }

    // 원래 상태 복원용 스냅샷
    const cancelOriginal = cancelLink ? {
        tag: cancelLink.tagName,
        text: cancelLink.textContent,
        href: cancelLink.getAttribute('href')
    } : null;

    // 업로드 도중 탭 닫기 / 새로고침 경고
    window.addEventListener('beforeunload', e => {
        if (!uploading) return;
        e.preventDefault();
        e.returnValue = '';
    });

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const fileInput = form.querySelector('input[type="file"]');
        const file = fileInput && fileInput.files[0] ? fileInput.files[0] : null;

        if (!file) {
            console.log(TAG, '파일 없음 — 경로 검증 후 background 등록 시작');
            startPathOnlyRegistration();
            return;
        }
        console.log(TAG, '파일 있음 — intent 핸드셰이크 후 XHR foreground 업로드', {
            name: file.name, size: file.size
        });

        // 1) Intent 핸드셰이크 — 사전 검증 + 토큰 발급
        const isoPathInput = form.querySelector('input[name="isoPath"]');
        const allowCreateDirInput = form.querySelector('input[name="allowCreateDirectory"]');
        const isoPath = isoPathInput ? isoPathInput.value.trim() : '';
        const allowCreateDirectory = !!(allowCreateDirInput && allowCreateDirInput.checked);
        submitBtn.disabled = true;
        submitBtn.textContent = '사전 검증 중…';

        let intent;
        try {
            intent = await requestIntent(isoPath, file, allowCreateDirectory);
        } catch (err) {
            console.error(TAG, 'intent 실패', err);
            showError(err.message);
            submitBtn.disabled = false;
            submitBtn.textContent = '등록';
            return;
        }

        // 서버가 반환한 경고는 confirm 으로 사용자 의사 확인
        if (intent.warnings && intent.warnings.length) {
            const msg = intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?';
            if (!confirm(msg)) {
                submitBtn.disabled = false;
                submitBtn.textContent = '등록';
                return;
            }
        }

        // 2) 실제 XHR 업로드
        startXhrUpload(file, intent.uploadToken);
    });

    async function requestIntent(isoPath, file, allowCreateDirectory) {
        // uploadUrl = /management/os/{osId}/iso/upload → 같은 경로에서 /upload-intent 로 파생
        const intentUrl = uploadUrl.replace(/\/upload$/, '/upload-intent');
        const resp = await fetch(intentUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({
                isoPath,
                filename: file.name,
                size: file.size,
                allowCreateDirectory
            })
        });
        if (!resp.ok) {
            // 서버 JSON 의 message 를 최우선으로 사용한다.
            // JSON 파싱 실패 등으로 body 메시지를 못 얻은 경우를 대비해 status 별로 의미 있는 fallback.
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
            case 400: return '입력값이 올바르지 않습니다. ISO 경로와 파일을 다시 확인해주세요.';
            case 404: return '대상 OS 이미지를 찾을 수 없습니다. 목록에서 다시 선택해주세요.';
            case 409: return '같은 경로에 이미 등록된 ISO 가 있거나 사전 검증 조건에 어긋났습니다.';
            case 500: return '서버 내부 오류로 사전 검증에 실패했습니다. 잠시 후 다시 시도해주세요.';
            default:  return '사전 검증 실패 (HTTP ' + status + ')';
        }
    }

    function startPathOnlyRegistration() {
        const fd = new FormData(form);

        resetError();
        lockPage(true);
        showProgress('기존 ISO 경로 확인 후 등록 작업 시작 중…', null);

        const xhr = new XMLHttpRequest();
        activeXhr = xhr;
        uploading = true;
        xhr.open('POST', uploadUrl);

        xhr.addEventListener('load', () => handleUploadResponse(xhr, false));
        xhr.addEventListener('error', () => handleUploadNetworkError('네트워크 오류로 등록 요청이 중단되었습니다.'));
        xhr.addEventListener('abort', () => handleUploadNetworkError('등록 요청을 취소했습니다.'));
        xhr.send(fd);
    }

    function startXhrUpload(file, uploadToken) {
        // intent 에서 받은 토큰은 이 함수에서 단 한 번 X-Upload-Token 으로 전송된다.

        const fd = new FormData(form);

        resetError();
        lockPage(true);
        showProgress('시작 중…', 0);

        const xhr = new XMLHttpRequest();
        activeXhr = xhr;
        uploading = true;

        xhr.open('POST', uploadUrl);
        if (uploadToken) xhr.setRequestHeader('X-Upload-Token', uploadToken);

        const REPORT_INTERVAL_MS = 100; // 페이지 내 바 갱신 간격
        const SPEED_WINDOW_MS = 2000;
        const speedSamples = [];
        let lastUiUpdate = 0;

        // 진행도 단계 비율. 실제 네트워크 전송은 0~UPLOAD_END, 그 뒤 서버가 background job 을
        // 등록하는 구간을 짧은 tween 으로 보여준다. 마지막 응답 수신 시 100%.
        const UPLOAD_END_PCT = 90;
        const CHECKSUM_END_PCT = 99;
        const CHECKSUM_ESTIMATED_MB_PER_SEC = 300;
        let checksumTimer = null;

        xhr.upload.addEventListener('progress', ev => {
            if (!ev.lengthComputable) {
                showProgress('전송 중…', null);
                return;
            }
            const now = Date.now();
            if (now - lastUiUpdate < REPORT_INTERVAL_MS) return;
            lastUiUpdate = now;

            const ratio = ev.total > 0 ? (ev.loaded / ev.total) : 0;
            const displayPct = Math.floor(ratio * UPLOAD_END_PCT); // 0~90%
            speedSamples.push({ t: now, loaded: ev.loaded });
            while (speedSamples.length > 1 && now - speedSamples[0].t > SPEED_WINDOW_MS) {
                speedSamples.shift();
            }
            let speedBps = 0;
            if (speedSamples.length >= 2) {
                const first = speedSamples[0];
                const last  = speedSamples[speedSamples.length - 1];
                const dtSec = (last.t - first.t) / 1000;
                if (dtSec > 0) speedBps = (last.loaded - first.loaded) / dtSec;
            }
            const etaSec = speedBps > 0 ? Math.round((ev.total - ev.loaded) / speedBps) : -1;

            const msg = `${formatBytes(ev.loaded)} / ${formatBytes(ev.total)}` +
                (speedBps > 0 ? ` · ${formatBytes(speedBps)}/s` : '') +
                (etaSec >= 0 ? ` · 약 ${formatDuration(etaSec)} 남음` : '');
            showProgress(`${displayPct}%  ${msg}`, displayPct);
        });

        xhr.upload.addEventListener('load', () => {
            // 업로드 바이트 전송 완료 — 이후 해시 계산/등록은 background job 이 담당한다.
            const fileSizeMB = (file && file.size ? file.size : 0) / (1024 * 1024);
            const estimatedSec = Math.max(1.0, fileSizeMB / CHECKSUM_ESTIMATED_MB_PER_SEC);
            const tweenStart = Date.now();
            checksumTimer = setInterval(() => {
                const elapsed = (Date.now() - tweenStart) / 1000;
                const ratio = 1 - Math.exp(-elapsed / (estimatedSec * 0.6));
                const pct = UPLOAD_END_PCT + (CHECKSUM_END_PCT - UPLOAD_END_PCT) * Math.min(1, ratio);
                showProgress(
                    `${Math.floor(pct)}%  서버 수신 완료 · 등록 작업 시작 중…`,
                    pct
                );
            }, 150);
        });

        xhr.addEventListener('load', () => handleUploadResponse(xhr, true, checksumTimer));
        xhr.addEventListener('error', () => handleUploadNetworkError('네트워크 오류로 업로드가 중단되었습니다.', checksumTimer));
        xhr.addEventListener('abort', () => handleUploadNetworkError('업로드를 취소했습니다.', checksumTimer));

        xhr.send(fd);
    }

    function handleUploadResponse(xhr, hadFile, checksumTimer) {
        uploading = false;
        activeXhr = null;
        if (checksumTimer) { clearInterval(checksumTimer); }
        if (xhr.status >= 200 && xhr.status < 300) {
            let body = {};
            try { body = JSON.parse(xhr.responseText || '{}'); } catch (_) { /* ignore */ }
            const redirect = body.redirect || listUrl;
            const msg = hadFile
                ? 'ISO 업로드가 끝났고 등록 후처리가 background 에서 계속됩니다.'
                : 'ISO 등록 후처리가 background 에서 시작되었습니다.';
            try {
                sessionStorage.setItem('os.isoRegistration.toast', msg);
            } catch (_) { /* ignore */ }
            showProgress('100%  등록 작업 시작됨', 100);
            window.location.href = redirect;
            return;
        }
        let msg = 'HTTP ' + xhr.status;
        try {
            const body = JSON.parse(xhr.responseText);
            if (body && body.message) msg = body.message;
        } catch (_) { /* ignore */ }
        console.error(TAG, 'upload 실패', msg);
        showError(msg);
        hideProgress();
        lockPage(false);
    }

    function handleUploadNetworkError(message, checksumTimer) {
        uploading = false;
        activeXhr = null;
        if (checksumTimer) { clearInterval(checksumTimer); }
        showError(message);
        hideProgress();
        lockPage(false);
    }

    // ---- 페이지 잠금 / 해제 --------------------------------------

    function lockPage(lock) {
        // 폼 요소
        submitBtn.disabled = lock;
        submitBtn.textContent = lock ? '업로드 중…' : '등록';
        Array.from(form.querySelectorAll('input, textarea')).forEach(el => {
            el.disabled = lock;
        });

        // "← 목록으로" 링크
        if (backLink) setLinkDisabled(backLink, lock);

        // 취소 링크 ↔ 업로드 취소 버튼 토글
        if (cancelLink) toggleCancelControl(lock);

        // navbar 링크 — 서류가방 트리거 제외, 모든 .n-nav-link 앵커 비활성화
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
        // Brand 링크도 막는다 (실수 클릭 방지)
        document.querySelectorAll('.n-nav a.n-nav-brand').forEach(a => setLinkDisabled(a, lock));
    }

    function toggleCancelControl(uploadingNow) {
        if (!cancelLink || !cancelOriginal) return;
        if (uploadingNow) {
            // 링크를 "업로드 취소" 버튼으로 전환
            cancelLink.textContent = '업로드 취소';
            cancelLink.setAttribute('role', 'button');
            cancelLink.removeAttribute('href');
            cancelLink.style.cursor = 'pointer';
            cancelLink.addEventListener('click', onCancelClick, true);
        } else {
            // 원상 복구
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

    // ---- UI 유틸 ------------------------------------------------

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
    function formatDuration(sec) {
        if (sec < 60) return sec + '초';
        const m = Math.floor(sec / 60);
        const s = sec % 60;
        if (m < 60) return m + '분 ' + s + '초';
        const h = Math.floor(m / 60);
        return h + '시간 ' + (m % 60) + '분';
    }
})();
