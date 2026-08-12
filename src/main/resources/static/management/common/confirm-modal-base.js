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

        // HF5 — 모든 정적 modal 메시지가 지나는 길목. 호출측이 어떻게 문구를 조립했든
        // 여기서 병기형 조사를 해소한다 (composeMessage 를 거치지 않는 호출도 함께 덮인다).
        const message = particles(opts.message || '');

        if (!modal || !confirmBtn || !cancelBtn) {
            console.warn(TAG, 'fragment 누락 — fallback native confirm. prefix=', prefix);
            if (window.confirm(message || '계속할까요?')) opts.onConfirm();
            return;
        }

        if (titleEl && opts.title) titleEl.textContent = opts.title;
        if (messageEl) messageEl.textContent = message;
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
        if (explicit) return particles(explicit);
        const resource = form.getAttribute('data-resource-label');
        if (resource && template) {
            let msg = template.replace('{resource}', resource);
            const extra = form.getAttribute('data-resource-extra');
            if (extra) msg += ' ' + extra;
            return particles(msg);
        }
        return particles(defaultMessage);
    }

    /**
     * HF5 — 병기형 조사('{resource} 을(를)' 등)를 앞 글자의 받침으로 해소하고 띄어쓰기를 정규화한다.
     * korean-particle.js 가 먼저 로드되지 않아도 문구가 깨지지 않도록 원문을 그대로 돌려준다.
     */
    function particles(text) {
        return (window.KoreanParticle && typeof text === 'string')
                ? window.KoreanParticle.resolve(text)
                : text;
    }

    /**
     * 사용자 확인 직후 전송. S10 이 기본값을 뒤집어 <b>fetch 제출이 기본</b>이다 — 거절 응답이
     * raw JSON 페이지로 노출되는 사고를 막기 위함이며, 종전의 {@code data-async-submit} opt-in 은
     * 마커를 빠뜨리면 그대로 사고가 되는 구조라 폐기했다.
     * <p>서버가 실패 시 뷰를 다시 렌더하는 폼({@code BindingResult})만 {@code data-native-submit} 으로
     * 빠져나가 native 재제출한다. 본 base 의 submit listener 가 두 번째 submit 을 통과시킨다.</p>
     * <p>전송 구현은 {@code window.FormSubmit.sendAsync} 하나뿐이다 — 전역 인터셉터와 본 경로가
     * 같은 구현을 공유해야 두 진입점의 동작이 갈라지지 않는다.</p>
     */
    function approveAndSubmit(form) {
        if (form.hasAttribute('data-native-submit')) {
            form.dataset.confirmApproved = '1';
            form.requestSubmit();
            return;
        }
        window.FormSubmit.sendAsync(form);
    }


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
        // MK4-4-2 — 확인 창은 세 계층이다(행위 · 대상 · 무슨 일). 제목과 보조 문단도 호출 시점에
        // 주입할 수 있어야 같은 fragment 로 종류마다 다른 행위를 말할 수 있다.
        const titleEl = modal.querySelector('[data-modal-title]');
        const noteEl = modal.querySelector('[data-modal-note]');
        const cancelEls = modal.querySelectorAll('[data-modal-cancel]');

        if (!confirmBtn) {
            console.warn(TAG, 'lazy modal 에 [data-modal-confirm] 부재.');
            slot.innerHTML = '';
            return;
        }

        if (opts.startDisabled) confirmBtn.disabled = true;

        // MK4-4-2 — 세 계층을 채운다. 값이 없으면 fragment 의 기본 문구를 그대로 두고,
        // 보조 문단은 비었을 때 아예 지운다 — 빈 문단이 남으면 여백만 벌어진다.
        if (titleEl && opts.title) titleEl.textContent = opts.title;
        if (messageEl && opts.message) messageEl.textContent = particles(opts.message);
        if (noteEl) {
            if (opts.note) noteEl.textContent = particles(opts.note);
            else noteEl.remove();
        }

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
