/**
 * MA7 CP6 개정 — 지원 RAID 레벨 체크박스의 전체 선택/해제.
 *
 * data-level-select-all 체크박스(master)가 같은 data-level-group 안의 레벨 체크박스를 일괄
 * 토글한다. 역방향 동기화 — 개별 체크 상태에 따라 master 가 checked / indeterminate 로 따라간다.
 * master 의 change 는 form 까지 버블되므로 수정 화면의 경고 승인 초기화(raidcard-cache-warning.js)와
 * 자연히 맞물린다.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('input[data-level-select-all]').forEach(master => {
            const group = master.closest('[data-level-group]');
            if (!group) return;
            const boxes = () => Array.from(
                group.querySelectorAll('input[type="checkbox"][name="supportedRaidLevels"]'));

            const sync = () => {
                const bs = boxes();
                const checked = bs.filter(cb => cb.checked).length;
                master.checked = bs.length > 0 && checked === bs.length;
                master.indeterminate = checked > 0 && checked < bs.length;
            };

            master.addEventListener('change', () => {
                boxes().forEach(cb => { cb.checked = master.checked; });
            });
            group.addEventListener('change', evt => {
                if (evt.target !== master) sync();
            });
            sync();
        });
    });
})();
