/* ============================================================
   U3-2-b — 세팅 정의서 삭제 lifecycle 확인 modal 바인딩.
   ─────────────────────────────────────────────────────────
   관리 공용 confirm 모달 fragment(confirm-soft-delete / confirm-purge) 와 base opener
   (confirm-modal-base.js 의 ConfirmModal.open) 를 재사용한다. 관리 도메인의 confirm-purge.js
   lazy 흐름(/ui/confirm-modal/PURGE)은 ResourceType/TypedNameVerifier 레지스트리를 전제하는데,
   세팅 정의서는 DB 전용 soft-delete(DEC-A)라 그 레지스트리에 없다. 그래서 lazy 대신 사전 include 된
   fragment 를 base opener 로 직접 구동하고, 영구삭제 확인용 정의서명은 이미 화면에 있는 값(form data 속성)을
   그대로 쓴다(서버 왕복 0).
   호출측 form 마커 :
     · data-confirm-soft-delete : + data-resource-label [+ data-referencing-count]
     · data-confirm-purge       : + data-resource-label + data-typed-name(정의서명)
   ============================================================ */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.ConfirmModal) return;

        // ── 성공 후 이동 지점 — 서버가 지정한 곳을 따른다 ──
        // 전역 기본 동작은 현재 페이지 재적재인데, 영구 삭제는 지금 보고 있는 정의서를 없애므로
        // 재적재하면 사라진 상세를 다시 요청해 404 응답이 화면이 된다. FormSubmit.sendAsync 는 redirect 를
        // follow 하므로 최종 도착지(resp.url)가 서버가 의도한 이동 지점이다(영구 삭제 → 목록,
        // 그 외 → 상세). 거절 · 네트워크 오류는 전역 핸들러에 그대로 위임한다
        // (trash-action.js · reconciliation/list.js 의 페이지 로컬 override 선례).
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

        // 확인 모달이 없는 즉시 실행 폼(활성 토글 · 사용 중단 권고 · 복원)의 가로채기는
        // S10 의 전역 인터셉터(global/form-submit.js)가 맡는다 — 여기서 폼별로 다시 걸지 않는다.

        // ── soft-delete : 참조 활성 할당 수를 정보성 경고로 곁들인다(차단 아님, DEC-C) ──
        ConfirmModal.bindFormSubmit('data-confirm-soft-delete', function (form) {
            const label = form.getAttribute('data-resource-label') || '이 정의서';
            const count = parseInt(form.getAttribute('data-referencing-count') || '0', 10);
            const warn = count > 0
                ? ' 현재 ' + count + '개 게스트에 활성 할당돼 있지만, 삭제해도 할당 스냅샷은 그대로 유지됩니다(소프트 참조).'
                : '';
            ConfirmModal.open('confirmSoftDelete', {
                title: '정의서 삭제',
                message: label + ' 을(를) 삭제할까요?' + warn,
                confirmLabel: '삭제',
                confirmClass: 'n-btn-outline-danger',
                onConfirm: function () { ConfirmModal.approveAndSubmit(form); }
            });
        });

        // ── purge : typed-name 정확 일치 시에만 확인 활성 ──
        ConfirmModal.bindFormSubmit('data-confirm-purge', function (form) {
            const label = form.getAttribute('data-resource-label') || '이 정의서';
            const expected = form.getAttribute('data-typed-name') || '';
            ConfirmModal.open('confirmPurge', {
                title: '정의서 영구 삭제',
                message: label + ' 을(를) 영구 삭제하려면 정의서명을 정확히 입력하세요. 되돌릴 수 없습니다.',
                confirmLabel: '영구 삭제',
                confirmClass: 'n-btn-danger',
                startDisabled: true,
                suppressDefaultFocus: true,
                afterOpen: function (ctx) {
                    const wrap = document.getElementById('confirmPurgeTypedTargetWrap');
                    const target = document.getElementById('confirmPurgeTypedTarget');
                    const input = document.getElementById('confirmPurgeTypedInput');
                    if (wrap) wrap.hidden = false;
                    if (target) target.textContent = expected;
                    if (!input) return null;
                    input.value = '';
                    input.placeholder = expected;
                    const onInput = function () {
                        ctx.confirmBtn.disabled = (input.value !== expected);
                    };
                    input.addEventListener('input', onInput);
                    setTimeout(function () { input.focus(); }, 0);
                    return function () {
                        input.removeEventListener('input', onInput);
                        if (wrap) wrap.hidden = true;
                    };
                },
                beforeConfirm: function () {
                    // 서버 @RequestParam typedName 으로 보낼 hidden input 을 form 에 심는다(백엔드 재검증 SSOT).
                    const input = document.getElementById('confirmPurgeTypedInput');
                    let hidden = form.querySelector('input[name="typedName"]');
                    if (!hidden) {
                        hidden = document.createElement('input');
                        hidden.type = 'hidden';
                        hidden.name = 'typedName';
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
