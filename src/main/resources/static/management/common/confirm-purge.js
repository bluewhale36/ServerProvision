/* ============================================================
   S5-2 — 자원 영구 삭제 (purge) 확인 modal handler.
   호출측 : form[data-confirm-purge] + data-typed-name + data-resource-label [+ data-resource-extra]
   typed-name input 과 일치 시에만 확인 버튼 활성. 일치 시 hidden typedName input 자동 채움.
   ============================================================ */
(function () {
    'use strict';
    const TEMPLATE = '{resource} 을(를) 영구 삭제하려면 자원명을 정확히 입력하세요.';
    const DEFAULT_MSG = '아래 자원을 영구 삭제하려면 자원명을 정확히 입력하세요.';
    const PREFIX = 'confirmPurge';

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
            const expectedName = form.getAttribute('data-typed-name') || '';
            const wrap = document.getElementById(PREFIX + 'TypedTargetWrap');
            const targetEl = document.getElementById(PREFIX + 'TypedTarget');
            const inputEl = document.getElementById(PREFIX + 'TypedInput');

            ConfirmModal.open(PREFIX, {
                title: '자원 영구 삭제',
                message: ConfirmModal.composeMessage(form, TEMPLATE, DEFAULT_MSG),
                confirmLabel: '영구 삭제',
                confirmClass: 'n-btn-danger',
                startDisabled: true,
                suppressDefaultFocus: true,
                afterOpen: ({ confirmBtn }) => {
                    if (!wrap || !targetEl || !inputEl) return null;
                    targetEl.textContent = expectedName;
                    inputEl.value = '';
                    inputEl.placeholder = expectedName;
                    wrap.hidden = false;
                    const onInput = () => { confirmBtn.disabled = (inputEl.value !== expectedName); };
                    inputEl.addEventListener('input', onInput);
                    setTimeout(() => inputEl.focus(), 0);
                    return () => {
                        inputEl.removeEventListener('input', onInput);
                        wrap.hidden = true;
                    };
                },
                beforeConfirm: () => {
                    if (inputEl) writeHiddenTypedName(form, inputEl.value);
                    return true;
                },
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    });
})();
