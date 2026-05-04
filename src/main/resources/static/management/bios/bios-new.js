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
        // S4 — submit 시작 시 기존 폼 에러 (배너 + 필드 has-error) 초기화.
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
            // MK2 WAVE 2 — intent 시점 메타 nudge (단계 A) 분기. proceed/replace 시 새 token 받아 자동 업로드 재개.
            if (err.body && err.body.code === 'NUDGE_REQUIRED' && err.body.nudgeId) {
                openIntentNudgeModal(err.body, commonFields);
                submitBtn.disabled = false;
                submitBtn.textContent = '번들 등록';
                return;
            }
            // S4 — 응답 body 의 fieldErrors 를 폼에 매핑 + banner 노출.
            if (window.FormError && err.body) {
                window.FormError.renderResponse(err.body, { root: form });
            } else {
                showError(err.message);
            }
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
                // CH3 : 업로드 진행 중 폴링 실패 누적 시 reload 보류 신호
                document.dispatchEvent(new CustomEvent('bgjob:uploadStart'));
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
                document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
                activeXhr = null;
                if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
                showProgress('100%  완료', 100);
                window.location.href = listUrl;
            },
            onHttpError(msg, xhr, body) {
                uploading = false;
                document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
                activeXhr = null;
                if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
                hideProgress();
                lockPage(false);
                // MK2 — 409 NUDGE_REQUIRED 응답이면 nudge modal 표시 + 사용자 3택 routing.
                if (body && body.code === 'NUDGE_REQUIRED' && body.nudgeId) {
                    openNudgeModal(body);
                    return;
                }
                // S4 — 업로드 응답 body 의 fieldErrors 매핑.
                if (window.FormError && body) {
                    window.FormError.renderResponse(body, { root: form });
                } else {
                    showError(msg);
                }
            },
            onNetworkError() {
                uploading = false;
                document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
                activeXhr = null;
                if (serverTimer) { clearInterval(serverTimer); serverTimer = null; }
                showError('네트워크 오류로 업로드가 중단되었습니다.');
                hideProgress();
                lockPage(false);
            },
            onAbort() {
                uploading = false;
                document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
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

    // ---- MK2 nudge modal -----------------------------------------------

    function openNudgeModal(body) {
        const modal       = document.getElementById('biosNudgeModal');
        const conflicts   = document.getElementById('biosNudgeConflictsList');
        const proceedBtn  = document.getElementById('biosNudgeProceedBtn');
        const replaceBtn  = document.getElementById('biosNudgeReplaceBtn');
        const cancelBtn   = document.getElementById('biosNudgeCancelBtn');
        if (!modal || !conflicts || !proceedBtn || !replaceBtn || !cancelBtn) {
            showError('nudge modal 요소를 찾을 수 없습니다. 페이지를 새로고침 해주세요.');
            return;
        }
        const baseUrl = modal.dataset.confirmBaseUrl;
        const nudgeId = body.nudgeId;
        let selectedTargetId = null;

        // conflicts 렌더 — radio 선택 시 replaceBtn 활성화.
        conflicts.innerHTML = '';
        (body.conflicts || []).forEach(entry => {
            const li = document.createElement('li');
            li.style.padding = '8px 12px';
            li.style.borderBottom = '1px solid var(--n-border, #e0e0e0)';
            li.innerHTML =
                '<label style="display:flex; gap:8px; align-items:center; cursor:pointer;">' +
                '  <input type="radio" name="biosNudgeTarget" value="' + entry.id + '">' +
                '  <span><strong>' + escapeHtml(entry.name) + '</strong> · v' + escapeHtml(entry.version) +
                '    <span style="color: var(--n-text-muted, #777); font-size: 11px;">[' + entry.state + ' · #' + entry.id + ']</span></span>' +
                '</label>';
            conflicts.appendChild(li);
        });
        conflicts.querySelectorAll('input[name="biosNudgeTarget"]').forEach(input => {
            input.addEventListener('change', () => {
                selectedTargetId = input.value;
                replaceBtn.disabled = false;
            });
        });

        modal.hidden = false;

        const closeModal = () => {
            modal.hidden = true;
            proceedBtn.onclick = null;
            replaceBtn.onclick = null;
            cancelBtn.onclick = null;
        };

        proceedBtn.onclick = async () => {
            disableNudgeButtons(true);
            try {
                const resp = await fetch(baseUrl + '/' + nudgeId + '/proceed', {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
                const respBody = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    showError(respBody.message || ('nudge proceed 실패 (HTTP ' + resp.status + ')'));
                    disableNudgeButtons(false);
                    return;
                }
                closeModal();
                window.location.href = respBody.redirect || listUrl;
            } catch (err) {
                showError('네트워크 오류 : ' + err.message);
                disableNudgeButtons(false);
            }
        };

        replaceBtn.onclick = async () => {
            if (!selectedTargetId) return;
            if (!confirm('선택한 기존 자원을 영구 삭제하고 새 자원으로 등록합니다. 진행하시겠습니까?')) return;
            disableNudgeButtons(true);
            try {
                const resp = await fetch(baseUrl + '/' + nudgeId + '/replace?targetId=' + encodeURIComponent(selectedTargetId), {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
                const respBody = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    showError(respBody.message || ('nudge replace 실패 (HTTP ' + resp.status + ')'));
                    disableNudgeButtons(false);
                    return;
                }
                closeModal();
                window.location.href = respBody.redirect || listUrl;
            } catch (err) {
                showError('네트워크 오류 : ' + err.message);
                disableNudgeButtons(false);
            }
        };

        cancelBtn.onclick = async () => {
            disableNudgeButtons(true);
            try {
                await fetch(baseUrl + '/' + nudgeId + '/cancel', {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
            } catch (err) {
                console.warn(TAG, 'cancel 호출 실패 (무시) :', err);
            } finally {
                closeModal();
                showError('업로드를 취소했습니다.');
            }
        };
    }

    // ---- MK2 WAVE 2 — intent (단계 A) nudge modal ---------------------
    //  단계 B (해시 nudge) 의 openNudgeModal 과 UI 는 동일하지만 endpoint base 와 후속 동작만 다름:
    //   · proceed/replace → 새 uploadToken 수신 → 그 token 으로 자동 업로드 시작
    //   · cancel → 폼 상태만 복구 (임시 파일 없음)

    function openIntentNudgeModal(body, commonFields) {
        const modal       = document.getElementById('biosNudgeModal');
        const conflicts   = document.getElementById('biosNudgeConflictsList');
        const proceedBtn  = document.getElementById('biosNudgeProceedBtn');
        const replaceBtn  = document.getElementById('biosNudgeReplaceBtn');
        const cancelBtn   = document.getElementById('biosNudgeCancelBtn');
        if (!modal || !conflicts || !proceedBtn || !replaceBtn || !cancelBtn) {
            showError('nudge modal 요소를 찾을 수 없습니다. 페이지를 새로고침 해주세요.');
            return;
        }
        // 단계 B 의 baseUrl (`.../nudge`) 을 단계 A 용 `.../intent-nudge` 로 치환.
        const contentBase = modal.dataset.confirmBaseUrl || '';
        const intentBase  = contentBase.replace(/\/nudge$/, '/intent-nudge');
        const nudgeId = body.nudgeId;
        let selectedTargetId = null;

        conflicts.innerHTML = '';
        (body.conflicts || []).forEach(entry => {
            const li = document.createElement('li');
            li.style.padding = '8px 12px';
            li.style.borderBottom = '1px solid var(--n-border, #e0e0e0)';
            li.innerHTML =
                '<label style="display:flex; gap:8px; align-items:center; cursor:pointer;">' +
                '  <input type="radio" name="biosIntentNudgeTarget" value="' + entry.id + '">' +
                '  <span><strong>' + escapeHtml(entry.name) + '</strong> · v' + escapeHtml(entry.version) +
                '    <span style="color: var(--n-text-muted, #777); font-size: 11px;">[' + entry.state + ' · #' + entry.id + ']</span></span>' +
                '</label>';
            conflicts.appendChild(li);
        });
        replaceBtn.disabled = true;
        conflicts.querySelectorAll('input[name="biosIntentNudgeTarget"]').forEach(input => {
            input.addEventListener('change', () => {
                selectedTargetId = input.value;
                replaceBtn.disabled = false;
            });
        });

        modal.hidden = false;

        const closeModal = () => {
            modal.hidden = true;
            proceedBtn.onclick = null;
            replaceBtn.onclick = null;
            cancelBtn.onclick = null;
        };

        // 단계 A 의 후속 — 새 uploadToken 받아 즉시 업로드 시작.
        const handleIntentReissued = (intent) => {
            closeModal();
            if (intent.warnings && intent.warnings.length) {
                const msg = intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?';
                if (!confirm(msg)) {
                    showError('업로드를 취소했습니다.');
                    return;
                }
            }
            startXhrUpload(intent.uploadToken, commonFields);
        };

        proceedBtn.onclick = async () => {
            disableNudgeButtons(true);
            try {
                const resp = await fetch(intentBase + '/' + nudgeId + '/proceed', {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
                const respBody = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    showError(respBody.message || ('intent nudge proceed 실패 (HTTP ' + resp.status + ')'));
                    disableNudgeButtons(false);
                    return;
                }
                handleIntentReissued(respBody);
            } catch (err) {
                showError('네트워크 오류 : ' + err.message);
                disableNudgeButtons(false);
            }
        };

        replaceBtn.onclick = async () => {
            if (!selectedTargetId) return;
            if (!confirm('선택한 기존 자원을 영구 삭제하고 새 자원으로 등록합니다. 진행하시겠습니까?')) return;
            disableNudgeButtons(true);
            try {
                const resp = await fetch(intentBase + '/' + nudgeId + '/replace?targetId=' + encodeURIComponent(selectedTargetId), {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
                const respBody = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    showError(respBody.message || ('intent nudge replace 실패 (HTTP ' + resp.status + ')'));
                    disableNudgeButtons(false);
                    return;
                }
                handleIntentReissued(respBody);
            } catch (err) {
                showError('네트워크 오류 : ' + err.message);
                disableNudgeButtons(false);
            }
        };

        cancelBtn.onclick = async () => {
            disableNudgeButtons(true);
            try {
                await fetch(intentBase + '/' + nudgeId + '/cancel', {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
            } catch (err) {
                console.warn(TAG, 'intent cancel 호출 실패 (무시) :', err);
            } finally {
                closeModal();
                showError('업로드를 취소했습니다.');
            }
        };
    }

    function disableNudgeButtons(disabled) {
        ['biosNudgeProceedBtn', 'biosNudgeReplaceBtn', 'biosNudgeCancelBtn'].forEach(id => {
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
