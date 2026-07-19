/* ============================================================
   HF4-1 — 휴지통 보존기간 연장 확인 modal handler (lazy-load 흐름, confirm-deprecate 패턴).
   호출측 : form[data-confirm-extend] + data-resource-label + data-extend-step-days
            [+ data-resource-extra]
   종전에는 연장 form 이 data-confirm-deprecate 를 차용해 라벨이 "Deprecated 표시" 로 뜨던 것의 해소.
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE = '{resource} 을(를) +{days}일 연장할까요?';
    const DEFAULT_MSG = '이 자원의 보존기간을 연장할까요?';

    function lazyUrl(form) {
        const resourceType = form.getAttribute('data-resource-type') || 'OS_ISO';
        const resourceId = form.getAttribute('data-resource-id') || '0';
        return '/ui/confirm-modal/EXTEND_TTL'
            + '?resourceType=' + encodeURIComponent(resourceType)
            + '&resourceId=' + encodeURIComponent(resourceId);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-extend', function (form) {
            // 가산 step 일수는 BE 뷰모델(extendStepDays)이 form data 속성으로 주입 — 운영 설정과 동기.
            const stepDays = form.getAttribute('data-extend-step-days') || '30';
            const template = TEMPLATE.replace('{days}', stepDays);
            const message = ConfirmModal.composeMessage(form, template, DEFAULT_MSG);
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
