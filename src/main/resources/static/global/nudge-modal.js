/**
 * MK2 — nudge modal 공통 처리 모듈.
 *
 * 사용법:
 *   NudgeModal.handle(payload, {
 *       baseUrl: '/management/os/image-nudge',  // confirm endpoint prefix
 *       listUrl: '/management/os',              // 종결 후 redirect 기본값
 *       toastKey: 'os.image.toast',             // sessionStorage 키 (선택)
 *       onError: (msg) => { ... },              // 표시 콜백
 *       afterCancel: () => { ... }              // cancel 종결 후 콜백 (선택)
 *   });
 *
 * 본 함수는 fragments/management/nudge-modal.html (prefix='nudge') 의 element id 를 그대로 잡는다.
 * 다른 prefix 사용 시 options.prefix 로 지정 가능.
 *
 * confirm 엔드포인트 응답이 200 + { redirect } JSON 이면 그 URL 로 이동.
 * 204 No Content (cancel) 면 afterCancel 호출. 실패 시 onError 호출.
 */
(function () {
    'use strict';

    function escapeHtml(s) {
        return String(s == null ? '' : s)
                .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function el(prefix, suffix) {
        return document.getElementById(prefix + suffix);
    }

    function handle(payload, options) {
        options = options || {};
        const prefix = options.prefix || 'nudge';
        const modal = el(prefix, 'Modal');
        if (!modal) {
            console.error('[NudgeModal] element 없음:', prefix + 'Modal');
            if (options.onError) options.onError(payload.message || '동일한 자원이 이미 존재합니다.');
            return;
        }

        const baseUrl = options.baseUrl || modal.dataset.confirmBaseUrl;
        const list = el(prefix, 'ConflictsList');
        const proceedBtn = el(prefix, 'ProceedBtn');
        const replaceBtn = el(prefix, 'ReplaceBtn');
        const cancelBtn = el(prefix, 'CancelBtn');
        const backdrop = el(prefix, 'Backdrop');

        list.innerHTML = '';
        let selectedTargetId = null;
        replaceBtn.disabled = true;
        replaceBtn.title = '대체할 기존 자원을 목록에서 선택하세요';

        (payload.conflicts || []).forEach(c => {
            const li = document.createElement('li');
            li.style.padding = '10px 12px';
            li.style.borderBottom = '1px solid var(--n-border)';
            li.style.cursor = 'pointer';
            li.dataset.targetId = c.id;
            const hashFragment = c.hash ? (' · ' + escapeHtml(String(c.hash).slice(0, 16)) + '…') : '';
            li.innerHTML =
                    '<div style="font-weight: 600;">' + escapeHtml(c.name || '') + ' ' + escapeHtml(c.version || '') + '</div>' +
                    '<div style="font-size: 12px; color: var(--n-text-muted);">' +
                    'id=' + c.id + ' · ' + escapeHtml(c.state || '') + hashFragment + '</div>';
            li.addEventListener('click', () => {
                Array.from(list.children).forEach(item => item.style.background = '');
                li.style.background = 'var(--n-bg-soft, #eef)';
                selectedTargetId = c.id;
                replaceBtn.disabled = false;
                replaceBtn.removeAttribute('title');
            });
            list.appendChild(li);
        });

        modal.hidden = false;

        proceedBtn.onclick = () => confirmNudge(baseUrl, payload.nudgeId, 'proceed', null, options);
        replaceBtn.onclick = () => {
            if (selectedTargetId == null) return;
            confirmNudge(baseUrl, payload.nudgeId, 'replace', selectedTargetId, options);
        };
        const onCancel = () => confirmNudge(baseUrl, payload.nudgeId, 'cancel', null, options);
        cancelBtn.onclick = onCancel;
        backdrop.onclick = onCancel;
    }

    function confirmNudge(baseUrl, nudgeId, action, targetId, options) {
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
                    hide(options.prefix || 'nudge');
                    if (action === 'cancel') {
                        if (options.afterCancel) options.afterCancel();
                        else if (options.onError) options.onError('등록을 취소했습니다.');
                        return;
                    }
                    const redirect = (body && body.redirect) || options.listUrl || '/';
                    if (options.toastKey) {
                        try {
                            sessionStorage.setItem(options.toastKey,
                                    action === 'replace'
                                            ? '기존 자원을 영구 삭제하고 신규 자원을 등록했습니다.'
                                            : '신규 자원을 등록했습니다.');
                        } catch (_) { /* ignore */ }
                    }
                    window.location.href = redirect;
                })
                .catch(err => {
                    hide(options.prefix || 'nudge');
                    if (options.onError) options.onError(err.message || 'nudge 처리에 실패했습니다.');
                });
    }

    function hide(prefix) {
        const modal = el(prefix, 'Modal');
        if (modal) modal.hidden = true;
    }

    window.NudgeModal = { handle: handle, hide: hide };
})();
