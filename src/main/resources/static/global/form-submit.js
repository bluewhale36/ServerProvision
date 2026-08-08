/* ============================================================
   S10 — 상태 변경 폼의 전역 제출 경로.

   왜 전역인가 : 종전에는 폼마다 data-async-submit 마커를 붙여야 fetch 로 나갔고,
   붙이지 않으면 네이티브 제출이 되어 서버 거절 응답(JSON)이 그대로 화면이 됐다.
   마커를 잊는 것이 사고로 이어지는 구조였다(U3-2-b 실사고). 기본값을 뒤집어
   "가로채는 것이 기본, 네이티브가 예외" 로 바꾼다 — 잊었을 때의 결과가 안전해진다.

   가로채지 않는 두 경우 :
     1. data-native-submit — 서버가 실패 시 뷰를 다시 렌더하는 폼(BindingResult).
        fetch 로 보내면 재렌더된 HTML 을 버리게 되어 필드별 오류 표시가 사라진다.
     2. 이미 defaultPrevented — 페이지 전용 스크립트가 자기 방식으로 처리 중인 폼
        (업로드 · nudge · 생성 폼 등 XHR 흐름). 이중 제출을 막는다.

   확인 modal 을 거치는 폼은 여기 오지 않는다. ConfirmModal.bindFormSubmit 이
   대상(form)에서 stopPropagation 하므로 document 까지 버블링되지 않으며,
   사용자 확인 후 ConfirmModal.approveAndSubmit 이 본 모듈의 sendAsync 를 부른다.
   ============================================================ */
(function () {
    'use strict';
    const TAG = '[form-submit]';

    /** 거절 응답을 사용자에게 보이는 기본 처리. error-modal.js 가 window.AsyncSubmitResult 로 덮어쓴다. */
    const defaultResult = {
        onSuccess: () => window.location.reload(),
        onRejected: (_form, status, payload) => {
            const msg = (payload && payload.message) || ('요청이 거절되었어요. (HTTP ' + status + ')');
            if (window.ErrorModal) ErrorModal.show({message: msg, status: status});
        },
        onNetworkError: () => {
            if (window.ErrorModal) ErrorModal.show({message: '서버와 통신할 수 없어요.', status: 0});
        }
    };

    function handler() {
        return window.AsyncSubmitResult || defaultResult;
    }

    /**
     * 폼을 fetch 로 전송하고 응답을 핸들러에 위임한다. 페이지는 이동하지 않는다.
     * 성공(2xx 또는 redirect 추종)은 onSuccess, 거절은 본문을 파싱해 onRejected 로 넘긴다.
     */
    async function sendAsync(form) {
        const method = (form.getAttribute('method') || 'POST').toUpperCase();
        const action = form.getAttribute('action') || window.location.pathname;
        const fd = new FormData(form);
        let url = action;
        let body = fd;
        if (method === 'GET') {
            url = action + (action.includes('?') ? '&' : '?') + new URLSearchParams(fd).toString();
            body = null;
        }
        const h = handler();
        let resp;
        try {
            resp = await fetch(url, {
                method,
                body,
                headers: {'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json,text/html;q=0.9,*/*;q=0.5'},
                redirect: 'follow'
            });
        } catch (err) {
            h.onNetworkError(form, err);
            return;
        }
        if (resp.ok || resp.redirected) {
            h.onSuccess(form, resp);
            return;
        }
        let payload = null;
        try {
            const ct = resp.headers.get('content-type') || '';
            if (ct.includes('application/json')) payload = await resp.json();
            else if (ct.includes('text/plain')) payload = {message: (await resp.text()).slice(0, 500)};
            else {
                // HF4-1 F-3 — 비 JSON 바디(HTML 오류 페이지 등)를 사용자 메시지로 쓰지 않는다.
                // 원문 500자가 모달에 노출되던 사고가 있었다. 진단용 console 로만 남기고 일반화 문구로 fallback.
                console.warn(TAG, '비 JSON 오류 응답 (HTTP ' + resp.status + ', ' + ct + ')',
                    (await resp.text()).slice(0, 500));
            }
        } catch (_) {
            payload = null;
        }
        h.onRejected(form, resp.status, payload);
    }

    /** 이 폼을 가로챌 것인가. 판정 근거는 파일 상단 주석 참조. */
    function shouldIntercept(form) {
        if ((form.getAttribute('method') || 'GET').toUpperCase() !== 'POST') return false;
        return !form.hasAttribute('data-native-submit');
    }

    document.addEventListener('submit', function (e) {
        // 페이지 전용 스크립트가 이미 처리한 폼은 건드리지 않는다(이중 제출 방지).
        if (e.defaultPrevented) return;
        const form = e.target;
        if (!(form instanceof HTMLFormElement)) return;
        if (!shouldIntercept(form)) return;
        e.preventDefault();
        sendAsync(form);
    });

    window.FormSubmit = {sendAsync, shouldIntercept};
})();
