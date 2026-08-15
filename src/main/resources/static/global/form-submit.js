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
        (업로드 · nudge · 생성 폼 등 XHR 흐름). 전역과 페이지 스크립트가 겹쳐 보내는 것을 막는다.

   MK4-4-4 — 위 2 는 <b>핸들러가 겹치는 것</b>을 막을 뿐, 사용자가 버튼을 두 번 누르는 것은 막지
   않았다. fetch 가 도는 동안 폼은 그대로 살아 있어 같은 요청이 두 번 나갈 수 있었다. 저장이
   멱등인 곳은 결과가 같지만, 그렇지 않은 곳(파일을 옮기는 해결 등)에서는 두 번째 요청이 이미
   바뀐 상태를 다시 건드린다. 아래 inFlight 가 그것을 막는다.

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
    /**
     * 지금 보내는 중인 폼. WeakSet 이라 폼이 DOM 에서 사라지면 함께 사라진다.
     * reload 로 화면이 갈리는 흐름에서 목록을 손으로 비울 필요가 없다.
     */
    const inFlight = new WeakSet();

    /**
     * 응답을 기다리는 동안 제출 버튼을 잠근다.
     *
     * <p>원래 잠겨 있던 버튼은 건드리지 않는다 — 그래야 푸는 쪽도 정확해진다. 화면마다 문구가
     * 달라 텍스트는 바꾸지 않고 잠그기만 한다(각 화면의 스크립트가 자기 문구로 바꾼다).</p>
     */
    function lockSubmits(form) {
        const locked = [];
        form.querySelectorAll('button[type="submit"], button:not([type])').forEach(b => {
            if (!b.disabled) {
                b.disabled = true;
                locked.push(b);
            }
        });
        return () => locked.forEach(b => {
            b.disabled = false;
        });
    }

    async function sendAsync(form) {
        // 이미 보내는 중이면 두 번째 요청을 만들지 않는다. 버튼 잠금은 눈으로 알리는 장치이고
        // 이 검사가 실제 방벽이다 — 잠금은 Enter 키 제출이나 스크립트 호출을 막지 못한다.
        if (inFlight.has(form)) return;
        inFlight.add(form);
        const unlock = lockSubmits(form);
        try {
            await send(form);
        } finally {
            inFlight.delete(form);
            unlock();
        }
    }

    async function send(form) {
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
