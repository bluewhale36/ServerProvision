/* ============================================================
   S5-6-1 — 자원 영구 삭제 (purge) 확인 modal handler (lazy-load 흐름).
   호출측 : form[data-confirm-purge] + data-resource-type + data-resource-id
            + data-resource-label [+ data-resource-extra]

   S5-2 와 달리 typed-name expected value 는 form 에 없음 — modal trigger 시 BE 의
   /ui/confirm-modal/PURGE?resourceType=...&resourceId=... 가 fragment 안에 직접 박아 응답.
   사용자 입력은 modal 내 [data-modal-typed-input] vs [data-modal-expected] textContent 비교.
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE = '{resource} 을(를) 영구 삭제하려면 자원명을 정확히 입력하세요.';
    const DEFAULT_MSG = '아래 자원을 영구 삭제하려면 자원명을 정확히 입력하세요.';

    function writeHiddenTypedName(form, value) {
        let hidden = form.querySelector('input[name="typedName"]');
        if (!hidden) {
            hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = 'typedName';
            form.appendChild(hidden);
        }
        hidden.value = value;
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-purge', function (form) {
            const resourceType = form.getAttribute('data-resource-type');
            const resourceId   = form.getAttribute('data-resource-id');
            if (!resourceType || !resourceId) {
                console.warn('[confirm-purge] form 에 data-resource-type / data-resource-id 누락');
                return;
            }
            const url = '/ui/confirm-modal/PURGE'
                    + '?resourceType=' + encodeURIComponent(resourceType)
                    + '&resourceId=' + encodeURIComponent(resourceId);
            const composedMessage = ConfirmModal.composeMessage(form, TEMPLATE, DEFAULT_MSG);

            ConfirmModal.openLazy(url, {
                startDisabled: true,
                afterInject: ({ confirmBtn, expectedEl, typedInput, messageEl }) => {
                    // form 에서 받은 메시지 (resource-label 조립) 를 modal 의 message slot 에 주입.
                    if (messageEl) messageEl.textContent = composedMessage;
                    if (!expectedEl || !typedInput) return null;
                    const expectedName = (expectedEl.textContent || '').trim();
                    typedInput.value = '';
                    typedInput.placeholder = expectedName;
                    const onInput = () => {
                        confirmBtn.disabled = (typedInput.value !== expectedName);
                    };
                    typedInput.addEventListener('input', onInput);
                    return () => typedInput.removeEventListener('input', onInput);
                },
                beforeConfirm: () => {
                    const slot = document.getElementById('modalLazySlot');
                    const typedInput = slot && slot.querySelector('[data-modal-typed-input]');
                    if (typedInput) writeHiddenTypedName(form, typedInput.value);
                    return true;
                },
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    });
})();
