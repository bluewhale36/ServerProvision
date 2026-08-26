/* ============================================================
   U6 — 게스트 서버 회수 경고 · 영구 삭제 확인 modal 바인딩.
   ─────────────────────────────────────────────────────────
   관리 공용 confirm 모달 fragment 와 base opener(confirm-modal-base.js)를 재사용한다
   (setting-lifecycle.js 선례). 회수는 경고 확인만, 영구 삭제는 typed 입력(systemUUID 의
   마지막 '-' 다음 값 — 기대값 SSOT 는 GuestServer.systemUUIDSuffix, 화면은 data 속성으로 받는다)
   대조 후 hidden input 으로 서버 재검증에 넘긴다.
   호출측 form 마커 :
     · data-confirm-decommission : 경고 4문 고지 후 제출
     · data-confirm-server-purge : + data-typed-suffix(기대값)
   ============================================================ */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;

        // ── 성공 후 이동 — 영구 삭제는 보고 있던 상세가 사라지므로 서버 redirect 도착지(목록)를 따른다
        //    (setting-lifecycle.js · trash-action.js 의 페이지 로컬 override 선례).
        const base = window.AsyncSubmitResult;
        window.AsyncSubmitResult = {
            onSuccess: function (_form, resp) {
                const target = (resp && resp.url) ? resp.url : window.location.href;
                window.location.assign(target);
            },
            onRejected: function (form, status, payload) {
                if (base && base.onRejected) { base.onRejected(form, status, payload); return; }
                ErrorModal.show({message: (payload && payload.message) || ('요청이 거절되었어요. (HTTP ' + status + ')'), status: status});
            },
            onNetworkError: function (form, err) {
                if (base && base.onNetworkError) { base.onNetworkError(form, err); return; }
                ErrorModal.show({message: '서버와 통신할 수 없어요.', status: 0});
            }
        };

        // ── 회수 — 이후 영향 4가지를 고지하고 확인을 받는다(U6 D-6) ──
        ConfirmModal.bindFormSubmit('data-confirm-decommission', function (form) {
            ConfirmModal.open('serverDecommission', {
                title: '서버 회수',
                // .cm-message 는 white-space: pre-line — 문장마다 줄을 바꿔 네 가지 영향이 한눈에 읽히게 한다.
                message: '이 서버를 회수하면 다음과 같이 됩니다.\n'
                    + '① 목록에서 숨겨집니다 (\'회수된 서버 보기\' 로만 확인할 수 있습니다)\n'
                    + '② 세팅 정의서 할당 · 전원 조작 등이 막힙니다 (이름 · 메모 수정은 그대로 가능)\n'
                    + '③ 이 서버가 다시 PXE 부팅하면 새 서버로 등록됩니다\n'
                    + '④ 회수를 되돌리는 기능은 없습니다',
                confirmLabel: '회수',
                confirmClass: 'n-btn-danger',
                onConfirm: function () { ConfirmModal.approveAndSubmit(form); }
            });
        });

        // ── 영구 삭제 — typed 입력(suffix) 대조 후 서버 재검증용 hidden 으로 전달(U6 D-5) ──
        ConfirmModal.bindFormSubmit('data-confirm-server-purge', function (form) {
            const expected = form.getAttribute('data-typed-suffix') || '';
            ConfirmModal.open('serverPurge', {
                title: '서버 영구 삭제',
                message: '이 서버의 모든 기록(상세 · 이력 · 할당 · 그룹 소속)이 함께 삭제되며 되돌릴 수 없습니다. '
                    + '확인을 위해 systemUUID 의 마지막 \'-\' 다음 값을 입력하세요.',
                confirmLabel: '영구 삭제',
                confirmClass: 'n-btn-danger',
                startDisabled: true,
                suppressDefaultFocus: true,
                afterOpen: function (ctx) {
                    const wrap = document.getElementById('serverPurgeTypedTargetWrap');
                    const target = document.getElementById('serverPurgeTypedTarget');
                    const input = document.getElementById('serverPurgeTypedInput');
                    if (wrap) wrap.hidden = false;
                    if (target) target.textContent = expected;
                    if (!input) return null;
                    input.value = '';
                    input.placeholder = expected;
                    const onInput = function () {
                        // 서버 가드는 대소문자를 관대하게 대조한다 — 화면도 같은 기준(드리프트 0).
                        ctx.confirmBtn.disabled = (input.value.trim().toLowerCase() !== expected.toLowerCase());
                    };
                    input.addEventListener('input', onInput);
                    setTimeout(function () { input.focus(); }, 0);
                    return function () {
                        input.removeEventListener('input', onInput);
                        if (wrap) wrap.hidden = true;
                    };
                },
                beforeConfirm: function () {
                    const input = document.getElementById('serverPurgeTypedInput');
                    let hidden = form.querySelector('input[name="typedSuffix"]');
                    if (!hidden) {
                        hidden = document.createElement('input');
                        hidden.type = 'hidden';
                        hidden.name = 'typedSuffix';
                        form.appendChild(hidden);
                    }
                    hidden.value = input ? input.value : '';
                    return true;
                },
                onConfirm: function () { ConfirmModal.approveAndSubmit(form); }
            });
        });
    });
})();
