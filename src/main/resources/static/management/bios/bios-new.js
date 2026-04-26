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
    const shell = window.BundleUploadShell;
    if (!shell) return;

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

    const bootstrap = window.BundleUploadBootstrap;
    if (bootstrap) {
        bootstrap.bindModeTabs({
            uploadModeInput, folderPane, zipPane, singlePane,
            folderInput, zipInput, singleInput,
            tabFolder, tabZip, tabSingle
        });
    }

    // ---- 경로 브라우저 -----------------------------------------------

    if (bootstrap) {
        bootstrap.bindDirectoryBrowse({
            targetInput: targetDirInput,
            browseBtn,
            browsePanel,
            browseUpBtn,
            browseCurrent,
            browseStatus,
            browseEntries,
            browseCancelBtn,
            browseApplyBtn,
            browseUrl,
            includeFiles: false
        });
    }

    // ---- Submit ------------------------------------------------------

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const mode = uploadModeInput.value;
        const { fileCount, totalBytes } = shell.collectSizeInfo(mode, {
            folderInput, zipInput, singleInput
        });
        if (fileCount === 0) {
            showError('업로드할 파일이 없습니다.');
            return;
        }

        // 폴더 모드 가드 : 파일이 모두 "단일 최상위 폴더" 로 감싸진 형태여야 함 (요구사항 케이스 2).
        // webkitdirectory 는 파일 선택 대화상자에서 폴더를 고르도록 강제하므로 정상 경로면 여기 걸리지 않는다.
        // 드래그 앤 드롭 등 일부 경우에 webkitRelativePath 가 비어있거나 여러 prefix 가 섞이면 거절.
        if (mode === 'FOLDER') {
            const err = shell.validateFolderWrapping(Array.from(folderInput.files));
            if (err) { showError(err); return; }
        }

        const commonFields = shell.resolveCommonFields(form);

        submitBtn.disabled = true;
        submitBtn.textContent = '사전 검증 중…';

        let intent;
        try {
            intent = await shell.requestIntent({
                intentUrl,
                body: {
                targetDirectory: commonFields.targetDirectory.trim(),
                uploadMode: mode,
                fileCount,
                totalBytes,
                version: commonFields.version.trim(),
                allowCreateDirectory: commonFields.allowCreateDirectory,
                entrypointRelativePath: commonFields.entrypointRelativePath.trim()
                },
                intentFallbackMessage
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

        startXhrUpload(intent.uploadToken, commonFields);
    });

    function intentFallbackMessage(status) {
        switch (status) {
            case 400: return '입력값이 올바르지 않습니다. 대상 디렉토리와 업로드 방식을 다시 확인해주세요.';
            case 404: return '대상 메인보드 모델을 찾을 수 없습니다. 목록에서 다시 선택해주세요.';
            case 409: return '사전 검증 조건에 어긋났습니다 (디렉토리 점유 · marker 충돌 · 버전 중복 등).';
            case 500: return '서버 내부 오류로 사전 검증에 실패했습니다. 잠시 후 다시 시도해주세요.';
            default:  return '사전 검증 실패 (HTTP ' + status + ')';
        }
    }

    function startXhrUpload(uploadToken, commonFields) {
        // FormData 수동 구성 — `new FormData(form)` 은 각 File 의 webkitRelativePath 를 소실시킨다.
        // 폴더 모드에서는 파일별로 Blob 자체 + 3번째 인자(filename) 에 상대경로를 명시적으로 실어
        // 서버의 MultipartFile.getOriginalFilename() 이 "BiosPkg/SPI_UPD/image.bin" 처럼 받도록 한다.
        const { formData: fd } = shell.buildBundleFormData({
            form,
            uploadModeInput,
            folderInput,
            zipInput,
            singleInput,
            fixedFields: {
                name: commonFields.name,
                version: commonFields.version,
                targetDirectory: commonFields.targetDirectory,
                description: commonFields.description,
                allowCreateDirectory: commonFields.allowCreateDirectory ? 'true' : 'false',
                entrypointRelativePath: commonFields.entrypointRelativePath
            }
        });

        resetError();
        const REPORT_INTERVAL_MS = 100;
        const UPLOAD_END_PCT = 90;
        const SERVER_END_PCT = 99;
        const progressTracker = shell.createUploadProgressTracker();
        let serverTimer = null;
        activeXhr = shell.startXhrUpload({
            uploadUrl,
            uploadToken,
            formData: fd,
            onStart() {
                uploading = true;
                lockPage(true);
                showProgress('시작 중…', 0);
            },
            onUploadProgress(ev) {
                const ratio = ev.total > 0 ? (ev.loaded / ev.total) : 0;
                const pct = Math.floor(ratio * UPLOAD_END_PCT);
                const progress = shell.describeUploadProgress(ev, {
                    tracker: progressTracker,
                    reportIntervalMs: REPORT_INTERVAL_MS,
                    displayPercent: pct
                });
                if (!progress.shouldRender) return;
                showProgress(`${progress.percent}%  ${progress.message}`, progress.percent);
            },
            onUploadLoad() {
                const tweenStart = Date.now();
                serverTimer = setInterval(() => {
                    const elapsedSec = (Date.now() - tweenStart) / 1000;
                    const r = 1 - Math.exp(-elapsedSec / 3);
                    const pct = UPLOAD_END_PCT + (SERVER_END_PCT - UPLOAD_END_PCT) * Math.min(1, r);
                    showProgress(`${Math.floor(pct)}%  서버 저장 · 전개 · marker 기록 중…`, pct);
                }, 150);
            },
            onSuccess() {
                uploading = false;
                activeXhr = null;
                if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
                showProgress('100%  완료', 100);
                window.location.href = listUrl;
            },
            onHttpError(msg) {
                uploading = false;
                activeXhr = null;
                if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
                showError(msg);
                hideProgress();
                lockPage(false);
            },
            onNetworkError() {
                uploading = false;
                activeXhr = null;
                if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
                showError('네트워크 오류로 업로드가 중단되었습니다.');
                hideProgress();
                lockPage(false);
            },
            onAbort() {
                uploading = false;
                activeXhr = null;
                if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
                showError('업로드를 취소했습니다.');
                hideProgress();
                lockPage(false);
            }
        });
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

})();
