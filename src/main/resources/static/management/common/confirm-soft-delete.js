/* ============================================================
   S5-2 — 자원 soft-delete 확인 modal handler.
   호출측 : form[data-confirm-soft-delete] + data-resource-label [+ data-resource-extra]
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE = '{resource} 을(를) 삭제할까요?';
    const DEFAULT_MSG = '이 자원을 삭제할까요?';

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-soft-delete', function (form) {
            ConfirmModal.open('confirmSoftDelete', {
                title: '자원 삭제',
                message: ConfirmModal.composeMessage(form, TEMPLATE, DEFAULT_MSG),
                confirmLabel: '삭제',
                confirmClass: 'n-btn-outline-danger',
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    });
})();
