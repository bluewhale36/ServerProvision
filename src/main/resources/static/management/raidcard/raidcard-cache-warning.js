/**
 * MA7 CP6 개정 — 캐시 없는 카드(0GB)에 RAID5 이상을 지원 레벨로 고르면 경고 모달을 띄운다.
 *
 * 근거 : RAID5 이상을 만들 수 있는 컨트롤러는 일반적으로 캐시를 갖는다(업무 관례의 실제 이유).
 * 캐시 0 + RAID5 이상 조합은 대개 사양 오기입이므로 확인을 요구하되, 실물이 실제로 그런 사양일 수
 * 있어 차단하지 않는다 — 서버 가드 없음, 확인 후 진행 허용.
 *
 * 어떤 레벨이 캐시를 동반하는지의 SSOT 는 서버 RaidLevel.typicallyRequiresCache 이며, 템플릿이
 * 각 체크박스에 data-requires-cache 로 내보낸다 — JS 는 레벨 목록을 복제하지 않는다(드리프트 0).
 *
 * 사용 :
 *  - XHR 폼(신규 등록) : raidcard-new.js 가 needsWarning / confirm API 를 직접 호출.
 *  - 네이티브 폼(수정) : form 에 data-cache-warning-native 마커 → 본 스크립트가 submit 을 가로채
 *    경고 승인 후 재제출한다 (data-native-submit 폼이라 전역 인터셉터와 겹치지 않는다).
 */
(function () {
    'use strict';

    function needsWarning(form) {
        const cacheInput = form.querySelector('[name="cacheCapacityGb"]');
        if (!cacheInput || parseInt(cacheInput.value, 10) !== 0) return false;
        return Array.from(form.querySelectorAll('input[type="checkbox"][data-requires-cache="true"]'))
            .some(cb => cb.checked);
    }

    function selectedCacheLevels(form) {
        return Array.from(form.querySelectorAll('input[type="checkbox"][data-requires-cache="true"]'))
            .filter(cb => cb.checked)
            .map(cb => cb.parentElement ? cb.parentElement.textContent.trim() : cb.value);
    }

    function confirm(form, onProceed) {
        if (!window.ConfirmModal) {
            // base 미적재 시 경고 없이 진행하는 대신 native confirm 으로 최소 안내.
            if (window.confirm('캐시 용량이 0GB(없음)인데 RAID5 이상 지원을 선택했습니다. 카드 사양을 다시 확인해주세요. 그대로 진행할까요?')) onProceed();
            return;
        }
        window.ConfirmModal.open('cacheWarn', {
            title: '캐시 없는 카드의 RAID5 이상 지원',
            message: '캐시 용량이 0GB(없음)인데 ' + selectedCacheLevels(form).join(', ')
                + ' 지원을 선택했습니다. 카드 사양을 다시 확인해주세요. '
                + '사실과 다르게 등록할 경우 프로비저닝 시 문제가 생길 수 있습니다.',
            confirmLabel: '사양이 맞습니다: 계속',
            confirmClass: 'n-btn-outline-warning',
            onConfirm: onProceed
        });
    }

    // 네이티브 제출 폼(수정 화면) 자동 바인딩.
    document.addEventListener('DOMContentLoaded', () => {
        const form = document.querySelector('form[data-cache-warning-native]');
        if (!form) return;
        form.addEventListener('submit', evt => {
            if (form.dataset.cacheWarnConfirmed === 'true') return;   // 승인 후 재제출 통과
            if (!needsWarning(form)) return;
            evt.preventDefault();
            confirm(form, () => {
                form.dataset.cacheWarnConfirmed = 'true';
                form.requestSubmit();
            });
        });
        // 값을 고치면 승인 상태 초기화 — 다시 경고 대상이 될 수 있다.
        form.addEventListener('change', () => { delete form.dataset.cacheWarnConfirmed; });
    });

    window.RaidCardCacheWarning = {needsWarning, confirm};
})();
