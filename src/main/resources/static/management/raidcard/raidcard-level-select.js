/**
 * MA7 CP6 개정 — 지원 RAID 레벨 체크박스의 전체 선택/해제.
 *
 * data-level-select-all 체크박스(master)가 같은 data-level-group 안의 레벨 체크박스를 일괄
 * 토글한다. 역방향 동기화 — 개별 체크 상태에 따라 master 가 checked / indeterminate 로 따라간다.
 *
 * R17-2 — 고른 칩 계열이 만들 수 없는 레벨은 잠그고 체크를 푼다. 만들 수 있는 레벨과 안내 문장은 칩 계열
 * option 의 data-creatable-levels · data-lock-hint 로 서버가 내린다(SSOT = RaidChipFamily) — 이 JS 는 문구를 짓지 않는다.
 * 전체 선택은 잠긴 칸을 건너뛴다.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('input[data-level-select-all]').forEach(master => {
            const group = master.closest('[data-level-group]');
            if (!group) return;
            const form = group.closest('form');
            const familySelect = form ? form.querySelector('select[name="chipFamily"]') : null;
            const hint = group.closest('.n-form-group').querySelector('[data-level-lock-hint]');
            const boxes = () => Array.from(
                group.querySelectorAll('input[type="checkbox"][name="supportedRaidLevels"]'));
            const openBoxes = () => boxes().filter(cb => !cb.disabled);

            const sync = () => {
                const bs = openBoxes();
                const checked = bs.filter(cb => cb.checked).length;
                master.checked = bs.length > 0 && checked === bs.length;
                master.indeterminate = checked > 0 && checked < bs.length;
            };

            const applyLock = () => {
                const option = familySelect && familySelect.selectedOptions[0];
                const creatable = option && option.dataset.creatableLevels
                    ? option.dataset.creatableLevels.split(' ') : null;   // 계열 미선택이면 잠그지 않는다
                const lockHint = option ? option.dataset.lockHint || '' : '';
                const released = [];
                boxes().forEach(cb => {
                    const locked = creatable !== null && !creatable.includes(cb.value);
                    if (locked && cb.checked) {
                        cb.checked = false;
                        released.push(cb.value);
                    }
                    cb.disabled = locked;
                    cb.closest('label').title = locked ? lockHint : '';
                });
                if (hint) {
                    hint.textContent = lockHint + (released.length ? '. 선택 해제: ' + released.join(', ') : '');
                    hint.hidden = !lockHint;
                }
                sync();
            };

            master.addEventListener('change', () => {
                openBoxes().forEach(cb => { cb.checked = master.checked; });
            });
            group.addEventListener('change', evt => {
                if (evt.target !== master) sync();
            });
            if (familySelect) familySelect.addEventListener('change', applyLock);
            applyLock();
        });
    });
})();
