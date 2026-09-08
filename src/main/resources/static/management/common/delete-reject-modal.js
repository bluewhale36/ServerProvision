/* ============================================================
   MK3-2 — softDelete 거절 모달(3 택) 처리.
   ─────────────────────────────────────────────────────────────
   삭제 폼은 확인 모달(confirm-modal-base · data-confirm-soft-delete)이 먼저 잡아 FormSubmit.sendAsync 로
   보낸다. 서버가 파일 부재를 발견해 409 SOFTDELETE_REQUIRES_INTENT 를 돌려주면(플래그
   provision.softdelete.reject-on-missing=true · HF14) 이 모듈이 전역 거절 처리기에서 그 코드만 받아
   3 택(위치 정정 후 삭제 · 강제 정리 · 취소) 모달을 연다. 조건 = form.mk3-2-delete-form 마커 + 조각
   fragments/management/delete-reject-modal(prefix 'deleteReject'). 그 밖의 거절은 종전대로 전역 오류 모달.

   종전에는 폼의 submit 을 직접 가로채 fetch 하는 경로(bind · handleSubmit)도 있었으나 마커 폼이 전부
   확인 마커도 갖고 있어 어느 화면에서도 실행되지 않았다(앵커 HF14 검증 O-A) — 제거했다.

   form 의 action 이 `/management/<domain>/.../delete` 일 때 동일 prefix 의 `/delete-intent/{token}`
   endpoint 를 자동 조립한다.
*/
(function () {
    function openModal(prefix, payload, deleteAction) {
        const modal = document.getElementById(prefix + 'Modal');
        const missingEl = document.getElementById(prefix + 'MissingPath');
        const ghostBadge = document.getElementById(prefix + 'GhostBadge');
        const correctBtn = document.getElementById(prefix + 'CorrectBtn');
        const forcedBtn = document.getElementById(prefix + 'ForcedBtn');
        const cancelBtn = document.getElementById(prefix + 'CancelBtn');
        const closeBtn = document.getElementById(prefix + 'CloseBtn');
        const errorEl = document.getElementById(prefix + 'Error');

        if (!modal || !correctBtn || !forcedBtn || !cancelBtn) {
            ErrorModal.show({message: payload.code + ' — modal 요소를 찾을 수 없습니다. 페이지를 새로고침 해주세요.'});
            return;
        }

        if (missingEl) missingEl.textContent = payload.missingPath || '(unknown)';
        if (ghostBadge) ghostBadge.hidden = !payload.ghostCandidate;
        if (errorEl) {
            errorEl.hidden = true;
            errorEl.textContent = '';
        }

        // intent endpoint URL 조립 — 기존 deleteAction 의 `/delete` 를 `/delete-intent/{token}` 으로 치환
        const intentUrl = deleteAction.replace(/\/delete$/, '/delete-intent/' + encodeURIComponent(payload.intentToken));

        modal.hidden = false;

        const close = () => {
            modal.hidden = true;
            correctBtn.onclick = null;
            forcedBtn.onclick = null;
            cancelBtn.onclick = null;
            if (closeBtn) closeBtn.onclick = null;
        };

        const callIntent = async (action) => {
            disableButtons(true);
            try {
                const resp = await fetch(intentUrl, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                    body: JSON.stringify({action: action})
                });
                if (resp.ok) {
                    close();
                    window.location.reload();
                    return;
                }
                let body = null;
                try {
                    body = await resp.json();
                } catch (_) { /* ignore */
                }
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
        forcedBtn.onclick = () => {
            if (!confirm('시스템 등록만 제거됩니다. 디스크 파일은 유지됩니다 (이미 사라진 경우 영향 없음). 계속할까요?')) return;
            callIntent('FORCED_CLEAR');
        };
        cancelBtn.onclick = close;
        if (closeBtn) closeBtn.onclick = close;

        function disableButtons(disabled) {
            correctBtn.disabled = disabled;
            forcedBtn.disabled = disabled;
            cancelBtn.disabled = disabled;
        }
    }

    // HF14 — 확인 모달(confirm-modal-base)이 같은 폼의 submit 을 먼저 잡아 FormSubmit.sendAsync 로 보낸다. 그 경로의 거절 처리기는 전역 오류 모달이라 409
    // SOFTDELETE_REQUIRES_INTENT 가 3 택 거절 모달에 닿지 못했다(플래그 false 기본이던 동안 잠복). 등록 순서에
    // 기대지 않고, 전역 거절 처리기를 감싸 그 코드만 여기로 돌린다. head defer 인 error-modal.js 가 base 를 만든
    // 뒤여야 하므로 DOMContentLoaded 안에서 감싼다(S17-2 CP5 F-3 의 순서).
    document.addEventListener('DOMContentLoaded', () => {
        const base = window.AsyncSubmitResult;
        if (!base) return;
        window.AsyncSubmitResult = Object.assign({}, base, {
            onRejected(form, status, payload) {
                if (status === 409 && payload && payload.code === 'SOFTDELETE_REQUIRES_INTENT'
                        && form && form.matches('form.mk3-2-delete-form') && document.getElementById('deleteRejectModal')) {
                    openModal('deleteReject', payload, form.getAttribute('action'));
                    return;
                }
                base.onRejected(form, status, payload);
            }
        });
    });

})();
