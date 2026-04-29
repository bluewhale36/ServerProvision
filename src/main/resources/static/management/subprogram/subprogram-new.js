/* ============================================================
   management/subprogram/subprogram-new.html 전용 스크립트
   ─────────────────────────────────────────────────────────────
   BMC / BIOS 와 동일한 intent + XHR foreground 업로드 패턴.
   추가 책임 : boardScope 라디오 (공용 / 보드별) 토글 + select 동기화 +
   동적 URL 조립 ({base}/{kind}/{boardScope}/upload-intent · upload).
   ============================================================ */
(function () {
    const TAG = '[subprogram-new]';

    const form = document.getElementById('subprogramForm');
    if (!form) return;
    const shell = window.BundleUploadShell;
    if (!shell) return;

    const baseUrl = form.dataset.baseUrl;
    const kindToken = form.dataset.kindToken;
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

    const boardSelect = document.getElementById('boardId');
    const scopeRadios = form.querySelectorAll('input[name="boardScopeMode"]');

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

    /* ───── boardScope 라디오 ↔ select disabled 동기화 ───── */
    function applyScopeMode() {
        const selected = form.querySelector('input[name="boardScopeMode"]:checked');
        const mode = selected ? selected.value : 'common';
        if (boardSelect) {
            if (mode === 'board') {
                boardSelect.disabled = false;
            } else {
                boardSelect.disabled = true;
                boardSelect.value = '';
            }
        }
    }
    scopeRadios.forEach(r => r.addEventListener('change', applyScopeMode));
    applyScopeMode();

    /* ───── boardScope token 결정 ───── */
    function resolveScopeToken() {
        const mode = (form.querySelector('input[name="boardScopeMode"]:checked') || {}).value || 'common';
        if (mode === 'common') return 'common';
        const v = boardSelect ? boardSelect.value : '';
        if (!v) {
            return null;
        }
        return v;
    }

    /* ───── 폼 제출 ───── */
    form.addEventListener('submit', async e => {
        e.preventDefault();
        // S4 — submit 시작 시 폼 에러 초기화.
        if (window.FormError && typeof window.FormError.clear === 'function') {
            window.FormError.clear(form);
        }

        const scopeToken = resolveScopeToken();
        if (!scopeToken) {
            showError('적용할 메인보드를 선택하거나 공용을 선택하세요.');
            return;
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

        const intentUrl = `${baseUrl}/${kindToken}/${scopeToken}/upload-intent`;
        const uploadUrl = `${baseUrl}/${kindToken}/${scopeToken}/upload`;

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
                    allowCreateDirectory: commonFields.allowCreateDirectory
                }
            });
            if (intent.warnings && intent.warnings.length) {
                if (!confirm(intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?')) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = '번들 등록';
                    return;
                }
            }
            startXhrUpload(uploadUrl, intent.uploadToken, commonFields);
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

    function startXhrUpload(uploadUrl, uploadToken, commonFields) {
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
                allowCreateDirectory: commonFields.allowCreateDirectory ? 'true' : 'false'
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
                const progress = shell.describeUploadProgress(evt, { tracker: progressTracker });
                if (!progress.shouldRender) return;
                // 네트워크 전송이 100% 에 도달한 뒤에도 서버는 압축 해제 / manifest 계산 / 마커 발급을
                // 동기 처리한다. 사용자가 "멈춘 것 같다" 고 오해하지 않도록 안내 문구로 전환한다.
                if (progress.percent >= 100) {
                    showProgress('서버에서 압축 해제 · 해시 계산 · 마커 발급 중… (잠시만 기다려주세요)', 100);
                } else {
                    showProgress(`${progress.percent}%  ${progress.message}`, progress.percent);
                }
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
