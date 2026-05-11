/* ============================================================
   MK3-2 — softDelete reject modal 처리.
   ─────────────────────────────────────────────────────────────
   기존 페이지의 "삭제" form submit 을 가로채 fetch() 로 호출.
   - 200/204/302  → 정상 삭제 → form 의 action 으로 redirect (서버가 redirect 응답 시)
                    또는 location.reload() 로 페이지 갱신
   - 409 SOFTDELETE_REQUIRES_INTENT → modal 표시 → 사용자 액션 → delete-intent endpoint 호출
   - 그 외 에러 → modal 의 error 영역 또는 alert

   사용 :
     window.DeleteRejectModal.bind({
         deleteFormSelector: 'form.delete-form',  // 가로챌 form 선택자
         modalPrefix: 'deleteReject'              // fragment 의 prefix 와 일치
     });

   form 의 action 이 `/management/<domain>/.../delete` 일 때, 본 JS 가
   동일 prefix 의 `/delete-intent/{token}` endpoint 를 자동 조립.
*/
(function () {
    const TAG = '[delete-reject]';

    function bind(opts) {
        const { deleteFormSelector, modalPrefix } = opts;
        const forms = document.querySelectorAll(deleteFormSelector);
        if (forms.length === 0) return;

        const modal = document.getElementById(modalPrefix + 'Modal');
        if (!modal) {
            console.warn(TAG, 'modal element not found :', modalPrefix + 'Modal');
            return;
        }

        forms.forEach(form => {
            form.addEventListener('submit', e => {
                e.preventDefault();
                handleSubmit(form, modalPrefix);
            }, true);
        });
    }

    async function handleSubmit(form, prefix) {
        const action = form.getAttribute('action');
        if (!action) return;

        // 사용자 confirm 은 form 의 onsubmit attribute 가 이미 처리. JS 가 가로챈 시점은
        // onsubmit 이 OK 를 반환한 직후이므로 별도 confirm 추가 안 함 (중복 dialog 방지).

        try {
            const resp = await fetch(action, {
                method: 'POST',
                headers: { 'Accept': 'application/json' },
                redirect: 'manual'
            });

            // 정상 (204 / 302 redirect / 200) — 페이지 갱신 또는 redirect 따라가기
            if (resp.ok || resp.status === 0 || resp.type === 'opaqueredirect') {
                window.location.reload();
                return;
            }

            // 409 + SOFTDELETE_REQUIRES_INTENT → modal
            if (resp.status === 409) {
                let body = null;
                try { body = await resp.json(); } catch (_) { /* ignore */ }
                if (body && body.code === 'SOFTDELETE_REQUIRES_INTENT') {
                    openModal(prefix, body, action);
                    return;
                }
                alert((body && body.message) || '삭제 충돌이 발생했습니다.');
                return;
            }

            // 그 외 에러
            let body = null;
            try { body = await resp.json(); } catch (_) { /* ignore */ }
            alert((body && body.message) || ('삭제 실패 (HTTP ' + resp.status + ')'));
        } catch (err) {
            console.error(TAG, 'submit error', err);
            alert('네트워크 오류 : ' + err.message);
        }
    }

    function openModal(prefix, payload, deleteAction) {
        const modal       = document.getElementById(prefix + 'Modal');
        const missingEl   = document.getElementById(prefix + 'MissingPath');
        const ghostBadge  = document.getElementById(prefix + 'GhostBadge');
        const correctBtn  = document.getElementById(prefix + 'CorrectBtn');
        const forcedBtn   = document.getElementById(prefix + 'ForcedBtn');
        const cancelBtn   = document.getElementById(prefix + 'CancelBtn');
        const closeBtn    = document.getElementById(prefix + 'CloseBtn');
        const errorEl     = document.getElementById(prefix + 'Error');

        if (!modal || !correctBtn || !forcedBtn || !cancelBtn) {
            alert(payload.code + ' — modal 요소를 찾을 수 없습니다. 페이지를 새로고침 해주세요.');
            return;
        }

        if (missingEl) missingEl.textContent = payload.missingPath || '(unknown)';
        if (ghostBadge) ghostBadge.hidden = !payload.ghostCandidate;
        if (errorEl) { errorEl.hidden = true; errorEl.textContent = ''; }

        // intent endpoint URL 조립 — 기존 deleteAction 의 `/delete` 를 `/delete-intent/{token}` 으로 치환
        const intentUrl = deleteAction.replace(/\/delete$/, '/delete-intent/' + encodeURIComponent(payload.intentToken));

        modal.hidden = false;

        const close = () => {
            modal.hidden = true;
            correctBtn.onclick = null;
            forcedBtn.onclick  = null;
            cancelBtn.onclick  = null;
            if (closeBtn) closeBtn.onclick = null;
        };

        const callIntent = async (action) => {
            disableButtons(true);
            try {
                const resp = await fetch(intentUrl, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                    body: JSON.stringify({ action: action })
                });
                if (resp.ok) {
                    close();
                    window.location.reload();
                    return;
                }
                let body = null;
                try { body = await resp.json(); } catch (_) { /* ignore */ }
                if (errorEl) {
                    errorEl.hidden = false;
                    errorEl.textContent = (body && body.message) || ('처리 실패 (HTTP ' + resp.status + ')');
                }
            } catch (err) {
                if (errorEl) {
                    errorEl.hidden = false;
                    errorEl.textContent = '네트워크 오류 : ' + err.message;
                }
            } finally {
                disableButtons(false);
            }
        };

        correctBtn.onclick = () => callIntent('CORRECT_PATH_THEN_DELETE');
        forcedBtn.onclick  = () => {
            if (!confirm('시스템 등록만 제거됩니다. 디스크 파일은 유지됩니다 (이미 사라진 경우 영향 없음). 계속할까요?')) return;
            callIntent('FORCED_CLEAR');
        };
        cancelBtn.onclick = close;
        if (closeBtn) closeBtn.onclick = close;

        function disableButtons(disabled) {
            correctBtn.disabled = disabled;
            forcedBtn.disabled  = disabled;
            cancelBtn.disabled  = disabled;
        }
    }

    window.DeleteRejectModal = { bind };
})();
