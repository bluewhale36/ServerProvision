/**
 * ISO 오펀 복구 modal 공통 모듈.
 *
 * 사용법:
 *   OrphanRecovery.open(recoveryId, {
 *       prefix: 'orphan',          // modal element id 접두사 (기본 'orphan')
 *       afterRetry: (body) => {},  // 재시도 성공 후. 생략 시 body.redirect 로 이동
 *       afterDiscard: () => {},    // 폐기 성공 후 (예: durable 페이지 reload)
 *       afterLater: () => {},      // [나중에] 로 닫은 후
 *       onError: (msg) => {}       // 상세 조회 실패 등. 생략 시 bgjobToast
 *   });
 *
 * fragments/management/orphan-recovery-modal.html (prefix='orphan') 의 element id 를 그대로 잡는다.
 * 서버 endpoint: GET /maintenance/quarantine/{id} · POST .../retry · POST .../discard?typedName=.
 */
(function () {
    'use strict';

    const BASE = '/maintenance/quarantine';

    function el(prefix, suffix) {
        return document.getElementById(prefix + suffix);
    }

    function toast(message, variant) {
        if (typeof window.bgjobToast === 'function') {
            window.bgjobToast(message, {variant: variant || 'info'});
        }
    }

    // fetch 응답을 {ok, status, body} 로 정규화. 204 No Content 는 빈 body.
    function readResponse(resp) {
        if (resp.status === 204) return Promise.resolve({ok: resp.ok, status: 204, body: {}});
        return resp.json().catch(() => ({}))
            .then(body => ({ok: resp.ok, status: resp.status, body: body}));
    }

    function open(recoveryId, options) {
        options = options || {};
        const prefix = options.prefix || 'orphan';
        const modal = el(prefix, 'Modal');
        if (!modal) {
            console.error('[OrphanRecovery] modal element 없음:', prefix + 'Modal');
            if (options.onError) options.onError('복구 모달을 찾을 수 없습니다.');
            else toast('복구 모달을 찾을 수 없습니다.', 'error');
            return;
        }

        fetch(BASE + '/' + encodeURIComponent(recoveryId), {headers: {'Accept': 'application/json'}})
            .then(readResponse)
            .then(({ok, status, body}) => {
                if (!ok) {
                    // 404 = 이미 해소(복구/폐기)됐거나 TTL reaper 가 정리한 경우.
                    const msg = status === 404
                        ? '이미 처리되었거나 만료된 격리 항목입니다.'
                        : (body && body.message) || ('격리 정보를 불러오지 못했습니다 (HTTP ' + status + ')');
                    if (options.onError) options.onError(msg);
                    else toast(msg, 'error');
                    return;
                }
                render(prefix, modal, recoveryId, body, options);
            })
            .catch(err => {
                const msg = '격리 정보를 불러오지 못했습니다: ' + (err.message || err);
                if (options.onError) options.onError(msg);
                else toast(msg, 'error');
            });
    }

    function render(prefix, modal, recoveryId, data, options) {
        const expectedName = data.originalFilename || '';
        setText(el(prefix, 'Filename'), expectedName || '(이름 없음)');
        setText(el(prefix, 'Path'), data.resolvedPath || '');
        setText(el(prefix, 'Reason'), data.failureReason || '알 수 없는 오류');

        const wrap = el(prefix, 'TypedNameWrap');
        const input = el(prefix, 'TypedName');
        const expectedEl = el(prefix, 'TypedNameExpected');
        const retryBtn = el(prefix, 'RetryBtn');
        const discardBtn = el(prefix, 'DiscardBtn');
        const laterBtn = el(prefix, 'LaterBtn');
        const backdrop = el(prefix, 'Backdrop');

        // 상태 초기화 (재진입 대비)
        let discardArmed = false;
        if (wrap) wrap.hidden = true;
        if (input) input.value = '';
        if (expectedEl) setText(expectedEl, expectedName);
        if (discardBtn) {
            discardBtn.textContent = '폐기…';
            discardBtn.disabled = false;
            discardBtn.classList.remove('n-btn-danger');
            discardBtn.classList.add('n-btn-outline-danger');
        }
        if (retryBtn) retryBtn.disabled = false;

        function close() {
            modal.hidden = true;
        }

        function lockButtons(locked) {
            if (retryBtn) retryBtn.disabled = locked;
            if (discardBtn) discardBtn.disabled = locked;
            if (laterBtn) laterBtn.disabled = locked;
        }

        const onLater = () => {
            close();
            if (options.afterLater) options.afterLater();
        };
        if (laterBtn) laterBtn.onclick = onLater;
        if (backdrop) backdrop.onclick = onLater;

        if (retryBtn) retryBtn.onclick = () => {
            lockButtons(true);
            fetch(BASE + '/' + encodeURIComponent(recoveryId) + '/retry',
                {method: 'POST', headers: {'Accept': 'application/json'}})
                .then(readResponse)
                .then(({ok, status, body}) => {
                    if (!ok) throw new Error((body && body.message) || ('HTTP ' + status));
                    close();
                    toast('등록을 다시 시작했습니다.', 'info');
                    if (options.afterRetry) options.afterRetry(body);
                    else if (body && body.redirect) window.location.href = body.redirect;
                })
                .catch(err => {
                    lockButtons(false);
                    toast('재시도 실패: ' + (err.message || err), 'error');
                });
        };

        if (discardBtn) discardBtn.onclick = () => {
            if (!discardArmed) {
                // 1차 클릭 — typed-name 노출, 버튼을 위험 확정 상태로 전환 (이름 일치 전까지 비활성).
                discardArmed = true;
                if (wrap) wrap.hidden = false;
                discardBtn.textContent = '폐기 확정';
                discardBtn.classList.remove('n-btn-outline-danger');
                discardBtn.classList.add('n-btn-danger');
                discardBtn.disabled = true;
                if (input) input.focus();
                return;
            }
            const typed = input ? input.value : '';
            lockButtons(true);
            const url = BASE + '/' + encodeURIComponent(recoveryId)
                + '/discard?typedName=' + encodeURIComponent(typed);
            fetch(url, {method: 'POST', headers: {'Accept': 'application/json'}})
                .then(readResponse)
                .then(({ok, status, body}) => {
                    if (!ok) throw new Error((body && body.message) || ('HTTP ' + status));
                    close();
                    toast('격리된 파일을 폐기했습니다.', 'success');
                    if (options.afterDiscard) options.afterDiscard();
                })
                .catch(err => {
                    lockButtons(false);
                    toast('폐기 실패: ' + (err.message || err), 'error');
                });
        };

        if (input) input.oninput = () => {
            if (!discardArmed || !discardBtn) return;
            discardBtn.disabled = input.value !== expectedName;
        };

        modal.hidden = false;
    }

    function setText(node, text) {
        if (node) node.textContent = text;
    }

    window.OrphanRecovery = {open: open};
})();
