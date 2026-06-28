/* ============================================================
   S5-2 — 자원 상태 변경 modal 공통 base helper.
   ─────────────────────────────────────────────────────────────
   4 종 specialized modal (soft-delete / deprecate / restore / purge) 이 공유하는
   인프라. modal element 다루기 / submit 가로채기 / 메시지 합성 등 도메인-agnostic 로직.

   각 specialized JS 는 본 base 의 API 만 사용 — 자기 actionKey 문자열을 보유하지 않는다.
   호출측 form 의 boolean 마커 (data-confirm-soft-delete 등) 가 곧 action 식별자.
   ============================================================ */
(function () {
    'use strict';
    const TAG = '[confirm-modal-base]';

    /**
     * 공통 modal opener. prefix 별 element id 셋
     * ({prefix}Modal / Backdrop / Title / Message / ConfirmBtn / CancelBtn) 을 자동 wiring.
     *
     * @param {string} prefix              element id 접두사
     * @param {object} opts
     * @param {string}   opts.title        modal 제목
     * @param {string}   opts.message      본문 메시지
     * @param {string}   opts.confirmLabel 확인 버튼 텍스트
     * @param {string}   opts.confirmClass 확인 버튼 CSS class (n-btn 외)
     * @param {boolean=} opts.startDisabled 확인 버튼 초기 disabled
     * @param {function=} opts.afterOpen   modal 표시 직후 추가 wiring. {modal, confirmBtn, cancelBtn} 받음. cleanup 반환 (close 시 호출).
     * @param {function=} opts.beforeConfirm 확인 클릭 시 onConfirm 전에 호출. false 반환 시 close 안 됨.
     * @param {function}  opts.onConfirm   확인 클릭 + close 후 호출.
     */
    function open(prefix, opts) {
        const modal = document.getElementById(prefix + 'Modal');
        const titleEl = document.getElementById(prefix + 'Title');
        const messageEl = document.getElementById(prefix + 'Message');
        const confirmBtn = document.getElementById(prefix + 'ConfirmBtn');
        const cancelBtn = document.getElementById(prefix + 'CancelBtn');
        const backdrop = document.getElementById(prefix + 'Backdrop');

        if (!modal || !confirmBtn || !cancelBtn) {
            console.warn(TAG, 'fragment 누락 — fallback native confirm. prefix=', prefix);
            if (window.confirm(opts.message || '계속할까요?')) opts.onConfirm();
            return;
        }

        if (titleEl && opts.title) titleEl.textContent = opts.title;
        if (messageEl) messageEl.textContent = opts.message || '';
        confirmBtn.textContent = opts.confirmLabel;
        confirmBtn.className = 'n-btn ' + (opts.confirmClass || 'n-btn-primary');
        confirmBtn.disabled = !!opts.startDisabled;

        let extraCleanup = null;
        if (opts.afterOpen) {
            extraCleanup = opts.afterOpen({modal, confirmBtn, cancelBtn});
        }

        modal.hidden = false;
        // afterOpen 측이 별도 focus 를 잡지 않으면 confirm 버튼 default focus.
        if (!opts.suppressDefaultFocus) confirmBtn.focus();

        const close = () => {
            modal.hidden = true;
            confirmBtn.onclick = null;
            cancelBtn.onclick = null;
            if (backdrop) backdrop.onclick = null;
            document.removeEventListener('keydown', onKey);
            if (typeof extraCleanup === 'function') extraCleanup();
        };
        const onKey = (ev) => {
            if (ev.key === 'Escape') close();
        };

        confirmBtn.onclick = () => {
            if (opts.beforeConfirm && opts.beforeConfirm() === false) return;
            close();
            opts.onConfirm();
        };
        cancelBtn.onclick = close;
        if (backdrop) backdrop.onclick = close;
        document.addEventListener('keydown', onKey);
    }

    /**
     * form[markerAttr] 마다 submit 가로채기 등록. 두 번째 submit (사용자 확인 후 requestSubmit) 은 통과.
     * @param {string}   markerAttr - 'data-confirm-soft-delete' 같은 form 식별자.
     * @param {function} handler    - (form) => void. modal 표시 + 확인 시 approveAndSubmit 호출.
     */
    function bindFormSubmit(markerAttr, handler) {
        document.querySelectorAll('form[' + markerAttr + ']').forEach(form => {
            form.addEventListener('submit', e => {
                if (form.dataset.confirmApproved === '1') {
                    delete form.dataset.confirmApproved;
                    return;
                }
                e.preventDefault();
                e.stopPropagation();
                // delete-reject-modal 등 다른 capture listener 가 동시에 fetch() 쏘지 않도록 즉시 차단.
                e.stopImmediatePropagation();
                handler(form);
            }, true);
        });
    }

    /**
     * '{resource}' placeholder 를 data-resource-label 로 치환 + data-resource-extra 합성.
     * data-confirm-message 명시 override 도 지원 (backward compat).
     */
    function composeMessage(form, template, defaultMessage) {
        const explicit = form.getAttribute('data-confirm-message');
        if (explicit) return explicit;
        const resource = form.getAttribute('data-resource-label');
        if (resource && template) {
            let msg = template.replace('{resource}', resource);
            const extra = form.getAttribute('data-resource-extra');
            if (extra) msg += ' ' + extra;
            return msg;
        }
        return defaultMessage;
    }

    /**
     * 사용자 확인 직후 form 재제출. 본 base 의 submit listener 가 두 번째 submit 을 통과시킴.
     * <p>form 에 {@code data-async-submit} 마커가 있으면 native navigation 대신 fetch 로 보내고
     * 응답을 {@code window.AsyncSubmitResult} 에 위임한다 — 거절 응답이 raw JSON 페이지로
     * 노출되는 사고를 막기 위함 (휴지통 등 일부 페이지 전용).</p>
     */
    function approveAndSubmit(form) {
        if (form.hasAttribute('data-async-submit')) {
            submitAsync(form);
            return;
        }
        form.dataset.confirmApproved = '1';
        form.requestSubmit();
    }

    async function submitAsync(form) {
        const method = (form.getAttribute('method') || 'POST').toUpperCase();
        const action = form.getAttribute('action') || window.location.pathname;
        const fd = new FormData(form);
        let url = action;
        let body = fd;
        if (method === 'GET') {
            const qs = new URLSearchParams(fd).toString();
            url = action + (action.includes('?') ? '&' : '?') + qs;
            body = null;
        }
        const handler = window.AsyncSubmitResult || defaultAsyncResult;
        let resp;
        try {
            resp = await fetch(url, {
                method,
                body,
                headers: {'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json,text/html;q=0.9,*/*;q=0.5'},
                redirect: 'follow'
            });
        } catch (err) {
            handler.onNetworkError(form, err);
            return;
        }
        if (resp.ok || resp.redirected) {
            handler.onSuccess(form, resp);
            return;
        }
        let payload = null;
        try {
            const ct = resp.headers.get('content-type') || '';
            if (ct.includes('application/json')) payload = await resp.json();
            else payload = {message: (await resp.text()).slice(0, 500)};
        } catch (_) {
            payload = null;
        }
        handler.onRejected(form, resp.status, payload);
    }

    const defaultAsyncResult = {
        onSuccess: () => window.location.reload(),
        onRejected: (_form, status, payload) => {
            const msg = (payload && payload.message) || ('요청이 거절되었어요. (HTTP ' + status + ')');
            ErrorModal.show({message: msg, status: status});
        },
        onNetworkError: () => ErrorModal.show({message: '서버와 통신할 수 없어요.', status: 0})
    };

    /**
     * S5-6-1 — modal lazy-load 흐름. fetchUrl 로 BE 에 fragment 요청 → #modalLazySlot 에 inject →
     * data-modal-* 셀렉터로 wiring 후 표시.
     *
     * @param {string}  fetchUrl  GET 으로 fragment 받을 URL (예 : /ui/confirm-modal/PURGE?...)
     * @param {object}  opts
     * @param {boolean=} opts.startDisabled    확인 버튼 초기 disabled
     * @param {function=} opts.afterInject     modal 표시 직후 추가 wiring.
     *                                          {modal, confirmBtn, expectedEl, typedInput, messageEl} 받음.
     *                                          cleanup 반환 (close 시 호출).
     * @param {function=} opts.beforeConfirm   확인 클릭 시 onConfirm 전에 호출. false 반환 시 close 안 됨.
     * @param {function}  opts.onConfirm       확인 클릭 + close 후 호출.
     * @param {function=} opts.onError         fetch / inject 실패 시 콜백.
     */
    async function openLazy(fetchUrl, opts) {
        let slot = document.getElementById('modalLazySlot');
        if (!slot) {
            // placeholder 누락 페이지 — 동적 생성.
            slot = document.createElement('div');
            slot.id = 'modalLazySlot';
            document.body.appendChild(slot);
        }

        let html;
        try {
            const resp = await fetch(fetchUrl, {
                headers: {'Accept': 'text/html', 'X-Requested-With': 'XMLHttpRequest'}
            });
            if (!resp.ok) {
                if (opts.onError) opts.onError(new Error('HTTP ' + resp.status));
                else ErrorModal.show({message: 'modal 을 불러오지 못했어요. (HTTP ' + resp.status + ')', status: resp.status});
                return;
            }
            html = await resp.text();
        } catch (err) {
            if (opts.onError) opts.onError(err);
            else ErrorModal.show({message: '서버와 통신할 수 없어요.', status: 0});
            return;
        }

        slot.innerHTML = html;
        const modal = slot.querySelector('[data-modal-active]');
        if (!modal) {
            console.warn(TAG, 'lazy modal markup 에 [data-modal-active] 부재 — fragment 응답 확인.');
            slot.innerHTML = '';
            return;
        }
        const confirmBtn = modal.querySelector('[data-modal-confirm]');
        const expectedEl = modal.querySelector('[data-modal-expected]');
        const typedInput = modal.querySelector('[data-modal-typed-input]');
        const messageEl = modal.querySelector('[data-modal-message]');
        const cancelEls = modal.querySelectorAll('[data-modal-cancel]');

        if (!confirmBtn) {
            console.warn(TAG, 'lazy modal 에 [data-modal-confirm] 부재.');
            slot.innerHTML = '';
            return;
        }

        if (opts.startDisabled) confirmBtn.disabled = true;

        let extraCleanup = null;
        if (opts.afterInject) {
            extraCleanup = opts.afterInject({modal, confirmBtn, expectedEl, typedInput, messageEl});
        }

        const close = () => {
            confirmBtn.onclick = null;
            cancelEls.forEach(el => {
                el.onclick = null;
            });
            document.removeEventListener('keydown', onKey);
            if (typeof extraCleanup === 'function') extraCleanup();
            slot.innerHTML = '';
        };
        const onKey = (ev) => {
            if (ev.key === 'Escape') close();
        };

        confirmBtn.onclick = () => {
            if (opts.beforeConfirm && opts.beforeConfirm() === false) return;
            close();
            opts.onConfirm();
        };
        cancelEls.forEach(el => {
            el.onclick = close;
        });
        document.addEventListener('keydown', onKey);

        if (typedInput) {
            setTimeout(() => typedInput.focus(), 0);
        } else {
            confirmBtn.focus();
        }
    }

    window.ConfirmModal = {open, openLazy, bindFormSubmit, composeMessage, approveAndSubmit};
})();
