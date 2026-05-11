/* ============================================================
   S5-2-1 — 자원 상태 변경 (soft-delete / deprecate / restore) 2차 확인 modal.
   ─────────────────────────────────────────────────────────────
   form 의 `data-confirm-action="<actionKey>"` 속성을 감지해 submit 을 가로채
   modal 을 띄운다. 사용자 확인 시 requestSubmit() 으로 통과 (delete-reject-modal
   같은 후속 submit listener 도 그대로 동작).

   actionKey :
     - soft-delete : 일반 삭제 (휴지통 이동). danger 버튼.
     - deprecate   : Deprecated 표시. warning 버튼.
     - restore     : 복구. info 버튼.

   message :
     - form 에 data-confirm-message 가 있으면 그 텍스트.
     - 없으면 actionKey 별 default 메시지.

   사용 :
     window.ConfirmActionModal.bind({ modalPrefix: 'confirmAction' });

   책임 분리 : MK3-2 의 delete-reject-modal (백엔드 409 응답 자동 modal) 과는 별개.
   본 modal 은 사용자 클릭 직후 확인용. 두 modal 은 sequence : confirm-action →
   submit → 409 → delete-reject-modal.
*/
(function () {
    const TAG = '[confirm-action]';

    // confirmClass : miller 의 form submit 버튼과 동일한 클래스로 맞춰 디자인 일관성 보장.
    //   - soft-delete : n-btn-outline-danger (모든 5 도메인 동일)
    //   - deprecate   : n-btn-outline-warning
    //   - restore     : n-btn-outline-info
    const ACTION_CONFIG = {
        'soft-delete': {
            title: '자원 삭제',
            defaultMessage: '이 자원을 삭제할까요?',
            confirmLabel: '삭제',
            confirmClass: 'n-btn-outline-danger'
        },
        'deprecate': {
            title: 'Deprecated 표시',
            defaultMessage: '이 자원을 Deprecated 로 표시할까요?',
            confirmLabel: 'Deprecated 표시',
            confirmClass: 'n-btn-outline-warning'
        },
        'restore': {
            title: '자원 복구',
            defaultMessage: '이 자원을 복구할까요?',
            confirmLabel: '복구',
            confirmClass: 'n-btn-outline-info'
        }
    };

    function bind(opts) {
        const prefix = (opts && opts.modalPrefix) || 'confirmAction';
        const modal  = document.getElementById(prefix + 'Modal');
        if (!modal) {
            console.warn(TAG, 'modal element not found :', prefix + 'Modal');
            return;
        }
        const forms = document.querySelectorAll('form[data-confirm-action]');
        if (forms.length === 0) return;

        forms.forEach(form => {
            // capture phase + 다른 listener (delete-reject-modal 등) 보다 먼저 등록되어야 함.
            form.addEventListener('submit', e => handleSubmit(e, form, prefix), true);
        });
    }

    function handleSubmit(e, form, prefix) {
        // 두 번째 submit (사용자 확인 후 requestSubmit) 은 그대로 통과.
        if (form.dataset.confirmActionApproved === '1') {
            delete form.dataset.confirmActionApproved;  // 재사용 대비 flag 초기화.
            return;
        }
        e.preventDefault();
        e.stopPropagation();
        // delete-reject-modal 같은 다른 capture listener 가 동시에 fetch() 를 쏘지 않도록 즉시 차단.
        e.stopImmediatePropagation();

        const actionKey = form.getAttribute('data-confirm-action');
        const config = ACTION_CONFIG[actionKey];
        if (!config) {
            console.warn(TAG, 'unknown actionKey :', actionKey);
            // fallback — native confirm 으로 사용자 보호.
            if (window.confirm(form.getAttribute('data-confirm-message') || '계속할까요?')) {
                form.dataset.confirmActionApproved = '1';
                form.requestSubmit();
            }
            return;
        }
        const message = form.getAttribute('data-confirm-message') || config.defaultMessage;

        openModal(prefix, config, message, () => {
            form.dataset.confirmActionApproved = '1';
            form.requestSubmit();
        });
    }

    function openModal(prefix, config, message, onConfirm) {
        const modal      = document.getElementById(prefix + 'Modal');
        const titleEl    = document.getElementById(prefix + 'Title');
        const messageEl  = document.getElementById(prefix + 'Message');
        const confirmBtn = document.getElementById(prefix + 'ConfirmBtn');
        const cancelBtn  = document.getElementById(prefix + 'CancelBtn');
        const closeBtn   = document.getElementById(prefix + 'CloseBtn');
        const backdrop   = document.getElementById(prefix + 'Backdrop');

        if (!modal || !confirmBtn || !cancelBtn) {
            // fragment 누락 fallback. 사용자 보호 — native confirm.
            if (window.confirm(message)) onConfirm();
            return;
        }

        if (titleEl) titleEl.textContent = config.title;
        if (messageEl) messageEl.textContent = message;
        confirmBtn.textContent = config.confirmLabel;
        confirmBtn.className = 'n-btn ' + config.confirmClass;

        modal.hidden = false;
        confirmBtn.focus();

        const close = () => {
            modal.hidden = true;
            confirmBtn.onclick = null;
            cancelBtn.onclick = null;
            if (closeBtn) closeBtn.onclick = null;
            if (backdrop) backdrop.onclick = null;
            document.removeEventListener('keydown', onKey);
        };
        const onKey = (ev) => { if (ev.key === 'Escape') close(); };

        confirmBtn.onclick = () => { close(); onConfirm(); };
        cancelBtn.onclick = close;
        if (closeBtn) closeBtn.onclick = close;
        if (backdrop) backdrop.onclick = close;
        document.addEventListener('keydown', onKey);
    }

    window.ConfirmActionModal = { bind };
})();
