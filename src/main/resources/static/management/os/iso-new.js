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

    // ---- 서버 경로 탐색 — PathBrowser 공통 헬퍼에 위임 (iso-edit 와 동일 진입점). ------
    if (window.PathBrowser) {
        window.PathBrowser.attach({
            inputId: 'isoPath',
            browseBtnId: 'browseBtn',
            panelPrefix: 'browse',
            includeFiles: true,
            fileHighlight: (entry) => entry.name.toLowerCase().endsWith('.iso')
        });
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
        // S4 — submit 시작 시 폼 에러 초기화.
        if (window.FormError && typeof window.FormError.clear === 'function') {
            window.FormError.clear(form);
        }
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
            // MK2 WAVE 2 — intent path nudge (단계 A) 분기.
            if (err.body && err.body.code === 'NUDGE_REQUIRED' && err.body.nudgeId) {
                openIntentNudgeModal(err.body, file);
                submitBtn.disabled = false;
                submitBtn.textContent = '등록';
                return;
            }
            // S4 — body.fieldErrors 를 폼에 매핑.
            if (window.FormError && err.body) {
                window.FormError.renderResponse(err.body, { root: form });
            } else {
                showError(err.message);
            }
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

        // MK2 단계 A — preExistingMatch 사전 경고. 같은 OS 의 동일 isoPath 로 등록됐던 휴지통/Deprecated
        // 자원이 있을 때 1차 dismiss modal 로 사용자 의사 확인. confirm 으로 단순 대체 (커스텀 modal 추가는 미루는 리팩터).
        if (intent.preExistingMatch) {
            const m = intent.preExistingMatch;
            const stateLabel = m.state || '';
            const note = `같은 경로에 ${stateLabel} 상태의 자원이 이미 존재합니다.\n` +
                         `(id=${m.id}, ${m.name || ''} ${m.version || ''})\n\n진행하시겠습니까?`;
            if (!confirm(note)) {
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
            // S4 — 응답 body 전체 (fieldErrors 포함) 를 throw 에 부착해 호출자가 FormError.renderResponse 가능.
            let body = null;
            try { body = await resp.json(); } catch (_) { /* ignore */ }
            const fallback = (body && body.message) || intentFallbackMessage(resp.status);
            const finalBody = body || { message: fallback };
            if (!finalBody.message) finalBody.message = fallback;
            const err = new Error(finalBody.message);
            err.body = finalBody;
            err.status = resp.status;
            throw err;
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
        // CH3 : 업로드 진행 중 폴링 실패 누적 시 reload 보류 신호
        document.dispatchEvent(new CustomEvent('bgjob:uploadStart'));
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
        // CH3 : 업로드 진행 중 폴링 실패 누적 시 reload 보류 신호
        document.dispatchEvent(new CustomEvent('bgjob:uploadStart'));

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
        document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
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
        // S4 — XHR 응답 body 전체를 파싱해 FormError.renderResponse 로 fieldErrors 매핑.
        let body = null;
        try { body = JSON.parse(xhr.responseText || '{}'); } catch (_) { /* ignore */ }
        // MK2 단계 B — body.code === 'NUDGE_REQUIRED' 면 modal 표시 후 confirm endpoint 분기.
        if (body && body.code === 'NUDGE_REQUIRED' && body.nudgeId) {
            console.log(TAG, 'NUDGE_REQUIRED 수신 — modal 표시', body);
            hideProgress();
            lockPage(false);
            showNudgeModal(body);
            return;
        }
        const msg = (body && body.message) || ('HTTP ' + xhr.status);
        console.error(TAG, 'upload 실패', msg);
        if (window.FormError && body) {
            window.FormError.renderResponse(body, { root: form });
        } else {
            showError(msg);
        }
        hideProgress();
        lockPage(false);
    }

    // ---- MK2 nudge modal ---------------------------------------------

    /**
     * NUDGE_REQUIRED 응답 body 를 받아 modal 을 띄우고 사용자 3택 (proceed / replace / cancel) 처리.
     * fragments/management/nudge-modal.html 에서 만든 element id (prefix='nudge') 를 잡는다.
     */
    function showNudgeModal(payload) {
        const modal = document.getElementById('nudgeModal');
        if (!modal) {
            console.error(TAG, 'nudge modal element 가 페이지에 없습니다.');
            showError(payload.message || '동일한 자원이 이미 존재합니다.');
            return;
        }
        const baseUrl = modal.dataset.confirmBaseUrl || '/management/os/nudge';
        const list = document.getElementById('nudgeConflictsList');
        const proceedBtn = document.getElementById('nudgeProceedBtn');
        const replaceBtn = document.getElementById('nudgeReplaceBtn');
        const cancelBtn  = document.getElementById('nudgeCancelBtn');
        const backdrop   = document.getElementById('nudgeBackdrop');

        // 충돌 후보 렌더 + 단일 선택 (replace 활성화).
        list.innerHTML = '';
        let selectedTargetId = null;
        (payload.conflicts || []).forEach(c => {
            const li = document.createElement('li');
            li.style.padding = '10px 12px';
            li.style.borderBottom = '1px solid var(--n-border)';
            li.style.cursor = 'pointer';
            li.dataset.targetId = c.id;
            li.innerHTML =
                '<div style="font-weight: 600;">' + escapeHtml(c.name || '') + ' ' + escapeHtml(c.version || '') + '</div>' +
                '<div style="font-size: 12px; color: var(--n-text-muted);">' +
                'id=' + c.id + ' · ' + escapeHtml(c.state || '') + ' · ' + escapeHtml((c.hash || '').slice(0, 16)) + '…</div>';
            li.addEventListener('click', () => {
                Array.from(list.children).forEach(el => el.style.background = '');
                li.style.background = 'var(--n-bg-soft, #eef)';
                selectedTargetId = c.id;
                replaceBtn.disabled = false;
                replaceBtn.removeAttribute('title');
            });
            list.appendChild(li);
        });

        modal.hidden = false;

        const onProceed = () => {
            confirmNudge(baseUrl, payload.nudgeId, 'proceed', null);
        };
        const onReplace = () => {
            if (selectedTargetId == null) return;
            confirmNudge(baseUrl, payload.nudgeId, 'replace', selectedTargetId);
        };
        const onCancel = () => {
            confirmNudge(baseUrl, payload.nudgeId, 'cancel', null);
        };
        // 1회용 핸들러 — 종결 후 onclick 제거.
        proceedBtn.onclick = onProceed;
        replaceBtn.onclick = onReplace;
        cancelBtn.onclick = onCancel;
        backdrop.onclick = onCancel;
    }

    function confirmNudge(baseUrl, nudgeId, action, targetId) {
        let url = baseUrl + '/' + encodeURIComponent(nudgeId) + '/' + action;
        if (action === 'replace' && targetId != null) {
            url += '?targetId=' + encodeURIComponent(targetId);
        }
        fetch(url, { method: 'POST', headers: { 'Accept': 'application/json' } })
            .then(resp => {
                if (!resp.ok) {
                    return resp.json().catch(() => ({})).then(body => {
                        throw new Error((body && body.message) || ('HTTP ' + resp.status));
                    });
                }
                if (resp.status === 204) return null;
                return resp.json();
            })
            .then(body => {
                hideNudgeModal();
                if (action === 'cancel') {
                    showError('업로드를 취소했습니다.');
                    return;
                }
                const redirect = (body && body.redirect) || listUrl;
                try {
                    sessionStorage.setItem('os.isoRegistration.toast',
                        action === 'replace'
                            ? '기존 자원을 영구 삭제하고 신규 ISO 를 등록했습니다.'
                            : '신규 ISO 를 등록했습니다.');
                } catch (_) { /* ignore */ }
                window.location.href = redirect;
            })
            .catch(err => {
                hideNudgeModal();
                showError(err.message || 'nudge 처리에 실패했습니다.');
            });
    }

    function hideNudgeModal() {
        const modal = document.getElementById('nudgeModal');
        if (modal) modal.hidden = true;
    }

    // ---- MK2 WAVE 2 — intent (단계 A) path nudge modal ----------------
    //  intent fail 에서 호출됨. proceed/replace 시 새 uploadToken 받아 즉시 업로드 시작.
    function openIntentNudgeModal(payload, file) {
        const modal = document.getElementById('nudgeModal');
        if (!modal) {
            showError('nudge modal element 가 페이지에 없습니다.');
            return;
        }
        const intentBase = '/management/os/intent-nudge';
        const list = document.getElementById('nudgeConflictsList');
        const proceedBtn = document.getElementById('nudgeProceedBtn');
        const replaceBtn = document.getElementById('nudgeReplaceBtn');
        const cancelBtn  = document.getElementById('nudgeCancelBtn');
        const backdrop   = document.getElementById('nudgeBackdrop');
        if (!list || !proceedBtn || !replaceBtn || !cancelBtn) {
            showError('nudge modal 구성 요소가 없습니다.');
            return;
        }

        let selectedTargetId = null;
        list.innerHTML = '';
        (payload.conflicts || []).forEach(entry => {
            const li = document.createElement('li');
            li.style.padding = '8px 12px';
            li.style.borderBottom = '1px solid var(--n-border, #e0e0e0)';
            li.innerHTML =
                '<label style="display:flex; gap:8px; align-items:center; cursor:pointer;">' +
                '  <input type="radio" name="isoIntentNudgeTarget" value="' + entry.id + '">' +
                '  <span><strong>' + escapeHtml(entry.name) + '</strong> · v' + escapeHtml(entry.version) +
                '    <span style="color: var(--n-text-muted, #777); font-size: 11px;">[' + entry.state + ' · #' + entry.id + ']</span></span>' +
                '</label>';
            list.appendChild(li);
        });
        replaceBtn.disabled = true;
        list.querySelectorAll('input[name="isoIntentNudgeTarget"]').forEach(input => {
            input.addEventListener('change', () => {
                selectedTargetId = input.value;
                replaceBtn.disabled = false;
            });
        });

        modal.hidden = false;

        const reissue = (intent) => {
            hideNudgeModal();
            if (intent.warnings && intent.warnings.length) {
                if (!confirm(intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?')) {
                    showError('업로드를 취소했습니다.');
                    return;
                }
            }
            startXhrUpload(file, intent.uploadToken);
        };

        proceedBtn.onclick = async () => {
            disableIntentBtns(true);
            try {
                const resp = await fetch(intentBase + '/' + payload.nudgeId + '/proceed', {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
                const body = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    showError(body.message || ('intent nudge proceed 실패 (HTTP ' + resp.status + ')'));
                    disableIntentBtns(false);
                    return;
                }
                reissue(body);
            } catch (e) {
                showError('네트워크 오류 : ' + e.message);
                disableIntentBtns(false);
            }
        };
        replaceBtn.onclick = async () => {
            if (!selectedTargetId) return;
            if (!confirm('선택한 기존 자원을 영구 삭제하고 새 자원으로 등록합니다. 진행하시겠습니까?')) return;
            disableIntentBtns(true);
            try {
                const resp = await fetch(intentBase + '/' + payload.nudgeId + '/replace?targetId=' + encodeURIComponent(selectedTargetId), {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
                const body = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    showError(body.message || ('intent nudge replace 실패 (HTTP ' + resp.status + ')'));
                    disableIntentBtns(false);
                    return;
                }
                reissue(body);
            } catch (e) {
                showError('네트워크 오류 : ' + e.message);
                disableIntentBtns(false);
            }
        };
        cancelBtn.onclick = async () => {
            disableIntentBtns(true);
            try {
                await fetch(intentBase + '/' + payload.nudgeId + '/cancel', {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
            } catch (_) { /* ignore */ }
            hideNudgeModal();
            showError('업로드를 취소했습니다.');
        };
        if (backdrop) backdrop.onclick = cancelBtn.onclick;
    }

    function disableIntentBtns(disabled) {
        ['nudgeProceedBtn', 'nudgeReplaceBtn', 'nudgeCancelBtn'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.disabled = disabled;
        });
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function handleUploadNetworkError(message, checksumTimer) {
        uploading = false;
        document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
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
