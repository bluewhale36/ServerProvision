/* ============================================================
   S5-2 — 자원 deprecate 확인 modal handler.
   호출측 : form[data-confirm-deprecate] + data-resource-label [+ data-resource-extra]
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE = '{resource} 을(를) Deprecated 로 표시할까요?';
    const DEFAULT_MSG = '이 자원을 Deprecated 로 표시할까요?';

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-deprecate', function (form) {
            ConfirmModal.open('confirmDeprecate', {
                title: 'Deprecated 표시',
                message: ConfirmModal.composeMessage(form, TEMPLATE, DEFAULT_MSG),
                confirmLabel: 'Deprecated 표시',
                confirmClass: 'n-btn-outline-warning',
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    });
})();
