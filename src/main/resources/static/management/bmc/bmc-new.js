/* ============================================================
   management/bmc/bmc-new.html 전용 스크립트
   ─────────────────────────────────────────────────────────────
   BIOS 업로드 흐름과 동일한 intent + XHR foreground 업로드.
   ============================================================ */
(function () {
    const TAG = '[bmc-new]';

    // S5-5 — 외부 우상단 '+ 신규 BMC 등록' 진입 시 폼이 AJAX 로 동적 삽입되므로
    // init() 으로 본체를 분리. idempotent 가드로 동일 form 중복 바인딩 방지.
    function init() {
        const form = document.getElementById('bmcForm');
        if (!form) return;
        if (form.dataset.bmcNewBound === '1') return;
        form.dataset.bmcNewBound = '1';
        const shell = window.BundleUploadShell;
        if (!shell) return;

        const uploadUrl = form.dataset.uploadUrl;
        const intentUrl = form.dataset.intentUrl;
        const registerExistingUrl = form.dataset.registerExistingUrl;
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
                },
                onFilesChange: resetError
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
            resetError();   // HF-4 — uploadError 박스도 초기화(재제출 stale 방지)

            const mode = uploadModeInput.value;

            if (mode === 'EXISTING_DIRECTORY') {
                await submitRegisterExisting();
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
                // MK2 WAVE 2 — intent 메타 nudge (단계 A) 분기.
                if (err.body && err.body.code === 'NUDGE_REQUIRED' && err.body.nudgeId) {
                    openIntentNudgeModal(err.body, commonFields);
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

        async function submitRegisterExisting() {
            if (!registerExistingUrl) {
                showError('기존 디렉토리 등록 URL 이 설정되지 않았습니다. 페이지를 새로고침 해주세요.');
                return;
            }
            const fields = shell.resolveCommonFields(form);
            submitBtn.disabled = true;
            const originalLabel = submitBtn.textContent;
            submitBtn.textContent = '등록 중…';
            try {
                const resp = await fetch(registerExistingUrl, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                    body: JSON.stringify({
                        name: fields.name.trim(),
                        version: fields.version.trim(),
                        targetDirectory: fields.targetDirectory.trim(),
                        description: fields.description,
                        entrypointRelativePath: fields.entrypointRelativePath.trim()
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

        function startXhrUpload(uploadToken, commonFields) {
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
        //  modal element id prefix = 'nudge' (bmc-new.html 의 fragment include 와 일치).
        //  · openContentNudgeModal — 단계 B (해시 충돌) : proceed/replace 시 listUrl 로 redirect (서버 204 응답)
        //  · openIntentNudgeModal  — 단계 A (intent 메타 충돌) : proceed/replace 시 새 token 받아 자동 업로드

        function bindModalCommon(body, opts) {
            const modal = document.getElementById('nudgeModal');
            const conflicts = document.getElementById('nudgeConflictsList');
            const proceedBtn = document.getElementById('nudgeProceedBtn');
            const replaceBtn = document.getElementById('nudgeReplaceBtn');
            const cancelBtn = document.getElementById('nudgeCancelBtn');
            if (!modal || !conflicts || !proceedBtn || !replaceBtn || !cancelBtn) {
                showError('nudge modal 요소를 찾을 수 없습니다. 페이지를 새로고침 해주세요.');
                return null;
            }
            const baseUrl = opts.baseUrl;
            const nudgeId = body.nudgeId;
            let selectedTargetId = null;

            conflicts.innerHTML = '';
            (body.conflicts || []).forEach(entry => {
                const li = document.createElement('li');
                li.style.padding = '8px 12px';
                li.style.borderBottom = '1px solid var(--n-border, #e0e0e0)';
                li.innerHTML =
                    '<label style="display:flex; gap:8px; align-items:center; cursor:pointer;">' +
                    '  <input type="radio" name="bmcNudgeTarget" value="' + entry.id + '">' +
                    '  <span><strong>' + escapeHtml(entry.name) + '</strong> · v' + escapeHtml(entry.version) +
                    '    <span style="color: var(--n-text-muted, #777); font-size: 11px;">[' + entry.state + ' · #' + entry.id + ']</span></span>' +
                    '</label>';
                conflicts.appendChild(li);
            });
            replaceBtn.disabled = true;
            conflicts.querySelectorAll('input[name="bmcNudgeTarget"]').forEach(input => {
                input.addEventListener('change', () => {
                    selectedTargetId = input.value;
                    replaceBtn.disabled = false;
                });
            });

            modal.hidden = false;
            // MK2 — TTL countdown. 0:00 도달 시 modal 자동 닫힘 + 안내.
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
                modal, baseUrl, nudgeId, getSelectedTargetId: () => selectedTargetId, closeModal,
                proceedBtn, replaceBtn, cancelBtn
            };
        }

        // 만료 응답 (404 NudgeNotFound / 409 NudgeSessionExpired) 공통 처리.
        function handleExpiredIfAny(closeModal, status, body) {
            if (window.NudgeTimer && window.NudgeTimer.isExpiredResponse(status, body)) {
                closeModal();
                showError('nudge 세션이 만료되었습니다. 다시 업로드해주세요.');
                return true;
            }
            return false;
        }

        function openContentNudgeModal(body) {
            const ctx = bindModalCommon(body, {baseUrl: '/management/bmc/nudge'});
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
                    const resp = await fetch(ctx.baseUrl + '/' + ctx.nudgeId + '/replace?replaceTargetId=' + encodeURIComponent(targetId), {
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

        function openIntentNudgeModal(body, commonFields) {
            const ctx = bindModalCommon(body, {baseUrl: '/management/bmc/intent-nudge'});
            if (!ctx) return;

            const handleIntentReissued = (intent) => {
                ctx.closeModal();
                if (intent.warnings && intent.warnings.length) {
                    if (!confirm(intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?')) {
                        showError('업로드를 취소했습니다.');
                        return;
                    }
                }
                startXhrUpload(intent.uploadToken, commonFields);
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
            ['nudgeProceedBtn', 'nudgeReplaceBtn', 'nudgeCancelBtn'].forEach(id => {
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
    }  // end init()

    window.BmcNewForm = {init: init};
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
