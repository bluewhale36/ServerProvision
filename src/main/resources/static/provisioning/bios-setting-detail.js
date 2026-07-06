/*
 * BIOS 세팅 템플릿 상세 페이지 액션 (U2-2-2, vanilla JS).
 * - 삭제: 기존 confirm-modal 자산(ConfirmModal.open + confirm-soft-delete fragment) 재사용.
 *   확인 시 XHR DELETE → 204 → 목록 이동 · 실패 → 상단 배너(n-alert-danger) 표시.
 *   (form submit 흐름이 아니므로 bindFormSubmit/data-confirm-* 마커는 쓰지 않는다.)
 * - 사용 중(inUse) 템플릿: 뷰가 버튼 disabled + tooltip 으로 1차 차단(U2-2-3) — click 핸들러도
 *   disabled 를 재확인해 modal 을 열지 않는다. 서버 409 는 direct DELETE 안전망.
 */
(function () {
	'use strict';

	const deleteButton = document.getElementById('templateDeleteBtn');
	if (!deleteButton) return;
	const banner = document.getElementById('templateActionBanner');
	const deleteUrl = deleteButton.dataset.deleteUrl;
	const listUrl = deleteButton.dataset.listUrl;
	const resourceLabel = deleteButton.dataset.resourceLabel || '이 템플릿';

	function showBanner(message) {
		if (!banner) return;
		banner.textContent = message;
		banner.hidden = false;
		window.scrollTo({top: 0, behavior: 'smooth'});
	}

	async function requestDelete() {
		deleteButton.disabled = true;
		if (banner) banner.hidden = true;
		try {
			const res = await fetch(deleteUrl, {
				method: 'DELETE',
				headers: {'Accept': 'application/json'}
			});
			if (res.ok) {
				window.location.href = listUrl; // 204 → 목록으로.
				return;
			}
			// ApiErrorResponse(message) / ProblemDetail(detail) 모두 수용 — error-modal.fromResponse 와 동일 순서.
			const body = await res.json().catch(() => null);
			showBanner((body && (body.detail || body.message))
				|| ('삭제 요청이 거절되었습니다. (HTTP ' + res.status + ')'));
		} catch (err) {
			showBanner('서버와 통신하지 못했습니다: ' + String(err));
		}
		deleteButton.disabled = false; // 실패 시에만 도달 — 재시도 허용 (성공 시엔 페이지 이탈).
	}

	deleteButton.addEventListener('click', () => {
		if (deleteButton.disabled) return; // 사용 중(inUse)/요청 진행 중 — modal 을 열지 않는다 (1차 차단)
		window.ConfirmModal.open('confirmSoftDelete', {
			title: '템플릿 삭제',
			message: "BIOS 세팅 템플릿 '" + resourceLabel + "' 을(를) 삭제할까요? 이 작업은 되돌릴 수 없습니다.",
			confirmLabel: '삭제',
			confirmClass: 'n-btn-danger',
			onConfirm: requestDelete
		});
	});
})();
