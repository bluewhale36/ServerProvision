/* ============================================================
   MA8 — 사용자 상세의 확인 모달.
   ─────────────────────────────────────────────────────────────
   권한 등급 변경 · 비활성화는 대상의 로그인 상태를 끝내므로(세션 만료) 제출 전에 그 사실을 알린다.
   마커 : form[data-confirm-user-role] · form[data-confirm-user-disable] + data-resource-label
   전송은 ConfirmModal.approveAndSubmit — 전역 인터셉터와 같은 구현(form-submit.js)이라 거절은 안내 모달로 수렴한다.
   (스크립트가 /users 아래가 아니라 /security 아래에 있는 이유: /users/{id} 컨트롤러 경로와 겹치면 정적 파일 요청이 상세 핸들러로 간다)
   ============================================================ */
(function () {
    'use strict';

    /** CP5 D-1 — 바꿀 등급을 고르면 [권한 등급 바꾸기] 를 풀고 '먼저 고르십시오' tooltip 을 끈다. */
    function wireLevelSubmit() {
        const button = document.querySelector('[data-level-submit]');
        if (!button) return;
        const wrap = button.closest('[data-level-submit-wrap]');
        button.form.addEventListener('change', function (e) {
            if (e.target.name !== 'level') return;
            button.disabled = false;
            if (wrap) wrap.setAttribute('data-tooltip-active', 'false');
        });
    }

    /**
     * CP7 — 초기 비밀번호 제출 전 확인. 조건을 못 맞추면 요청을 보내지 않고 그 칸에 서버와 같은 문장을 띄운다
     * (보내면 페이지 전체가 다시 그려져, 맨 아래 카드의 오류를 못 보고 반영된 것으로 오해했다).
     * 규칙 정규식 · 문구는 form 의 data 속성으로 서버 상수(PasswordRule · InitialPasswordRequest)를 받는다 — 복사본이 없다.
     * 서버 검증은 그대로 남는 안전망이다.
     */
    function wireInitialPassword() {
        const form = document.querySelector('form[data-initial-password-form]');
        if (!form) return;
        const rule = new RegExp(form.dataset.ruleRegex);
        const password = form.querySelector('[name="password"]');
        const retyped = form.querySelector('[name="retypedPassword"]');

        function show(input, message) {
            const slot = form.querySelector('[data-client-error="' + input.name + '"]');
            input.classList.toggle('is-invalid', !!message);
            if (!slot) return;
            slot.textContent = message || '';
            slot.hidden = !message;
        }

        function problems() {
            const p = password.value || '';
            const r = retyped.value || '';
            const result = {password: null, retypedPassword: null};
            if (!p.trim()) result.password = form.dataset.requiredMessage;
            else if (!rule.test(p)) result.password = form.dataset.ruleMessage;
            if (!r.trim()) result.retypedPassword = form.dataset.retypeRequiredMessage;
            else if (p.trim() && p !== r) result.retypedPassword = form.dataset.mismatchMessage;
            return result;
        }

        // 서버가 거절해 다시 그린 화면이면 실패 안내가 있는 이 카드를 화면에 들인다(포커스만으로는 안내가 화면 밖에 남았다)
        const failed = document.querySelector('[data-password-failed]');
        if (failed) {
            failed.closest('[data-password-section]').scrollIntoView({block: 'start'});
            const firstInvalid = form.querySelector('.is-invalid');
            if (firstInvalid) firstInvalid.focus({preventScroll: true});   // 스크롤은 위 한 번으로 — 포커스가 화면을 다시 끌어내리지 않게
        }

        form.addEventListener('submit', function (e) {
            const found = problems();
            show(password, found.password);
            show(retyped, found.retypedPassword);
            if (found.password || found.retypedPassword) {
                e.preventDefault();
                (found.password ? password : retyped).focus();
            }
        });
        // 고치는 동안에는 오류를 지우기만 한다 — 입력 중에 새 오류를 띄워 재촉하지 않는다
        [password, retyped].forEach(function (input) {
            input.addEventListener('input', function () { show(input, null); });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        wireLevelSubmit();
        wireInitialPassword();
        if (!window.ConfirmModal) return;

        ConfirmModal.bindFormSubmit('data-confirm-user-role', function (form) {
            const label = form.getAttribute('data-resource-label') || '이 사용자';
            const checked = form.querySelector('input[name="level"]:checked');
            const levelText = checked ? checked.parentElement.querySelector('.n-option-title').textContent.trim() : '';
            ConfirmModal.open('confirmUserAction', {
                title: '권한 등급 바꾸기',
                message: label + '의 권한 등급을 ' + levelText + '으로(로) 바꿀까요? 그 사용자는 즉시 로그아웃되고 다시 로그인해야 합니다.',
                confirmLabel: '바꾸기',
                confirmClass: 'n-btn-primary',
                onConfirm: function () { ConfirmModal.approveAndSubmit(form); }
            });
        });

        ConfirmModal.bindFormSubmit('data-confirm-user-disable', function (form) {
            const label = form.getAttribute('data-resource-label') || '이 사용자';
            ConfirmModal.open('confirmUserAction', {
                title: '계정 비활성화',
                message: label + '의 계정을 비활성화할까요? 그 사용자는 즉시 로그아웃되고 다시 로그인할 수 없습니다. 계정과 기록은 그대로 남고, 언제든 다시 활성화할 수 있습니다.',
                confirmLabel: '비활성화',
                confirmClass: 'n-btn-outline-danger',
                onConfirm: function () { ConfirmModal.approveAndSubmit(form); }
            });
        });
    });
})();
