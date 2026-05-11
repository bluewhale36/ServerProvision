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
     - purge (S5-2-2) : 영구 삭제. danger 버튼 + typed-name 입력 일치 시에만 활성.
     - restore-cascade (S5-2-3) : 부모 자원 복구 + 하위 자원 일괄 복구 옵션. 라디오 2.

   message :
     - form 에 data-confirm-message 가 있으면 그 텍스트.
     - 없으면 actionKey 별 default 메시지.

   purge 전용 form 속성 (S5-2-2) :
     - data-typed-name : 사용자가 정확히 입력해야 할 자원명
     - hidden input[name=typedName] : 확인 시 자동 채움 (백엔드 재검증 용)

   restore-cascade 전용 form 속성 (S5-2-3) :
     - data-cascade-true-title : 라디오의 "함께 복구" 항목 제목 (자식 카운트 포함)
     - data-cascade-true-desc  : 라디오의 "함께 복구" 항목 설명 (하위 자원 이름 나열)
     - hidden input[name=cascade] : 라디오 선택에 따라 'true'/'false' 자동 채움

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
        },
        'purge': {
            title: '자원 영구 삭제',
            defaultMessage: '아래 자원을 영구 삭제하려면 자원명을 정확히 입력하세요. 디스크의 파일도 함께 제거됩니다.',
            confirmLabel: '영구 삭제',
            confirmClass: 'n-btn-danger',
            requiresTypedName: true
        },
        'restore-cascade': {
            title: '자원 복구',
            defaultMessage: '자원을 복구할까요? 하위 자원도 함께 복구할지 선택해주세요.',
            confirmLabel: '복구',
            confirmClass: 'n-btn-outline-info',
            requiresCascadeRadio: true
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

        openModal(prefix, config, message, form, () => {
            form.dataset.confirmActionApproved = '1';
            form.requestSubmit();
        });
    }

    function openModal(prefix, config, message, form, onConfirm) {
        const modal      = document.getElementById(prefix + 'Modal');
        const titleEl    = document.getElementById(prefix + 'Title');
        const messageEl  = document.getElementById(prefix + 'Message');
        const confirmBtn = document.getElementById(prefix + 'ConfirmBtn');
        const cancelBtn  = document.getElementById(prefix + 'CancelBtn');
        const closeBtn   = document.getElementById(prefix + 'CloseBtn');
        const backdrop   = document.getElementById(prefix + 'Backdrop');
        const typedWrap  = document.getElementById(prefix + 'TypedTargetWrap');
        const typedTarg  = document.getElementById(prefix + 'TypedTarget');
        const typedInput = document.getElementById(prefix + 'TypedInput');
        const cascadeWrap = document.getElementById(prefix + 'CascadeWrap');
        const cascadeTrueRadio = document.getElementById(prefix + 'CascadeTrue');
        const cascadeFalseRadio = document.getElementById(prefix + 'CascadeFalse');
        const cascadeTrueTitle = document.getElementById(prefix + 'CascadeTrueTitle');
        const cascadeTrueDesc = document.getElementById(prefix + 'CascadeTrueDesc');

        if (!modal || !confirmBtn || !cancelBtn) {
            // fragment 누락 fallback. 사용자 보호 — native confirm.
            if (window.confirm(message)) onConfirm();
            return;
        }

        if (titleEl) titleEl.textContent = config.title;
        if (messageEl) messageEl.textContent = message;
        confirmBtn.textContent = config.confirmLabel;
        confirmBtn.className = 'n-btn ' + config.confirmClass;

        // S5-2-2 — purge actionKey 의 typed-name input slot.
        // 일치 시에만 확인 버튼 활성. 별도 시각 피드백 없이 disabled 토글만.
        let expectedName = null;
        const onInput = () => {
            if (expectedName != null && typedInput) {
                confirmBtn.disabled = (typedInput.value !== expectedName);
            }
        };
        if (config.requiresTypedName && typedWrap && typedTarg && typedInput) {
            expectedName = form.getAttribute('data-typed-name') || '';
            typedTarg.textContent = expectedName;
            typedInput.value = '';
            typedInput.placeholder = expectedName;
            typedWrap.hidden = false;
            confirmBtn.disabled = true;
            typedInput.addEventListener('input', onInput);
        } else if (typedWrap) {
            typedWrap.hidden = true;
            confirmBtn.disabled = false;
        }

        // S5-2-3 — restore-cascade actionKey 의 라디오 slot.
        // 'data-cascade-true-title' / 'data-cascade-true-desc' 로 항목 텍스트 채움. default = true.
        if (config.requiresCascadeRadio && cascadeWrap && cascadeTrueRadio && cascadeFalseRadio) {
            if (cascadeTrueTitle) {
                cascadeTrueTitle.textContent = form.getAttribute('data-cascade-true-title') || '하위 자원도 함께 복구';
            }
            if (cascadeTrueDesc) {
                cascadeTrueDesc.textContent = form.getAttribute('data-cascade-true-desc') || '';
            }
            cascadeTrueRadio.checked = true;
            cascadeFalseRadio.checked = false;
            cascadeWrap.hidden = false;
            confirmBtn.disabled = false;
        } else if (cascadeWrap) {
            cascadeWrap.hidden = true;
        }

        modal.hidden = false;
        // purge 시에는 input 포커스, 그 외는 confirm 버튼 포커스
        if (config.requiresTypedName && typedInput) {
            typedInput.focus();
        } else {
            confirmBtn.focus();
        }

        const close = () => {
            modal.hidden = true;
            confirmBtn.onclick = null;
            cancelBtn.onclick = null;
            if (closeBtn) closeBtn.onclick = null;
            if (backdrop) backdrop.onclick = null;
            if (typedInput) typedInput.removeEventListener('input', onInput);
            document.removeEventListener('keydown', onKey);
        };
        const onKey = (ev) => { if (ev.key === 'Escape') close(); };

        confirmBtn.onclick = () => {
            // purge 시 hidden typedName input 채우기
            if (config.requiresTypedName && typedInput) {
                let hidden = form.querySelector('input[name="typedName"]');
                if (!hidden) {
                    hidden = document.createElement('input');
                    hidden.type = 'hidden';
                    hidden.name = 'typedName';
                    form.appendChild(hidden);
                }
                hidden.value = typedInput.value;
            }
            // S5-2-3 — restore-cascade 시 hidden cascade input 채우기 (선택된 라디오 값)
            if (config.requiresCascadeRadio && cascadeTrueRadio) {
                let hidden = form.querySelector('input[name="cascade"]');
                if (!hidden) {
                    hidden = document.createElement('input');
                    hidden.type = 'hidden';
                    hidden.name = 'cascade';
                    form.appendChild(hidden);
                }
                hidden.value = cascadeTrueRadio.checked ? 'true' : 'false';
            }
            close();
            onConfirm();
        };
        cancelBtn.onclick = close;
        if (closeBtn) closeBtn.onclick = close;
        if (backdrop) backdrop.onclick = close;
        document.addEventListener('keydown', onKey);
    }

    window.ConfirmActionModal = { bind };
})();
