/* ============================================================
   S5-6-2 — 자원 deprecate 확인 modal handler (lazy-load 흐름).
   호출측 : form[data-confirm-deprecate] + data-resource-label [+ data-resource-extra]
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE = '{resource} 을(를) Deprecated 로 표시할까요?';
    const DEFAULT_MSG = '이 자원을 Deprecated 로 표시할까요?';

    function lazyUrl(form) {
        const resourceType = form.getAttribute('data-resource-type') || 'OS_IMAGE';
        const resourceId = form.getAttribute('data-resource-id') || '0';
        return '/ui/confirm-modal/DEPRECATE'
            + '?resourceType=' + encodeURIComponent(resourceType)
            + '&resourceId=' + encodeURIComponent(resourceId);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-deprecate', function (form) {
            const message = ConfirmModal.composeMessage(form, TEMPLATE, DEFAULT_MSG);
            ConfirmModal.openLazy(lazyUrl(form), {
                afterInject: ({messageEl}) => {
                    if (messageEl) messageEl.textContent = message;
                    return null;
                },
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    });
})();
