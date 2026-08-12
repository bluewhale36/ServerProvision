/* ============================================================
   U3-4 — 그룹 상세의 삭제 확인 modal 바인딩 + 성공 후 이동 지점 보정.
   ─────────────────────────────────────────────────────────
   확인 modal 은 관리 공용 fragment(confirm-soft-delete)를 prefix 만 바꿔 재사용한다.
   그룹 전용 fragment 를 새로 만들지 않는 이유는 필요한 것이 제목 · 문구 · 버튼뿐이고
   그 셋은 전부 opener 인자로 주입되기 때문이다.

   호출측 form 마커 :
     · data-confirm-group-delete : + data-resource-label + data-member-count
   ============================================================ */
(function () {
    'use strict';

    /* ── 서버 넣기 모달 (개정) ──
       내용은 열 때 조각 엔드포인트에서 받아 채운다. 상세를 그릴 때마다 후보를 시간 × 스펙으로
       조립하면 열지도 않을 화면의 값을 매번 치르게 된다. 한 번 받은 뒤에는 다시 받지 않는다 —
       모달을 닫았다 여는 동안 후보가 바뀌었다면 넣기 시점에 서버 가드가 잡는다. */
    function wireServerPicker() {
        var opener = document.getElementById('openServerPicker');
        var overlay = document.getElementById('serverPicker');
        if (!opener || !overlay) return;

        var body = document.getElementById('serverPickerBody');
        var count = document.getElementById('serverPickerCount');
        var submit = document.getElementById('serverPickerSubmit');
        var loaded = false;

        function refreshSelection() {
            var checked = body.querySelectorAll('[data-picker-check]:checked').length;
            count.textContent = checked === 0 ? '선택한 서버 없음' : '선택한 서버 ' + checked + '대';
            submit.disabled = checked === 0;
        }

        function close() {
            overlay.hidden = true;
            document.removeEventListener('keydown', onKeydown);
        }

        function onKeydown(e) {
            if (e.key === 'Escape') close();
        }

        opener.addEventListener('click', async function () {
            overlay.hidden = false;
            document.addEventListener('keydown', onKeydown);
            if (loaded) { refreshSelection(); return; }
            try {
                var resp = await fetch(opener.getAttribute('data-fetch-url'), {
                    headers: {'Accept': 'text/html'}, credentials: 'same-origin'
                });
                body.innerHTML = resp.ok
                    ? await resp.text()
                    : '<p class="n-detail-empty">후보를 불러오지 못했습니다. 다시 시도해주세요.</p>';
                loaded = resp.ok;
            } catch (e) {
                body.innerHTML = '<p class="n-detail-empty">서버와 통신할 수 없습니다.</p>';
            }
            refreshSelection();
        });

        // 내용이 나중에 채워지므로 위임으로 듣는다 — 직접 바인딩하면 교체된 체크박스를 놓친다
        body.addEventListener('change', function (e) {
            if (e.target.matches('[data-picker-check]')) refreshSelection();
        });
        overlay.addEventListener('click', function (e) {
            if (e.target.closest('[data-picker-close]')) close();
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        wireServerPicker();
        if (!window.ConfirmModal) return;

        // ── 성공 후 이동 지점 — 서버가 지정한 곳을 따른다 ──
        // 전역 기본 동작은 현재 페이지 재적재인데, 그룹 삭제는 지금 보고 있는 그룹을 없애므로
        // 재적재하면 사라진 상세를 다시 요청해 404 가 화면이 된다. sendAsync 가 redirect 를 따라가므로
        // 최종 도착지(resp.url)가 서버가 의도한 곳이다(삭제 → 목록, 그 외 → 상세).
        // 거절 · 네트워크 오류는 전역 핸들러에 그대로 위임한다(setting-lifecycle.js 선례).
        const base = window.AsyncSubmitResult;
        window.AsyncSubmitResult = {
            onSuccess: function (_form, resp) {
                window.location.assign((resp && resp.url) ? resp.url : window.location.href);
            },
            onRejected: function (form, status, payload) {
                if (base && base.onRejected) { base.onRejected(form, status, payload); return; }
                ErrorModal.show({
                    message: (payload && payload.message) || ('요청이 거절되었어요. (HTTP ' + status + ')'),
                    status: status
                });
            },
            onNetworkError: function (form, err) {
                if (base && base.onNetworkError) { base.onNetworkError(form, err); return; }
                ErrorModal.show({message: '서버와 통신할 수 없어요.', status: 0});
            }
        };

        // ── 그룹 삭제 : 멤버가 어떻게 되는지를 문구로 먼저 알린다(DEC-E) ──
        // 되돌릴 수 없지만 파괴적이지 않다 — 서버도 할당도 그대로 남고 없어지는 것은 묶음과 이름뿐이다.
        // 그래서 typed-name 까지 요구하지 않고 확인 한 번으로 끝낸다.
        ConfirmModal.bindFormSubmit('data-confirm-group-delete', function (form) {
            const label = form.getAttribute('data-resource-label') || '이 그룹';
            const count = parseInt(form.getAttribute('data-member-count') || '0', 10);
            const tail = count > 0
                ? ' 멤버 ' + count + '대는 무소속으로 돌아가며, 서버와 그 서버의 세팅 정의서 할당은 그대로 남습니다.'
                : ' 멤버가 없으므로 영향을 받는 서버가 없습니다.';
            ConfirmModal.open('confirmGroupDelete', {
                title: '그룹 지우기',
                message: label + ' 을(를) 지울까요?' + tail,
                confirmLabel: '지우기',
                confirmClass: 'n-btn-outline-danger',
                onConfirm: function () { ConfirmModal.approveAndSubmit(form); }
            });
        });
    });
})();
