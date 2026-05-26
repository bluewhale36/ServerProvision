/* ============================================================
   R1-3 — 자원 undeprecate 확인 modal handler (lazy-load 흐름).
   호출측 : form[data-confirm-undeprecate] + data-resource-label [+ data-resource-extra]
   DEPRECATE 의 역동작에 대한 2 차 확인. confirm-deprecate.js 와 패턴 동일.
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE = '{resource} 의 Deprecated 표시를 해제할까요?';
    const DEFAULT_MSG = '이 자원의 Deprecated 표시를 해제할까요?';

    function lazyUrl(form) {
        const resourceType = form.getAttribute('data-resource-type') || 'OS_IMAGE';
        const resourceId = form.getAttribute('data-resource-id') || '0';
        return '/ui/confirm-modal/UNDEPRECATE'
            + '?resourceType=' + encodeURIComponent(resourceType)
            + '&resourceId=' + encodeURIComponent(resourceId);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-undeprecate', function (form) {
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
