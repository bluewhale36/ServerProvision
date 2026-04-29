(function () {
    function collectSizeInfo(mode, inputs) {
        const { folderInput, zipInput, singleInput } = inputs;
        if (mode === 'FOLDER') {
            const files = Array.from((folderInput && folderInput.files) || []);
            return { fileCount: files.length, totalBytes: files.reduce((sum, file) => sum + file.size, 0) };
        }
        if (mode === 'ZIP') {
            const file = zipInput && zipInput.files && zipInput.files[0];
            return { fileCount: file ? 1 : 0, totalBytes: file ? file.size : 0 };
        }
        const file = singleInput && singleInput.files && singleInput.files[0];
        return { fileCount: file ? 1 : 0, totalBytes: file ? file.size : 0 };
    }

    function validateFolderWrapping(files) {
        if (files.length === 0) return '업로드할 파일이 없습니다.';
        const prefixes = new Set();
        for (const file of files) {
            const rel = file.webkitRelativePath || '';
            if (!rel) return '여러 개의 개별 파일 업로드는 거절됩니다. 관련 파일들을 하나의 폴더로 묶어 업로드하세요.';
            const firstSeg = rel.split('/')[0];
            if (!firstSeg) return '파일 상대경로 형식이 올바르지 않습니다.';
            prefixes.add(firstSeg);
            if (prefixes.size > 1) return '여러 폴더가 섞여 있습니다. 단일 폴더만 선택해 업로드하세요.';
        }
        return null;
    }

    async function requestIntent(opts) {
        const { intentUrl, body, intentFallbackMessage } = opts;
        const resp = await fetch(intentUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!resp.ok) {
            // S4 — 응답 body 전체 (fieldErrors 포함) 를 호출자에 전달해 FormError.renderResponse 가능하게 한다.
            let payload = null;
            try { payload = await resp.json(); } catch (_) { /* ignore */ }
            let message = (payload && payload.message) || null;
            if (!message && typeof intentFallbackMessage === 'function') {
                message = intentFallbackMessage(resp.status);
            }
            const fallback = message || ('사전 검증 실패 (HTTP ' + resp.status + ')');
            const finalBody = payload || { message: fallback };
            // payload.message 가 비어있으면 fallback 으로 보강
            if (!finalBody.message) finalBody.message = fallback;
            const err = new Error(finalBody.message);
            err.body = finalBody;
            err.status = resp.status;
            throw err;
        }
        return resp.json();
    }

    function buildBundleFormData(opts) {
        const {
            form, uploadModeInput, folderInput, zipInput, singleInput,
            fixedFields
        } = opts;
        const formData = new FormData();
        const mode = uploadModeInput.value;
        formData.append('uploadMode', mode);
        if (fixedFields) {
            Object.entries(fixedFields).forEach(([key, value]) => {
                formData.append(key, value == null ? '' : value);
            });
        }
        if (mode === 'FOLDER') {
            for (const file of Array.from((folderInput && folderInput.files) || [])) {
                formData.append('folderFiles', file, file.webkitRelativePath || file.name);
            }
        } else if (mode === 'ZIP') {
            const file = zipInput && zipInput.files && zipInput.files[0];
            if (file) formData.append('zipFile', file, file.name);
        } else {
            const file = singleInput && singleInput.files && singleInput.files[0];
            if (file) formData.append('singleFile', file, file.name);
        }
        return { formData, mode, form };
    }

    function createUploadProgressTracker() {
        return {
            lastUiUpdate: 0,
            speedSamples: []
        };
    }

    function describeUploadProgress(event, opts) {
        const {
            tracker,
            reportIntervalMs = 100,
            speedWindowMs = 3000,
            displayPercent = null
        } = opts;
        if (!event.lengthComputable) {
            return { shouldRender: true, percent: displayPercent, message: '전송 중…' };
        }

        const now = Date.now();
        if (tracker && now - tracker.lastUiUpdate < reportIntervalMs) {
            return { shouldRender: false };
        }
        if (tracker) tracker.lastUiUpdate = now;

        const percent = displayPercent == null
                ? Math.max(0, Math.min(100, Math.round((event.loaded / event.total) * 100)))
                : displayPercent;

        let speedBps = 0;
        let etaSec = -1;
        if (tracker) {
            tracker.speedSamples.push({ t: now, loaded: event.loaded });
            while (tracker.speedSamples.length > 1 && now - tracker.speedSamples[0].t > speedWindowMs) {
                tracker.speedSamples.shift();
            }
            if (tracker.speedSamples.length >= 2) {
                const first = tracker.speedSamples[0];
                const last = tracker.speedSamples[tracker.speedSamples.length - 1];
                const dtSec = (last.t - first.t) / 1000;
                if (dtSec > 0) speedBps = (last.loaded - first.loaded) / dtSec;
            }
            if (speedBps > 0) {
                etaSec = Math.round((event.total - event.loaded) / speedBps);
            }
        }

        const message = `${formatBytes(event.loaded)} / ${formatBytes(event.total)}` +
                (speedBps > 0 ? ` · ${formatBytes(speedBps)}/s` : '') +
                (etaSec >= 0 ? ` · 약 ${formatDuration(etaSec)} 남음` : '');

        return { shouldRender: true, percent, message, speedBps, etaSec };
    }

    function startXhrUpload(opts) {
        const {
            uploadUrl,
            uploadToken,
            formData,
            onStart,
            onUploadProgress,
            onUploadLoad,
            onSuccess,
            onHttpError,
            onNetworkError,
            onAbort
        } = opts;

        if (typeof onStart === 'function') onStart();

        const xhr = new XMLHttpRequest();
        xhr.open('POST', uploadUrl, true);
        if (uploadToken) xhr.setRequestHeader('X-Upload-Token', uploadToken);

        xhr.upload.addEventListener('progress', event => {
            if (typeof onUploadProgress === 'function') onUploadProgress(event, xhr);
        });
        xhr.upload.addEventListener('load', () => {
            if (typeof onUploadLoad === 'function') onUploadLoad(xhr);
        });
        xhr.addEventListener('load', () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                let body = {};
                try { body = JSON.parse(xhr.responseText || '{}'); } catch (_) { /* ignore */ }
                if (typeof onSuccess === 'function') onSuccess(body, xhr);
                return;
            }
            // S4 — 응답 body 전체를 호출자에 전달해 FormError.renderResponse 가 fieldErrors 매핑 가능하게 한다.
            let body = null;
            try { body = JSON.parse(xhr.responseText || '{}'); } catch (_) { /* ignore */ }
            const message = (body && body.message) || ('HTTP ' + xhr.status);
            if (typeof onHttpError === 'function') onHttpError(message, xhr, body || { message });
        });
        xhr.addEventListener('error', () => {
            if (typeof onNetworkError === 'function') onNetworkError(xhr);
        });
        xhr.addEventListener('abort', () => {
            if (typeof onAbort === 'function') onAbort(xhr);
        });

        xhr.send(formData);
        return xhr;
    }

    function resolveCommonFields(form) {
        return {
            name: valueOf(form, 'input[name="name"]'),
            version: valueOf(form, 'input[name="version"]'),
            targetDirectory: valueOf(form, 'input[name="targetDirectory"]'),
            description: valueOf(form, 'textarea[name="description"]'),
            allowCreateDirectory: checkedValueOf(form, 'input[name="allowCreateDirectory"]'),
            entrypointRelativePath: valueOf(form, 'input[name="entrypointRelativePath"]')
        };
    }

    function valueOf(form, selector) {
        const el = form.querySelector(selector);
        return el ? (el.value || '') : '';
    }

    function checkedValueOf(form, selector) {
        const el = form.querySelector(selector);
        return !!(el && el.checked);
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

    window.BundleUploadShell = {
        collectSizeInfo,
        validateFolderWrapping,
        requestIntent,
        buildBundleFormData,
        resolveCommonFields,
        createUploadProgressTracker,
        describeUploadProgress,
        formatBytes,
        formatDuration,
        startXhrUpload
    };
})();
