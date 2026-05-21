/* ============================================================
   S5-6-2 — 자원 soft-delete 확인 modal handler (lazy-load 흐름).
   호출측 : form[data-confirm-soft-delete] + data-resource-label [+ data-resource-extra]

   S5-2 와 달리 modal markup 은 사전 include 가 아닌 BE 의 /ui/confirm-modal/SOFT_DELETE
   응답으로 lazy-load. 표시 메시지는 form 의 data-resource-label 로 JS 가 inject.
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE = '{resource} 을(를) 삭제할까요?';
    const DEFAULT_MSG = '이 자원을 삭제할까요?';

    function lazyUrl(form) {
        // S5-6-2 의 SOFT_DELETE 는 자원 lookup 불요 — dummy 값으로 endpoint signature 만 충족.
        const resourceType = form.getAttribute('data-resource-type') || 'OS_IMAGE';
        const resourceId   = form.getAttribute('data-resource-id') || '0';
        return '/ui/confirm-modal/SOFT_DELETE'
                + '?resourceType=' + encodeURIComponent(resourceType)
                + '&resourceId=' + encodeURIComponent(resourceId);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-soft-delete', function (form) {
            const message = ConfirmModal.composeMessage(form, TEMPLATE, DEFAULT_MSG);
            ConfirmModal.openLazy(lazyUrl(form), {
                afterInject: ({ messageEl }) => {
                    if (messageEl) messageEl.textContent = message;
                    return null;
                },
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    });
})();
