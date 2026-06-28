/*
  MK1 경로 재조정 페이지 — list.js
  ─────────────────────────────────────
  Miller 컬럼 클릭 동작은 os-list.js 가 처리한다 (data-os-key / data-os-id 일반화).
  본 스크립트는 reconciliation 페이지 특화 동작:
    · 수동 스캔 / Deep 스캔 버튼 → JS fetch (form submit 아님 — JSON 응답이 화면에 노출되는 것을 막음)
    · jobId 는 작업 조회 아이콘이 폴링으로 자동 픽업
    · 사용자 안내 toast 또는 alert 로 "스캔 시작" 표시
*/
(function () {
    'use strict';

    function trigger(btn) {
        const url = btn.dataset.scanUrl;
        if (!url) return;

        const isDeep = btn.id === 'scanDeepBtn';
        const isReissue = btn.id === 'reissueBtn';
        if (isDeep && !confirm('Deep scan 은 모든 자원의 manifestHash 를 재계산합니다. ISO 큰 파일은 수십 초~수 분 소요. 계속할까요?')) {
            return;
        }
        if (isReissue && !confirm(
            '⚠ 마커 서명 일괄 재발급\n\n' +
            'secret 회전 직후에만 실행하세요. 모든 자원의 marker signature 가 현재 secret 으로 재계산됩니다.\n' +
            'manifestHash 는 그대로 유지되므로 변조된 자원이 있다면 이후 deep scan 에서 HASH_MISMATCH 로 노출됩니다.\n\n' +
            '계속할까요?')) {
            return;
        }

        btn.disabled = true;
        btn.dataset.originalLabel = btn.textContent;
        btn.textContent = '시작 중…';

        fetch(url, {
            method: 'POST',
            headers: {'Accept': 'application/json'}
        }).then(res => {
            if (res.ok) return res.json();
            // 409 동시 실행 / 5xx 에러
            return res.text().then(txt => {
                throw new Error('스캔 시작 실패 (HTTP ' + res.status + ') : ' + txt);
            });
        }).then(data => {
            // jobId 받음 — 작업 조회 아이콘이 다음 폴링 사이클에 자동 발견
            btn.textContent = '스캔 중…';
            // 진행률은 작업 조회 아이콘(서류가방) 에서 확인. 완료 후 사용자가 페이지를 새로고침하면 새 보고서가 보임.
            // 후속 개선: jobId 폴링 후 COMPLETED 시 자동 reload.
            setTimeout(() => {
                btn.disabled = false;
                btn.textContent = btn.dataset.originalLabel;
            }, 3000);
        }).catch(err => {
            ErrorModal.show({message: err.message || '스캔 시작 실패', status: 0});
            btn.disabled = false;
            btn.textContent = btn.dataset.originalLabel;
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        ['scanBtn', 'scanDeepBtn', 'reissueBtn'].forEach(id => {
            const btn = document.getElementById(id);
            if (btn) btn.addEventListener('click', () => trigger(btn));
        });
    });
})();
