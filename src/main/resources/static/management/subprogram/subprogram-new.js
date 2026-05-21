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
    const existingPane = document.getElementById('existingPane');
    const folderInput = document.getElementById('folderFiles');
    const zipInput = document.getElementById('zipFile');
    const singleInput = document.getElementById('singleFile');
    const tabFolder = document.getElementById('modeTabFolder');
    const tabZip = document.getElementById('modeTabZip');
    const tabSingle = document.getElementById('modeTabSingle');
    const tabExisting = document.getElementById('modeTabExisting');

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
            uploadModeInput, folderPane, zipPane, singlePane, existingPane,
            folderInput, zipInput, singleInput,
            tabFolder, tabZip, tabSingle, tabExisting,
            onModeChange(mode) {
                if (submitBtn) submitBtn.textContent = mode === 'EXISTING_DIRECTORY' ? '기존 디렉토리 등록' : '번들 등록';
            }
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

        if (mode === 'EXISTING_DIRECTORY') {
            await submitRegisterExisting(scopeToken);
            return;
        }

        const {fileCount, totalBytes} = shell.collectSizeInfo(mode, {
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
            // MK2 WAVE 2 — intent 메타 nudge (단계 A) 분기.
            if (err.body && err.body.code === 'NUDGE_REQUIRED' && err.body.nudgeId) {
                openIntentNudgeModal(err.body, uploadUrl, commonFields);
                submitBtn.disabled = false;
                submitBtn.textContent = '번들 등록';
                return;
            }
            // S4 — body.fieldErrors 매핑.
            if (window.FormError && err.body) {
                window.FormError.renderResponse(err.body, {root: form});
            } else {
                showError(err.message);
            }
            submitBtn.disabled = false;
            submitBtn.textContent = '번들 등록';
        }
    });

    async function submitRegisterExisting(scopeToken) {
        const fields = shell.resolveCommonFields(form);
        const url = `${baseUrl}/${kindToken}/${scopeToken}/register-existing`;
        submitBtn.disabled = true;
        const originalLabel = submitBtn.textContent;
        submitBtn.textContent = '등록 중…';
        try {
            const resp = await fetch(url, {
                method: 'POST',
                headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                body: JSON.stringify({
                    name: fields.name.trim(),
                    version: fields.version.trim(),
                    targetDirectory: fields.targetDirectory.trim(),
                    description: fields.description
                })
            });
            const body = await resp.json().catch(() => ({}));
            if (!resp.ok) {
                if (body && body.code === 'NUDGE_REQUIRED' && body.nudgeId) {
                    openContentNudgeModal(body);
                    return;
                }
                if (window.FormError && body) {
                    window.FormError.renderResponse(body, {root: form});
                } else {
                    showError(body.message || ('등록 실패 (HTTP ' + resp.status + ')'));
                }
                return;
            }
            window.location.href = body.redirect || listUrl;
        } catch (err) {
            showError('네트워크 오류 : ' + err.message);
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = originalLabel;
        }
    }

    function startXhrUpload(uploadUrl, uploadToken, commonFields) {
        const {formData: fd} = shell.buildBundleFormData({
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
                const progress = shell.describeUploadProgress(evt, {tracker: progressTracker});
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
                // MK2 — 단계 B 해시 충돌 nudge.
                if (body && body.code === 'NUDGE_REQUIRED' && body.nudgeId) {
                    openContentNudgeModal(body);
                    return;
                }
                if (window.FormError && body) {
                    window.FormError.renderResponse(body, {root: form});
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

    // ---- MK2 nudge modal -----------------------------------------------
    //  modal element id prefix = 'subprogramNudge' (subprogram-new.html 의 fragment include 와 일치).

    function bindModalCommon(body, baseUrl) {
        const modal = document.getElementById('subprogramNudgeModal');
        const conflicts = document.getElementById('subprogramNudgeConflictsList');
        const proceedBtn = document.getElementById('subprogramNudgeProceedBtn');
        const replaceBtn = document.getElementById('subprogramNudgeReplaceBtn');
        const cancelBtn = document.getElementById('subprogramNudgeCancelBtn');
        if (!modal || !conflicts || !proceedBtn || !replaceBtn || !cancelBtn) {
            showError('nudge modal 요소를 찾을 수 없습니다. 페이지를 새로고침 해주세요.');
            return null;
        }
        const nudgeId = body.nudgeId;
        let selectedTargetId = null;

        conflicts.innerHTML = '';
        (body.conflicts || []).forEach(entry => {
            const li = document.createElement('li');
            li.style.padding = '8px 12px';
            li.style.borderBottom = '1px solid var(--n-border, #e0e0e0)';
            li.innerHTML =
                '<label style="display:flex; gap:8px; align-items:center; cursor:pointer;">' +
                '  <input type="radio" name="subprogramNudgeTarget" value="' + entry.id + '">' +
                '  <span><strong>' + escapeHtml(entry.name) + '</strong> · v' + escapeHtml(entry.version) +
                '    <span style="color: var(--n-text-muted, #777); font-size: 11px;">[' + entry.state + ' · #' + entry.id + ']</span></span>' +
                '</label>';
            conflicts.appendChild(li);
        });
        replaceBtn.disabled = true;
        conflicts.querySelectorAll('input[name="subprogramNudgeTarget"]').forEach(input => {
            input.addEventListener('change', () => {
                selectedTargetId = input.value;
                replaceBtn.disabled = false;
            });
        });

        modal.hidden = false;
        // MK2 — TTL countdown.
        if (window.NudgeTimer) {
            window.NudgeTimer.start(modal, body.expiresAt, () => {
                showError('nudge 세션이 만료되었습니다. 다시 업로드해주세요.');
            });
        }
        const closeModal = () => {
            if (window.NudgeTimer) window.NudgeTimer.stop(modal);
            modal.hidden = true;
            proceedBtn.onclick = null;
            replaceBtn.onclick = null;
            cancelBtn.onclick = null;
        };
        return {
            baseUrl, nudgeId, getSelectedTargetId: () => selectedTargetId, closeModal,
            proceedBtn, replaceBtn, cancelBtn
        };
    }

    function handleExpiredIfAny(closeModal, status, body) {
        if (window.NudgeTimer && window.NudgeTimer.isExpiredResponse(status, body)) {
            closeModal();
            showError('nudge 세션이 만료되었습니다. 다시 업로드해주세요.');
            return true;
        }
        return false;
    }

    function openContentNudgeModal(body) {
        const ctx = bindModalCommon(body, '/management/subprogram/nudge');
        if (!ctx) return;
        ctx.proceedBtn.onclick = async () => {
            disableNudgeBtns(true);
            try {
                const resp = await fetch(ctx.baseUrl + '/' + ctx.nudgeId + '/proceed', {
                    method: 'POST', headers: {'Accept': 'application/json'}
                });
                if (!resp.ok) {
                    const respBody = await resp.json().catch(() => ({}));
                    if (handleExpiredIfAny(ctx.closeModal, resp.status, respBody)) return;
                    showError(respBody.message || ('nudge proceed 실패 (HTTP ' + resp.status + ')'));
                    disableNudgeBtns(false);
                    return;
                }
                ctx.closeModal();
                window.location.href = listUrl;
            } catch (e) {
                showError('네트워크 오류 : ' + e.message);
                disableNudgeBtns(false);
            }
        };
        ctx.replaceBtn.onclick = async () => {
            const targetId = ctx.getSelectedTargetId();
            if (!targetId) return;
            if (!confirm('선택한 기존 자원을 영구 삭제하고 새 자원으로 등록합니다. 진행하시겠습니까?')) return;
            disableNudgeBtns(true);
            try {
                const resp = await fetch(ctx.baseUrl + '/' + ctx.nudgeId + '/replace?targetId=' + encodeURIComponent(targetId), {
                    method: 'POST', headers: {'Accept': 'application/json'}
                });
                if (!resp.ok) {
                    const respBody = await resp.json().catch(() => ({}));
                    if (handleExpiredIfAny(ctx.closeModal, resp.status, respBody)) return;
                    showError(respBody.message || ('nudge replace 실패 (HTTP ' + resp.status + ')'));
                    disableNudgeBtns(false);
                    return;
                }
                ctx.closeModal();
                window.location.href = listUrl;
            } catch (e) {
                showError('네트워크 오류 : ' + e.message);
                disableNudgeBtns(false);
            }
        };
        ctx.cancelBtn.onclick = async () => {
            disableNudgeBtns(true);
            try {
                await fetch(ctx.baseUrl + '/' + ctx.nudgeId + '/cancel', {
                    method: 'POST', headers: {'Accept': 'application/json'}
                });
            } catch (e) { /* ignore */
            }
            ctx.closeModal();
            showError('업로드를 취소했습니다.');
        };
    }

    function openIntentNudgeModal(body, uploadUrl, commonFields) {
        const ctx = bindModalCommon(body, '/management/subprogram/intent-nudge');
        if (!ctx) return;
        const handleIntentReissued = (intent) => {
            ctx.closeModal();
            if (intent.warnings && intent.warnings.length) {
                if (!confirm(intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?')) {
                    showError('업로드를 취소했습니다.');
                    return;
                }
            }
            startXhrUpload(uploadUrl, intent.uploadToken, commonFields);
        };

        ctx.proceedBtn.onclick = async () => {
            disableNudgeBtns(true);
            try {
                const resp = await fetch(ctx.baseUrl + '/' + ctx.nudgeId + '/proceed', {
                    method: 'POST', headers: {'Accept': 'application/json'}
                });
                const respBody = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    if (handleExpiredIfAny(ctx.closeModal, resp.status, respBody)) return;
                    showError(respBody.message || ('intent nudge proceed 실패 (HTTP ' + resp.status + ')'));
                    disableNudgeBtns(false);
                    return;
                }
                handleIntentReissued(respBody);
            } catch (e) {
                showError('네트워크 오류 : ' + e.message);
                disableNudgeBtns(false);
            }
        };
        ctx.replaceBtn.onclick = async () => {
            const targetId = ctx.getSelectedTargetId();
            if (!targetId) return;
            if (!confirm('선택한 기존 자원을 영구 삭제하고 새 자원으로 등록합니다. 진행하시겠습니까?')) return;
            disableNudgeBtns(true);
            try {
                const resp = await fetch(ctx.baseUrl + '/' + ctx.nudgeId + '/replace?targetId=' + encodeURIComponent(targetId), {
                    method: 'POST', headers: {'Accept': 'application/json'}
                });
                const respBody = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    if (handleExpiredIfAny(ctx.closeModal, resp.status, respBody)) return;
                    showError(respBody.message || ('intent nudge replace 실패 (HTTP ' + resp.status + ')'));
                    disableNudgeBtns(false);
                    return;
                }
                handleIntentReissued(respBody);
            } catch (e) {
                showError('네트워크 오류 : ' + e.message);
                disableNudgeBtns(false);
            }
        };
        ctx.cancelBtn.onclick = async () => {
            disableNudgeBtns(true);
            try {
                await fetch(ctx.baseUrl + '/' + ctx.nudgeId + '/cancel', {
                    method: 'POST', headers: {'Accept': 'application/json'}
                });
            } catch (e) { /* ignore */
            }
            ctx.closeModal();
            showError('업로드를 취소했습니다.');
        };
    }

    function disableNudgeBtns(disabled) {
        ['subprogramNudgeProceedBtn', 'subprogramNudgeReplaceBtn', 'subprogramNudgeCancelBtn'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.disabled = disabled;
        });
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    }
})();
