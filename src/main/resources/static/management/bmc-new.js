/* ============================================================
   management/bmc/bmc-new.html 전용 스크립트
   ─────────────────────────────────────────────────────────────
   BIOS 업로드 흐름과 동일한 intent + XHR foreground 업로드.
   ============================================================ */
(function () {
    const TAG = '[bmc-new]';

    const form = document.getElementById('bmcForm');
    if (!form) return;
    const shell = window.BundleUploadShell;
    if (!shell) return;

    const uploadUrl = form.dataset.uploadUrl;
    const intentUrl = form.dataset.intentUrl;
    const listUrl = form.dataset.listUrl;

    const submitBtn = document.getElementById('submitBtn');
    const cancelLink = document.getElementById('cancelLink');
    const backLink = document.getElementById('backLink');

    const progressBox = document.getElementById('uploadProgress');
    const progressBar = document.getElementById('uploadBar');
    const progressText = document.getElementById('uploadText');
    const errorBox = document.getElementById('uploadError');

    const uploadModeInput = document.getElementById('uploadMode');
    const folderPane = document.getElementById('folderPane');
    const zipPane = document.getElementById('zipPane');
    const singlePane = document.getElementById('singlePane');
    const folderInput = document.getElementById('folderFiles');
    const zipInput = document.getElementById('zipFile');
    const singleInput = document.getElementById('singleFile');
    const tabFolder = document.getElementById('modeTabFolder');
    const tabZip = document.getElementById('modeTabZip');
    const tabSingle = document.getElementById('modeTabSingle');

    const targetDirInput = document.getElementById('targetDirectory');
    const browseBtn = document.getElementById('browseBtn');
    const browsePanel = document.getElementById('browsePanel');
    const browseUpBtn = document.getElementById('browseUpBtn');
    const browseCurrent = document.getElementById('browseCurrentPath');
    const browseStatus = document.getElementById('browseStatus');
    const browseEntries = document.getElementById('browseEntries');
    const browseCancelBtn = document.getElementById('browseCancelBtn');
    const browseApplyBtn = document.getElementById('browseApplyBtn');
    const browseUrl = targetDirInput ? targetDirInput.dataset.browseUrl : null;

    let activeXhr = null;
    let uploading = false;

    window.addEventListener('beforeunload', e => {
        if (!uploading) return;
        e.preventDefault();
        e.returnValue = '';
    });

    const bootstrap = window.BundleUploadBootstrap;
    if (bootstrap) {
        bootstrap.bindModeTabs({
            uploadModeInput, folderPane, zipPane, singlePane,
            folderInput, zipInput, singleInput,
            tabFolder, tabZip, tabSingle
        });
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

    form.addEventListener('submit', async e => {
        e.preventDefault();
        // S4 — submit 시작 시 폼 에러 초기화.
        if (window.FormError && typeof window.FormError.clear === 'function') {
            window.FormError.clear(form);
        }

        const mode = uploadModeInput.value;
        const { fileCount, totalBytes } = shell.collectSizeInfo(mode, {
            folderInput, zipInput, singleInput
        });
        if (fileCount === 0) {
            showError('업로드할 파일이 없습니다.');
            return;
        }
        if (mode === 'FOLDER') {
            const err = shell.validateFolderWrapping(Array.from(folderInput.files));
            if (err) {
                showError(err);
                return;
            }
        }

        const commonFields = shell.resolveCommonFields(form);

        submitBtn.disabled = true;
        submitBtn.textContent = '사전 검증 중…';

        try {
            const intent = await shell.requestIntent({
                intentUrl,
                body: {
                targetDirectory: commonFields.targetDirectory.trim(),
                uploadMode: mode,
                fileCount,
                totalBytes,
                version: commonFields.version.trim(),
                allowCreateDirectory: commonFields.allowCreateDirectory,
                entrypointRelativePath: commonFields.entrypointRelativePath.trim()
                }
            });
            // MK2 단계 A — preExistingMatch 안내 (1차 dismiss). 사용자 진행 의사 확인.
            if (intent.preExistingMatch) {
                const m = intent.preExistingMatch;
                const stateLabel = m.state === 'SOFT_DELETED' ? '휴지통' : (m.state === 'DEPRECATED' ? 'Deprecated' : '활성');
                const msg = `같은 메인보드에 같은 버전의 BMC 자원이 이미 존재합니다.\n\n`
                    + `  · id : ${m.id}\n`
                    + `  · 이름 : ${m.name}\n`
                    + `  · 버전 : ${m.version}\n`
                    + `  · 상태 : ${stateLabel}\n\n`
                    + `그래도 업로드를 진행하시겠습니까?`;
                if (!confirm(msg)) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = '번들 등록';
                    return;
                }
            }
            if (intent.warnings && intent.warnings.length) {
                if (!confirm(intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?')) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = '번들 등록';
                    return;
                }
            }
            startXhrUpload(intent.uploadToken, commonFields);
        } catch (err) {
            // S4 — body.fieldErrors 매핑.
            if (window.FormError && err.body) {
                window.FormError.renderResponse(err.body, { root: form });
            } else {
                showError(err.message);
            }
            submitBtn.disabled = false;
            submitBtn.textContent = '번들 등록';
        }
    });
    function startXhrUpload(uploadToken, commonFields) {
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
        const progressTracker = shell.createUploadProgressTracker();
        activeXhr = shell.startXhrUpload({
            uploadUrl,
            uploadToken,
            formData: fd,
            onStart() {
                uploading = true;
                // CH3 : 업로드 진행 중 폴링 실패 누적 시 reload 보류 신호
                document.dispatchEvent(new CustomEvent('bgjob:uploadStart'));
                lockPage(true);
                showProgress('시작 중…', 0);
            },
            onUploadProgress(evt) {
                const progress = shell.describeUploadProgress(evt, {
                    tracker: progressTracker
                });
                if (!progress.shouldRender) return;
                showProgress(`${progress.percent}%  ${progress.message}`, progress.percent);
            },
            onSuccess(body) {
                activeXhr = null;
                uploading = false;
                document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
                lockPage(false);
                showProgress('완료. 이동 중…', 100);
                window.location.href = body.redirect || listUrl;
            },
            onHttpError(msg, xhr, body) {
                activeXhr = null;
                uploading = false;
                document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
                lockPage(false);
                submitBtn.textContent = '번들 등록';
                if (window.FormError && body) {
                    window.FormError.renderResponse(body, { root: form });
                } else {
                    showError(msg);
                }
            },
            onNetworkError() {
                activeXhr = null;
                uploading = false;
                document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
                lockPage(false);
                submitBtn.textContent = '번들 등록';
                showError('네트워크 오류로 업로드에 실패했습니다.');
            }
        });
    }

    function lockPage(locked) {
        submitBtn.disabled = locked;
        if (cancelLink) cancelLink.style.pointerEvents = locked ? 'none' : '';
        if (backLink) backLink.style.pointerEvents = locked ? 'none' : '';
        progressBox.style.display = locked ? 'block' : '';
    }

    function showProgress(text, percent) {
        progressText.textContent = text;
        progressBar.style.width = percent + '%';
    }

    function showError(message) {
        errorBox.textContent = message;
        errorBox.style.display = 'block';
    }

    function resetError() {
        errorBox.textContent = '';
        errorBox.style.display = '';
    }
})();
