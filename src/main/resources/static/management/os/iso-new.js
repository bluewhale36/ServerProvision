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
    const listUrl = form.dataset.listUrl;
    const submitBtn = document.getElementById('submitBtn');
    const cancelLink = document.getElementById('cancelLink');
    const backLink = document.getElementById('backLink');

    const progressBox = document.getElementById('uploadProgress');
    const progressBar = document.getElementById('uploadBar');
    const progressText = document.getElementById('uploadText');
    const errorBox = document.getElementById('uploadError');

    if (!uploadUrl || !listUrl || !submitBtn) return;

    let activeXhr = null;
    let uploading = false;

    const RETRY_MSG = '요청을 처리하지 못했습니다. 다시 시도해야 합니다.';

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

        // MK2 WAVE 3 — 2단계 intent 흐름 위임.
        await runIntentFlow(file, isoPath, allowCreateDirectory);
    });

    /**
     * MK2 WAVE 3 — 2단계 intent 흐름.
     * Phase 1 응답이 HASH_CHECK_REQUIRED 면 Web Worker SHA-256 계산 후 Phase 2 호출.
     */
    async function runIntentFlow(file, isoPath, allowCreateDirectory) {
        let intent;
        try {
            intent = await requestIntent(isoPath, file, allowCreateDirectory, null);
        } catch (err) {
            handleIntentError(err, file);
            return;
        }

        // sealed sum type — type discriminator 로 분기
        if (intent && intent.type === 'HASH_CHECK_REQUIRED') {
            // Phase 1.5 — Web Worker SHA-256 계산
            submitBtn.textContent = '해시 계산 중… 0%';
            let clientHash;
            try {
                clientHash = await computeFingerprint(file, (progress, processedBytes, totalBytes) => {
                    const pct = Math.floor(progress * 100);
                    submitBtn.textContent = `해시 계산 중… ${pct}%`;
                });
            } catch (err) {
                console.error(TAG, 'fingerprint 계산 실패', err);
                showError('파일의 해시를 계산하지 못했습니다. 다시 시도해야 합니다.');
                submitBtn.disabled = false;
                submitBtn.textContent = '등록';
                return;
            }

            // Phase 2 — hash 동봉 재호출
            submitBtn.textContent = '사전 검증 중…';
            try {
                intent = await requestIntent(isoPath, file, allowCreateDirectory, clientHash);
            } catch (err) {
                handleIntentError(err, file);
                return;
            }
            // Phase 2 응답이 또 HashCheckRequired 면 안 됨 (이론상)
            if (intent.type !== 'INTENT_TOKEN_ISSUED') {
                console.error(TAG, 'intent 응답 형식 오류', intent.type);
                showError(RETRY_MSG);
                submitBtn.disabled = false;
                submitBtn.textContent = '등록';
                return;
            }
        }

        // INTENT_TOKEN_ISSUED — 정상 진행
        if (intent.warnings && intent.warnings.length) {
            const msg = intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?';
            if (!confirm(msg)) {
                submitBtn.disabled = false;
                submitBtn.textContent = '등록';
                return;
            }
        }

        startXhrUpload(file, intent.uploadToken);
    }

    function handleIntentError(err, file) {
        console.error(TAG, 'intent 실패', err);
        if (err.body && err.body.code === 'NUDGE_REQUIRED' && err.body.nudgeId) {
            openIntentNudgeModal(err.body, file);
            submitBtn.disabled = false;
            submitBtn.textContent = '등록';
            return;
        }
        if (window.FormError && err.body) {
            window.FormError.renderResponse(err.body, {root: form});
        } else {
            showError(err.message);
        }
        submitBtn.disabled = false;
        submitBtn.textContent = '등록';
    }

    // MK2 WAVE 3 — Web Worker 로 SHA-256 계산. Promise resolve = hex string.
    function computeFingerprint(file, onProgress) {
        return new Promise((resolve, reject) => {
            const worker = new Worker('/global/iso-fingerprint-worker.js');
            const requestId = 'req-' + Date.now();
            worker.onmessage = (e) => {
                const msg = e.data || {};
                if (msg.requestId !== requestId) return;
                if (msg.error) {
                    worker.terminate();
                    reject(new Error(msg.error));
                    return;
                }
                if (msg.fingerprint) {
                    worker.terminate();
                    resolve(msg.fingerprint);
                    return;
                }
                if (typeof msg.progress === 'number' && typeof onProgress === 'function') {
                    onProgress(msg.progress, msg.processedBytes, msg.totalBytes);
                }
            };
            worker.onerror = (e) => {
                worker.terminate();
                reject(new Error('Worker error : ' + (e.message || e)));
            };
            worker.postMessage({requestId, file});
        });
    }

    async function requestIntent(isoPath, file, allowCreateDirectory, clientHash) {
        // uploadUrl = /management/os/{osId}/iso/upload → 같은 경로에서 /upload-intent 로 파생
        const intentUrl = uploadUrl.replace(/\/upload$/, '/upload-intent');
        const body = {
            isoPath,
            filename: file.name,
            size: file.size,
            allowCreateDirectory
        };
        if (clientHash) body.clientHash = clientHash;
        const resp = await fetch(intentUrl, {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
            body: JSON.stringify(body)
        });
        if (!resp.ok) {
            // S4 — 응답 body 전체 (fieldErrors 포함) 를 throw 에 부착해 호출자가 FormError.renderResponse 가능.
            let body = null;
            try {
                body = await resp.json();
            } catch (_) { /* ignore */
            }
            const fallback = (body && body.message) || intentFallbackMessage(resp.status);
            const finalBody = body || {message: fallback};
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
            case 400:
                return '입력값이 올바르지 않습니다. ISO 경로와 파일을 다시 확인해야 합니다.';
            case 404:
                return '대상 OS 버전을 찾을 수 없습니다. 목록에서 다시 선택해야 합니다.';
            case 409:
                return '같은 경로에 이미 등록된 ISO 가 있거나 사전 검증 조건에 맞지 않습니다.';
            case 500:
                return '서버 오류로 사전 검증에 실패했습니다. 잠시 후 다시 시도해야 합니다.';
            default:
                return '사전 검증에 실패했습니다. 다시 시도해야 합니다.';
        }
    }

    function startPathOnlyRegistration() {
        const fd = new FormData(form);

        resetError();
        lockPage(true);
        showProgress('경로를 확인하고 등록을 시작하는 중…', null);

        const xhr = new XMLHttpRequest();
        activeXhr = xhr;
        uploading = true;
        // CH3 : 업로드 진행 중 폴링 실패 누적 시 reload 보류 신호
        document.dispatchEvent(new CustomEvent('bgjob:uploadStart'));
        xhr.open('POST', uploadUrl);

        xhr.addEventListener('load', () => handleUploadResponse(xhr, false));
        xhr.addEventListener('error', () => handleUploadNetworkError('네트워크 오류로 등록 요청이 중단되었습니다.'));
        xhr.addEventListener('abort', () => handleUploadAborted('등록 요청을 취소했습니다.'));
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
            speedSamples.push({t: now, loaded: ev.loaded});
            while (speedSamples.length > 1 && now - speedSamples[0].t > SPEED_WINDOW_MS) {
                speedSamples.shift();
            }
            let speedBps = 0;
            if (speedSamples.length >= 2) {
                const first = speedSamples[0];
                const last = speedSamples[speedSamples.length - 1];
                const dtSec = (last.t - first.t) / 1000;
                if (dtSec > 0) speedBps = (last.loaded - first.loaded) / dtSec;
            }
            const etaSec = speedBps > 0 ? Math.round((ev.total - ev.loaded) / speedBps) : -1;

            const msg = `${UiUtil.formatBytes(ev.loaded)} / ${UiUtil.formatBytes(ev.total)}` +
                (speedBps > 0 ? `, ${UiUtil.formatBytes(speedBps)}/s` : '') +
                (etaSec >= 0 ? `, 남은 시간 약 ${formatDuration(etaSec)}` : '');
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
                    `${Math.floor(pct)}%  서버 수신 완료, 등록 시작 중…`,
                    pct
                );
            }, 150);
        });

        xhr.addEventListener('load', () => handleUploadResponse(xhr, true, checksumTimer));
        xhr.addEventListener('error', () => handleUploadNetworkError('네트워크 오류로 업로드가 중단되었습니다.', checksumTimer));
        xhr.addEventListener('abort', () => handleUploadAborted('업로드를 취소했습니다.', checksumTimer));

        xhr.send(fd);
    }

    function handleUploadResponse(xhr, hadFile, checksumTimer) {
        uploading = false;
        document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
        activeXhr = null;
        if (checksumTimer) {
            clearInterval(checksumTimer);
        }
        if (xhr.status >= 200 && xhr.status < 300) {
            let body = {};
            try {
                body = JSON.parse(xhr.responseText || '{}');
            } catch (_) { /* ignore */
            }
            const redirect = body.redirect || listUrl;
            const msg = hadFile
                ? 'ISO 업로드를 마쳤습니다. 등록을 이어서 진행합니다.'
                : 'ISO 등록을 시작했습니다.';
            try {
                sessionStorage.setItem('os.isoRegistration.toast', msg);
            } catch (_) { /* ignore */
            }
            showProgress('100%  등록 시작', 100);
            window.location.href = redirect;
            return;
        }
        // S4 — XHR 응답 body 전체를 파싱해 FormError.renderResponse 로 fieldErrors 매핑.
        let body = null;
        try {
            body = JSON.parse(xhr.responseText || '{}');
        } catch (_) { /* ignore */
        }
        const msg = (body && body.message) || '업로드에 실패했습니다. 다시 시도해야 합니다.';
        console.error(TAG, 'upload 실패', msg);
        if (window.FormError && body) {
            window.FormError.renderResponse(body, {root: form});
        } else {
            showError(msg);
        }
        hideProgress();
        lockPage(false);
    }

    // ---- 중복 확인 창 (처리는 global/nudge-modal.js 한 곳, HF30-3 D14) ----------------
    //  ISO 는 업로드 전(intent) 창만 있다. 업로드 뒤 창은 HF30-2 에서 서버 경로와 함께 사라졌다.
    function openIntentNudgeModal(payload, file) {
        NudgeModal.open(payload, {
            prefix: 'nudge',
            phase: 'intent',
            cancelNote: 'beforeUpload',
            onDone: intent => {
                if (intent.warnings && intent.warnings.length) {
                    if (!confirm(intent.warnings.join('\n') + '\n\n그래도 업로드를 진행하시겠습니까?')) {
                        showNotice('등록을 취소했습니다.');
                        return;
                    }
                }
                startXhrUpload(file, intent.uploadToken);
            },
            notify: {info: showNotice, error: showError}
        });
    }

    function handleUploadNetworkError(message, checksumTimer) {
        endUpload(checksumTimer);
        showError(message);
    }

    // 사용자가 직접 취소한 것은 실패가 아니므로 오류 상자 대신 알림으로 보인다(S22-4).
    function handleUploadAborted(message, checksumTimer) {
        endUpload(checksumTimer);
        showNotice(message);
    }

    function endUpload(checksumTimer) {
        uploading = false;
        document.dispatchEvent(new CustomEvent('bgjob:uploadEnd'));
        activeXhr = null;
        if (checksumTimer) {
            clearInterval(checksumTimer);
        }
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

    function preventClick(e) {
        e.preventDefault();
        e.stopPropagation();
    }

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
        if (!errorBox) {
            alert(msg);
            return;
        }
        errorBox.textContent = msg;
        errorBox.classList.add('is-visible');
    }

    // 취소처럼 실패가 아닌 결과는 빨간 오류 상자 대신 알림으로 보인다(S22-4).
    function showNotice(msg) {
        if (window.UiUtil) UiUtil.toast(msg, 'info');
    }

    function resetError() {
        if (!errorBox) return;
        errorBox.textContent = '';
        errorBox.classList.remove('is-visible');
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
