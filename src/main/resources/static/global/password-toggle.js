/* 비밀번호 칸 보기 토글 + 입력 상태 경고(2026-09-16 · 최초 관리자 등록에서 한/영 전환 · Caps Lock 으로 의도와 다른 비밀번호가
 * 저장된 사고의 정정). 규칙은 화면이 아니라 서버가 갖는다 — 비밀번호 규칙은 ASCII 만 허용(PasswordRule)하므로 영문이 아닌 글자가
 * 들어오면 그 자리에서 알린다. 마크업 계약:
 *   <button type="button" data-password-toggle="<input id>">보기</button>  — 클릭마다 type 을 password ↔ text 로 바꾼다
 *   <input type="password" data-password-watch>                            — Caps Lock · 비 ASCII 입력을 감시한다
 *   같은 .n-form-group 안의 [data-password-hint]                              — 경고 문구 자리(비어 있으면 hidden)
 */
(function () {
    'use strict';

    function bindToggle(button) {
        const input = document.getElementById(button.dataset.passwordToggle);
        if (!input) return;
        button.addEventListener('click', function () {
            const reveal = input.type === 'password';
            input.type = reveal ? 'text' : 'password';
            button.textContent = reveal ? '숨기기' : '보기';
            button.setAttribute('aria-pressed', String(reveal));
            input.focus();
        });
    }

    function bindWatch(input) {
        const group = input.closest('.n-form-group');
        const hint = group ? group.querySelector('[data-password-hint]') : null;
        if (!hint) return;
        let capsLock = false;

        function render() {
            const value = input.value || '';
            const messages = [];
            if (capsLock) messages.push('Caps Lock 이 켜져 있습니다.');
            if (/[^\x20-\x7E]/.test(value)) messages.push('영문이 아닌 글자(한글 등)가 들어 있습니다 — 한/영 전환을 확인하십시오.');
            hint.textContent = messages.join(' ');
            hint.hidden = messages.length === 0;
        }

        function onKey(e) {
            if (typeof e.getModifierState === 'function') capsLock = e.getModifierState('CapsLock');
            render();
        }

        input.addEventListener('keydown', onKey);
        input.addEventListener('keyup', onKey);
        input.addEventListener('input', render);
        input.addEventListener('blur', function () { capsLock = false; render(); });
    }

    document.querySelectorAll('[data-password-toggle]').forEach(bindToggle);
    document.querySelectorAll('input[data-password-watch]').forEach(bindWatch);
})();
