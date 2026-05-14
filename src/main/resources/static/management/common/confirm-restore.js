/* ============================================================
   S5-2 — 자원 복구 확인 modal handler.
   호출측 : form[data-confirm-restore] + data-resource-label [+ data-resource-extra]
   cascade 옵션 : form 에 data-cascade-true-title (필수) + data-cascade-true-desc (선택) 있으면
                  라디오 slot 노출 + hidden cascade input 자동 채움.
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE_PLAIN = '{resource} 을(를) 복구할까요?';
    const TEMPLATE_CASCADE = '{resource} 을(를) 복구할까요? 하위 자원도 함께 복구할지 선택해주세요.';
    const DEFAULT_MSG = '이 자원을 복구할까요?';
    const PREFIX = 'confirmRestore';

    function fillCascadeSlot(form, cascadeTitle, cascadeDesc) {
        const wrap = document.getElementById(PREFIX + 'CascadeWrap');
        const trueRadio = document.getElementById(PREFIX + 'CascadeTrue');
        const falseRadio = document.getElementById(PREFIX + 'CascadeFalse');
        const titleEl = document.getElementById(PREFIX + 'CascadeTrueTitle');
        const descEl = document.getElementById(PREFIX + 'CascadeTrueDesc');
        if (!wrap || !trueRadio || !falseRadio) return false;
        if (titleEl) titleEl.textContent = cascadeTitle;
        if (descEl) descEl.textContent = cascadeDesc || '';
        trueRadio.checked = true;
        falseRadio.checked = false;
        wrap.hidden = false;
        return true;
    }

    function hideCascadeSlot() {
        const wrap = document.getElementById(PREFIX + 'CascadeWrap');
        if (wrap) wrap.hidden = true;
    }

    function writeHiddenCascade(form) {
        const trueRadio = document.getElementById(PREFIX + 'CascadeTrue');
        let hidden = form.querySelector('input[name="cascade"]');
        if (!hidden) {
            hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = 'cascade';
            form.appendChild(hidden);
        }
        hidden.value = trueRadio && trueRadio.checked ? 'true' : 'false';
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-confirm-restore', function (form) {
            const cascadeTitle = form.getAttribute('data-cascade-true-title');
            const cascadeDesc = form.getAttribute('data-cascade-true-desc');
            const hasCascade = !!cascadeTitle;
            const template = hasCascade ? TEMPLATE_CASCADE : TEMPLATE_PLAIN;

            ConfirmModal.open(PREFIX, {
                title: '자원 복구',
                message: ConfirmModal.composeMessage(form, template, DEFAULT_MSG),
                confirmLabel: '복구',
                confirmClass: 'n-btn-outline-info',
                afterOpen: () => {
                    if (hasCascade) {
                        fillCascadeSlot(form, cascadeTitle, cascadeDesc);
                    } else {
                        hideCascadeSlot();
                    }
                    return () => hideCascadeSlot();
                },
                beforeConfirm: () => {
                    if (hasCascade) writeHiddenCascade(form);
                    return true;
                },
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    });
})();
