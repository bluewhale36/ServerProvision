/* ============================================================
   S5-6-2 — 자원 복구 확인 modal handler (lazy-load 흐름).
   호출측 : form[data-confirm-restore] + data-resource-label [+ data-resource-extra]
   cascade 옵션 : form 에 data-cascade-true-title (필수) + data-cascade-true-desc (선택) 있으면
                  modal 내 라디오 slot 노출 + form 의 hidden cascade input 자동 채움.
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE_PLAIN = '{resource} 을(를) 복구할까요?';
    const TEMPLATE_CASCADE = '{resource} 을(를) 복구할까요? 하위 자원도 함께 복구할지 선택해주세요.';
    const DEFAULT_MSG = '이 자원을 복구할까요?';

    function lazyUrl(form) {
        const resourceType = form.getAttribute('data-resource-type') || 'OS_IMAGE';
        const resourceId   = form.getAttribute('data-resource-id') || '0';
        return '/ui/confirm-modal/RESTORE'
                + '?resourceType=' + encodeURIComponent(resourceType)
                + '&resourceId=' + encodeURIComponent(resourceId);
    }

    function writeHiddenCascade(form, cascadeValue) {
        let hidden = form.querySelector('input[name="cascade"]');
        if (!hidden) {
            hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = 'cascade';
            form.appendChild(hidden);
        }
        hidden.value = cascadeValue;
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-restore', function (form) {
            const cascadeTitle = form.getAttribute('data-cascade-true-title');
            const cascadeDesc = form.getAttribute('data-cascade-true-desc');
            const hasCascade = !!cascadeTitle;
            const template = hasCascade ? TEMPLATE_CASCADE : TEMPLATE_PLAIN;
            const message = ConfirmModal.composeMessage(form, template, DEFAULT_MSG);

            let cascadeWrap, cascadeTrueRadio;
            ConfirmModal.openLazy(lazyUrl(form), {
                afterInject: ({ modal, messageEl }) => {
                    if (messageEl) messageEl.textContent = message;
                    cascadeWrap = modal.querySelector('[data-modal-cascade-wrap]');
                    cascadeTrueRadio = modal.querySelector('[data-modal-cascade-true]');
                    if (hasCascade && cascadeWrap) {
                        const titleEl = modal.querySelector('[data-modal-cascade-true-title]');
                        const descEl = modal.querySelector('[data-modal-cascade-true-desc]');
                        if (titleEl) titleEl.textContent = cascadeTitle;
                        if (descEl) descEl.textContent = cascadeDesc || '';
                        cascadeWrap.hidden = false;
                    }
                    return null;
                },
                beforeConfirm: () => {
                    if (hasCascade) {
                        const value = (cascadeTrueRadio && cascadeTrueRadio.checked) ? 'true' : 'false';
                        writeHiddenCascade(form, value);
                    }
                    return true;
                },
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    });
})();
