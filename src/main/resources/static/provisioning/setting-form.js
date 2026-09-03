/**
 * 세팅 정의서 작성/수정 폼 공유 로직 (setting-new.html / setting-edit.html 공용).
 *
 * 레거시 setting-form.js 를 renew 계약에 맞춰 정리 이식한 것 :
 *  - 판별자 정정         : BASIC_UPDATES → BASIC_UPDATE
 *  - 엔드포인트 변경     : /pxe/v1/setting/api/* → /provisioning/setting (form data-* 로 주입)
 *  - /api/validate 사전검증 흐름 제거 — 제출 시 바로 POST/PUT (U2-3 에서 재도입 예정)
 *  - alert() 미사용      : 폼 배너(.n-form-banner) + 인라인 필드 에러 + window.ErrorModal
 *  - 동적 행은 <template> 복제 — 서버 enum 선택지는 Thymeleaf 가 렌더, JS 는 값만 채운다
 *  - deprecated 자원 선택 : 확인 modal(ConfirmModal 'deprecatedUse') + 라벨 옆 '지원 중단' 뱃지.
 *    저장은 막지 않는다. pre-fill 경로는 뱃지만 갱신하고 modal 을 띄우지 않는다.
 *
 * 전송 JSON 계약 (dto/request 의 @JsonProperty 와 일치해야 한다) :
 *   {name, processList:[{type, ...}]}
 *   type: BASIC_UPDATE | BASIC_SETTING | OS_INSTALLATION | OS_SETTING
 *   OS 항목의 2단 판별자 osFamily: RHEL_BASED | DEBIAN_BASED | WINDOWS (E4-1-a-2)
 *   BASIC_UPDATE selector : boardModel {mode: AUTO|SPECIFIED, boardModelId}
 *                           + bios/bmc {mode: LATEST|SPECIFIED, firmwareId}
 *                           (SSOT: 보드 AUTO ⇒ BIOS/BMC 는 LATEST 만 — UI 가 고정+disabled 로 1차 차단)
 *   BASIC_SETTING : biosSettingTemplateIds [id...] (1개 이상 — 서버 400 안전망과 동일 SSOT 의 UI 1차 차단 :
 *                   보드 SPECIFIED ⇒ 그 보드 템플릿 1개(라디오 의미론), AUTO ⇒ 보드당 1개)
 *   boolean 키 : isUTC / isGrow / isKDumpEnabled / isSudoer / isPasswordEncrypted / keepExistingPassword
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const form = document.getElementById('settingForm');
        if (!form) return;

        const banner = form.querySelector('.n-form-banner');

        /* ─────────────────────────── 공통 유틸 ─────────────────────────── */

        function showBanner(lines) {
            const text = (Array.isArray(lines) ? lines : [lines]).filter(Boolean).join(' · ');
            if (!banner) {
                if (text) console.warn('[settingForm]', text);
                return;
            }
            banner.textContent = text;
            banner.hidden = !text;
        }

        function selectedOption(select) {
            return select && select.selectedIndex >= 0 ? select.options[select.selectedIndex] : null;
        }

        function intOrNull(value) {
            const n = parseInt(value, 10);
            return Number.isNaN(n) ? null : n;
        }

        function splitCsv(value) {
            return String(value || '').split(',').map(v => v.trim()).filter(v => v.length > 0);
        }

        /**
         * 직렬화 키 방어 판독 — initialSettingJson 은 backend 가 Lombok getter 기준으로 직렬화하므로
         * is-접두 boolean 필드의 출력 키가 요청 계약 키(isUTC 등)와 다를 수 있다
         * (예: isUTC() getter → "utc"/"UTC" 로 mangling 될 가능성). 후보 키를 순서대로 조회한다.
         */
        function pickBool(obj) {
            if (!obj) return false;
            for (let i = 1; i < arguments.length; i++) {
                const key = arguments[i];
                if (typeof obj[key] !== 'undefined' && obj[key] !== null) return !!obj[key];
            }
            return false;
        }

        function cssEscape(value) {
            if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_\-\[\].]/g, ch => '\\' + ch);
        }

        function cloneTemplateRow(templateId) {
            const tpl = document.getElementById(templateId);
            return tpl ? tpl.content.firstElementChild.cloneNode(true) : null;
        }

        function bindRowRemove(row) {
            const btn = row.querySelector('[data-row-remove]');
            if (btn) btn.addEventListener('click', () => row.remove());
        }

        /* ─────────────────────────── 단계 카드 추가/제거 ─────────────────────────── */

        const emptyNotice = document.getElementById('stepEmptyNotice');

        function cardOf(type) {
            return form.querySelector('[data-process-card="' + cssEscape(type) + '"]');
        }

        function activeCards() {
            return Array.from(form.querySelectorAll('[data-process-card]:not([hidden])'));
        }

        function addButtonOf(type) {
            return form.querySelector('[data-step-add="' + cssEscape(type) + '"]');
        }

        function refreshEmptyNotice() {
            if (emptyNotice) emptyNotice.hidden = activeCards().length > 0;
        }

        function addStep(type) {
            const card = cardOf(type);
            if (!card || !card.hidden) return;
            card.hidden = false;
            card.open = true;
            const btn = addButtonOf(type);
            if (btn) btn.disabled = true;
            refreshEmptyNotice();
            syncOsSelectionLock();
            syncBsBoardLock();
            refreshBsTemplateRules(); // BASIC_UPDATE/BASIC_SETTING 카드 유무가 템플릿 선택 규칙에 반영된다
            // U4-1-2 — RAID 구성 카드를 처음 열면 우선순위 기본 행을 채운다(E26 — 정의서는 언제나 값을 갖고 기본값은 폼이 채운 값).
            // 수정 pre-fill 은 이 뒤에 저장본으로 덮어쓴다. OS 설치 카드의 유무가 OS 후보 안내에 걸리므로 진리표도 다시 본다.
            if (type === 'RAID_CONFIGURATION' && rcPriorityTbody && !priorityRows().length) resetPriorityRows();
            applyDiskGroupConstraints();
        }

        function removeStep(type) {
            const card = cardOf(type);
            if (!card || card.hidden) return;
            card.hidden = true;
            const btn = addButtonOf(type);
            if (btn) btn.disabled = false;
            refreshEmptyNotice();
            syncOsSelectionLock();
            syncBsBoardLock();
            refreshBsTemplateRules(); // 펌웨어 단계 제거 ⇒ AUTO 취급으로 재평가
            applyDiskGroupConstraints(); // OS 설치 카드 제거 ⇒ OS 후보 안내 재평가(U4-1-2)
        }

        form.querySelectorAll('[data-step-add]').forEach(btn => {
            btn.addEventListener('click', () => addStep(btn.dataset.stepAdd));
        });

        form.querySelectorAll('[data-step-remove]').forEach(btn => {
            btn.addEventListener('click', e => {
                // summary 내부 버튼 — preventDefault 로 details 토글을 막는다.
                e.preventDefault();
                e.stopPropagation();
                const card = btn.closest('[data-process-card]');
                if (card) removeStep(card.dataset.processCard);
            });
        });

        refreshEmptyNotice();

        /* ─────────────────────────── BASIC_UPDATE : selector (자동 감지/최신 버전) ─────────────────────────── */

        // 센티널 option 값 = mode enum 상수명 (BoardModelSelectionMode.AUTO / FirmwareSelectionMode.LATEST).
        const BOARD_AUTO = 'AUTO';
        const FIRMWARE_LATEST = 'LATEST';

        const buBoardModel = document.getElementById('buBoardModel');
        const buBios = document.getElementById('buBios');
        const buBmc = document.getElementById('buBmc');

        function isBoardAuto() {
            return !buBoardModel || buBoardModel.value === BOARD_AUTO;
        }

        /**
         * 펌웨어 select 를 보드 선택 상태에 정렬한다 — SSOT 규칙
         * (BasicUpdateRequest.isFirmwareSelectionCoherent)의 UI 1차 차단 구현 :
         *   보드 AUTO      ⇒ '최신 버전' 고정 + disabled (+ wrapper tooltip 활성)
         *   보드 SPECIFIED ⇒ 활성화, '최신 버전' + 해당 보드의 펌웨어(data-board-id)만 선택 가능
         */
        function syncFirmwareSelect(select, autoLocked, boardId) {
            if (!select) return;
            select.querySelectorAll('option[data-board-id]').forEach(opt => {
                const match = !autoLocked && !!boardId && opt.dataset.boardId === boardId;
                opt.classList.toggle('unavailable', !match);
                opt.disabled = !match;
            });
            if (autoLocked) {
                select.value = FIRMWARE_LATEST;
                select.disabled = true;
            } else {
                select.disabled = false;
                // 다른 보드의 펌웨어가 남아 있으면 '최신 버전' 으로 초기화
                const current = selectedOption(select);
                if (!current || current.disabled) select.value = FIRMWARE_LATEST;
            }
            // 잠금 상태에서만 tooltip 노출 (활성 상태의 noise 회피 — S5-3-2 관례)
            const wrap = select.closest('.n-btn-tooltip-wrap');
            if (wrap) wrap.setAttribute('data-tooltip-active', autoLocked ? 'true' : 'false');
            // SPECIFIED 보드에 등록 펌웨어 0개 — 저장은 막지 않는 인지용 경고(실행 시 해당 축 skip, 사용자 확정).
            const emptyHint = document.getElementById(select === buBios ? 'buBiosEmptyHint' : 'buBmcEmptyHint');
            if (emptyHint) {
                const hasAny = !autoLocked && !!boardId
                    && !!select.querySelector('option[data-board-id="' + cssEscape(boardId) + '"]');
                emptyHint.hidden = autoLocked || !boardId || hasAny;
            }
        }

        function onBoardModelChange() {
            const auto = isBoardAuto();
            const boardId = auto ? null : buBoardModel.value;
            syncFirmwareSelect(buBios, auto, boardId);
            syncFirmwareSelect(buBmc, auto, boardId);
        }

        if (buBoardModel) {
            buBoardModel.addEventListener('change', onBoardModelChange);
            onBoardModelChange(); // 초기 상태(기본 AUTO) 정렬
        }

        /* ─────────────────────────── OS_INSTALLATION ─────────────────────────── */

        const oiOsSelect = document.getElementById('oiOsSelect');
        const oiGuide = document.getElementById('oiGuide');
        const oiDetailFields = document.getElementById('oiDetailFields');
        const oiDefaultPartitions = document.getElementById('oiDefaultPartitions');
        const oiPartitionTbody = document.querySelector('#oiPartitionTable tbody');
        const oiUserTbody = document.querySelector('#oiUserTable tbody');
        const oiEnvironment = document.getElementById('oiEnvironment');
        const oiPkgPlaceholder = document.getElementById('oiPkgPlaceholder');
        const oiAllowSshBox = document.getElementById('oiAllowSshBox');
        const oiRootKeepWrap = document.getElementById('oiRootKeepWrap');
        const oiRootKeep = document.getElementById('oiRootKeep');
        const oiRootPassword = document.getElementById('oiRootPassword');
        // E4-1-a-2 — Windows 계열 블록(설치 이미지 · Administrator 비밀번호). 선택 OS 의 data-os-family 가 WINDOWS 일 때 열린다.
        const oiWindowsBlock = document.getElementById('oiWindowsBlock');
        const oiWindowsImage = document.getElementById('oiWindowsImage');
        const oiWindowsImagePrefillWarning = document.getElementById('oiWindowsImagePrefillWarning');
        const oiWinAdminPassword = document.getElementById('oiWinAdminPassword');
        const oiWinAdminKeepWrap = document.getElementById('oiWinAdminKeepWrap');
        const oiWinAdminKeep = document.getElementById('oiWinAdminKeep');
        // U4-1-1 v2 — RAID 구성 단계 카드(RAID 카드 + 디스크 묶음 규칙). 독립 단계라 OS 선택과 무관하게 동작한다.
        const rcRaidCard = document.getElementById('rcRaidCard');
        const rcRaidCardHint = document.getElementById('rcRaidCardHint');
        const rcRaidCardPrefillWarning = document.getElementById('rcRaidCardPrefillWarning');
        const rcDiskGroupTbody = document.querySelector('#rcDiskGroupTable tbody');
        // U4-1-2 — 볼륨 우선순위 표 · 안내
        const rcPriorityTbody = document.querySelector('#rcPriorityTable tbody');
        const rcAddPriority = document.getElementById('rcAddPriority');
        const rcResetPriority = document.getElementById('rcResetPriority');
        const rcPriorityHint = document.getElementById('rcPriorityHint');
        const rcPriorityPrefillWarning = document.getElementById('rcPriorityPrefillWarning');
        const rcNoOsCandidateHint = document.getElementById('rcNoOsCandidateHint');

        function osFamilyPanes() {
            return Array.from(form.querySelectorAll('#oiDetailFields .n-os-family-pane'));
        }

        /* R11 표적 축소 — OS 설치 카드는 식별(OS · ISO)만 받는다. 이 파일에 남은 상세 입력
           로직(타임존 · 파티션 · 사용자 · 계열 pane · 환경/패키지)은 마크업 제거로 미호출이며,
           요소 부재 시 null-guard 로 자연 no-op 이다 — 서버 계약 보존(E4 부활 시 재노출)과
           한 벌로 남긴다(R11 plan D-R2 · D-R5). */
        function onInstallOsChange() {
            const osId = oiOsSelect ? oiOsSelect.value : '';
            if (oiDetailFields) oiDetailFields.hidden = !osId;
            filterIsoOptions(osId);
            syncWindowsBlock();
            syncOsSelectionLock();
        }

        /** 선택된 설치 OS 의 계열(옵션 data-os-family) — 미선택이면 ''. 판별자와 Windows 블록 노출의 단일 출처. */
        function installOsFamily() {
            const opt = oiOsSelect ? selectedOption(oiOsSelect) : null;
            return opt && oiOsSelect.value ? (opt.dataset.osFamily || '') : '';
        }

        /** Windows 블록 노출 — 계열이 바뀌면 이전 입력을 비워 다른 계열 payload 에 섞이지 않게 한다. */
        function syncWindowsBlock() {
            if (!oiWindowsBlock) return;
            const windows = installOsFamily() === 'WINDOWS';
            oiWindowsBlock.hidden = !windows;
            if (!windows) {
                if (oiWindowsImage) oiWindowsImage.value = '';
                if (oiWinAdminPassword) oiWinAdminPassword.value = '';
                if (oiWindowsImagePrefillWarning) oiWindowsImagePrefillWarning.hidden = true;
            }
        }

        const oiIsoSelect = document.getElementById('oiIsoSelect');
        const oiTzRegion = document.getElementById('oiTzRegion');
        const oiTzCity = document.getElementById('oiTzCity');

        /* ---- 타임존 2-select (사용자 확정 2026-07-12) — 값은 "대륙/도시" IANA 조합 ---- */

        /** 대륙 선택에 따라 도시 옵션을 필터하고, 기본(fallback) 도시를 선택한다. */
        function filterTzCities(preferredCity) {
            if (!oiTzRegion || !oiTzCity) return;
            const region = oiTzRegion.value;
            let first = null;
            oiTzCity.querySelectorAll('option[data-region]').forEach(opt => {
                const match = opt.dataset.region === region;
                opt.classList.toggle('unavailable', !match);
                opt.disabled = !match;
                if (match && first === null) first = opt.value;
            });
            const wanted = preferredCity != null ? preferredCity : (region === 'Asia' ? 'Seoul' : null);
            oiTzCity.value = '';
            if (wanted) oiTzCity.value = wanted;
            const chosen = selectedOption(oiTzCity);
            if ((!chosen || chosen.disabled) && first !== null) oiTzCity.value = first;
        }

        /** wire 조합값 — "Asia/Seoul". 미선택 방어는 Layer A(@NotBlank/@AssertTrue)가 받는다. */
        function tzValue() {
            if (!oiTzRegion || !oiTzCity || !oiTzCity.value) return '';
            return oiTzRegion.value + '/' + oiTzCity.value;
        }

        if (oiTzRegion) {
            oiTzRegion.addEventListener('change', () => filterTzCities(null));
            filterTzCities(null); // 초기 기본값 — Asia(서버 selected)/Seoul
        }

        /** 설치 ISO 옵션을 선택된 osMetadataId 로 필터링한다(환경 select 와 동일 관용구, U2-4). */
        function filterIsoOptions(osId) {
            if (!oiIsoSelect) return;
            const placeholder = oiIsoSelect.querySelector('option[data-placeholder]');
            oiIsoSelect.disabled = !osId;
            if (placeholder) {
                placeholder.textContent = osId ? 'ISO 를 선택하세요' : 'OS 를 먼저 선택하세요';
                placeholder.selected = true;
            }
            oiIsoSelect.querySelectorAll('option[data-os-id]').forEach(opt => {
                const match = !!osId && opt.dataset.osId === osId;
                opt.classList.toggle('unavailable', !match);
                opt.disabled = !match;
            });
            // 해당 OS 의 ISO 가 1개뿐이면 자동 선택(서버 제외 규칙상 0개는 도달 불가).
            const candidates = Array.from(oiIsoSelect.querySelectorAll('option[data-os-id]:not([disabled])'));
            if (candidates.length === 1) {
                oiIsoSelect.value = candidates[0].value;
                commitDeprecatedSelection(oiIsoSelect);
            }
        }

        /** 설치 환경 옵션 필터 — 가용 스코프는 선택된 ISO(data-iso-id, 사용자 확정 2026-07-11). */
        function filterEnvironmentOptions() {
            if (!oiEnvironment) return;
            const isoId = oiIsoSelect ? oiIsoSelect.value : '';
            const placeholder = oiEnvironment.querySelector('option[data-placeholder]');
            oiEnvironment.disabled = !isoId;
            if (placeholder) {
                placeholder.textContent = isoId ? '환경을 선택하세요' : 'ISO 를 먼저 선택하세요';
                placeholder.selected = true;
            }
            oiEnvironment.querySelectorAll('option[data-iso-id]').forEach(opt => {
                const match = !!isoId && opt.dataset.isoId === isoId;
                opt.classList.toggle('unavailable', !match);
                opt.disabled = !match;
            });
        }

        /**
         * 패키지 그룹 체크박스 필터 — OS 뿐 아니라 선택된 설치 환경의 허용 목록(comps.xml 관계,
         * env option 의 data-group-ids)까지 반영한다. 환경 미선택 시 그룹은 전부 숨긴다
         * (그룹 가용성이 환경에 종속 — 서버 정합 가드와 동일 SSOT).
         */
        function applyPackageGroupFilter() {
            const isoId = oiIsoSelect ? oiIsoSelect.value : '';
            const envOpt = oiEnvironment ? oiEnvironment.selectedOptions[0] : null;
            const allowed = envOpt && envOpt.dataset.groupIds != null
                ? envOpt.dataset.groupIds.split(',').filter(Boolean)
                : null; // null = 환경 미선택
            let visible = 0;
            form.querySelectorAll('.n-pkg-group').forEach(row => {
                const chk = row.querySelector('input[type="checkbox"]');
                const match = !!isoId && row.dataset.isoId === isoId
                    && allowed !== null && !!chk && allowed.indexOf(chk.value) >= 0;
                row.classList.toggle('unavailable', !match);
                if (chk && !match) chk.checked = false;
                if (match) visible++;
            });
            if (oiPkgPlaceholder) {
                oiPkgPlaceholder.hidden = visible > 0;
                oiPkgPlaceholder.textContent = allowed !== null && visible === 0
                    ? '이 설치 환경에서 선택할 수 있는 패키지 그룹이 없습니다.'
                    : '설치 환경을 선택하면 선택 가능한 패키지 그룹이 표시됩니다.';
            }
            refreshPkgToggleAll();
        }

        /* ---- 패키지 그룹 전체 선택 토글 (사용자 지시) ---- */

        const oiPkgToggleAll = document.getElementById('oiPkgToggleAll');

        function visiblePkgChecks() {
            return Array.from(form.querySelectorAll('.n-pkg-group:not(.unavailable) input[type="checkbox"]'));
        }

        // 라벨이 곧 다음 동작: 하나라도 미선택이면 '전체 선택', 전부 선택돼 있으면 '전체 선택 해제'.
        function refreshPkgToggleAll() {
            if (!oiPkgToggleAll) return;
            const checks = visiblePkgChecks();
            oiPkgToggleAll.hidden = checks.length === 0;
            const allChecked = checks.length > 0 && checks.every(chk => chk.checked);
            oiPkgToggleAll.textContent = allChecked ? '전체 선택 해제' : '전체 선택';
            oiPkgToggleAll.dataset.mode = allChecked ? 'clear' : 'all';
        }

        if (oiPkgToggleAll) {
            oiPkgToggleAll.addEventListener('click', () => {
                const selectAll = oiPkgToggleAll.dataset.mode !== 'clear';
                visiblePkgChecks().forEach(chk => { chk.checked = selectAll; });
                refreshPkgToggleAll();
            });
            // 개별 체크 변화에도 라벨 동기화 — 패널 위임 listener.
            const pkgPanel = document.getElementById('oiPackageGroups');
            if (pkgPanel) pkgPanel.addEventListener('change', refreshPkgToggleAll);
        }

        /**
         * 버전 특화 박스(allowSshRoot — Rocky Linux 10 전용) 가시성.
         * 박스의 data-os-name / data-os-version-prefix 와 선택 OS 옵션의 data-* 를 대조한다.
         */
        function dispatchVersionSpecificBox(opt) {
            if (!oiAllowSshBox) return;
            const nameMatches = !!opt && opt.dataset.osName === oiAllowSshBox.dataset.osName;
            const versionMatches = !!opt
                && String(opt.dataset.osVersion || '').indexOf(oiAllowSshBox.dataset.osVersionPrefix) === 0;
            oiAllowSshBox.hidden = !(nameMatches && versionMatches);
            if (oiAllowSshBox.hidden) {
                const chk = document.getElementById('oiAllowSshRoot');
                if (chk) chk.checked = false;
            }
        }

        if (oiOsSelect) oiOsSelect.addEventListener('change', onInstallOsChange);
        // 환경 선택이 바뀌면 허용 패키지 그룹이 달라진다(comps.xml 관계).
        if (oiEnvironment) oiEnvironment.addEventListener('change', applyPackageGroupFilter);

        /* ---- 파티션 행 ---- */

        // 마운트포인트-파일시스템 조합 제약 — 서버 LinuxPartitionRules 와 동일 표(단일 SSOT).
        // FIXED = 마운트포인트별 허용 집합, BLOCKED = 일반 행 금지(고정 전용 + 리눅스 설치 불가 NTFS).
        const FS_CONSTRAINT = {
            FIXED: {'/boot/efi': ['EFI', 'FAT32'], 'swap': ['SWAP']},
            BLOCKED: ['EFI', 'SWAP', 'FAT32', 'NTFS']
        };

        function applyFsConstraint(row) {
            const mountPoint = row.querySelector('.pMountPoint').value.trim();
            const fsSelect = row.querySelector('.pFileSystem');
            const allowed = FS_CONSTRAINT.FIXED[mountPoint] || null;
            Array.from(fsSelect.options).forEach(opt => {
                opt.disabled = allowed ? allowed.indexOf(opt.value) < 0 : FS_CONSTRAINT.BLOCKED.includes(opt.value);
            });
            if (allowed) {
                if (allowed.indexOf(fsSelect.value) < 0) fsSelect.value = allowed[0];
                // 허용이 1개뿐인 고정 마운트(swap)는 select 자체를 비활성 표시 — /boot/efi 는 EFI/FAT32 선택 가능.
                fsSelect.disabled = allowed.length === 1;
            } else {
                fsSelect.disabled = false;
                if (FS_CONSTRAINT.BLOCKED.includes(fsSelect.value)) {
                    fsSelect.value = 'EXT4';
                }
            }
        }

        /** grow 체크 시 크기 입력 비활성화 + 다른 행의 grow 해제 — 파티션이 놓이는 OS 영역 볼륨이 하나라 grow 도 하나(U4-1-3 D3). */
        function onGrowChange(checkbox) {
            const row = checkbox.closest('tr');
            const sizeInput = row.querySelector('.pSize');
            if (checkbox.checked) {
                sizeInput.disabled = true;
                sizeInput.value = '';
                sizeInput.classList.remove('has-error');
                oiPartitionTbody.querySelectorAll('tr').forEach(other => {
                    if (other === row) return;
                    const otherGrow = other.querySelector('.pGrow');
                    if (otherGrow.checked) {
                        otherGrow.checked = false;
                        other.querySelector('.pSize').disabled = false;
                    }
                });
            } else {
                sizeInput.disabled = false;
            }
            refreshOsVolumeTargetHint();
        }

        function addPartitionRow(data) {
            const row = cloneTemplateRow('tplPartitionRow');
            if (!row || !oiPartitionTbody) return;
            const d = data || {};
            row.querySelector('.pMountPoint').value = d.mountPoint || '';
            if (d.fileSystem) row.querySelector('.pFileSystem').value = d.fileSystem;
            row.querySelector('.pSizeUnit').value = d.sizeUnit || 'GB';
            const grow = !!d.isGrow;
            row.querySelector('.pGrow').checked = grow;
            const sizeInput = row.querySelector('.pSize');
            sizeInput.value = (!grow && d.size != null && d.size !== 0) ? d.size : '';
            sizeInput.disabled = grow;

            row.querySelector('.pMountPoint').addEventListener('input', () => applyFsConstraint(row));
            row.querySelector('.pGrow').addEventListener('change', e => onGrowChange(e.target));
            row.querySelector('.pSize').addEventListener('input', refreshOsVolumeTargetHint);
            row.querySelector('.pSizeUnit').addEventListener('change', refreshOsVolumeTargetHint);
            bindRowRemove(row);
            row.querySelector('[data-row-remove]').addEventListener('click', () => setTimeout(refreshOsVolumeTargetHint, 0));
            oiPartitionTbody.appendChild(row);
            applyFsConstraint(row);
            refreshOsVolumeTargetHint();
        }

        /* ---- OS 설치 카드의 대상 볼륨 안내 (U4-1-3) — OsVolumeTargets.describe 의 네 분기 + 용량 하한을 DOM 으로 재현 ---- */
        const oiOsVolumeTargetKind = document.getElementById('oiOsVolumeTargetKind');
        const oiOsVolumeTargetCapacity = document.getElementById('oiOsVolumeTargetCapacity');
        // 문구는 서버가 SSOT(OsVolumeTargetKind · OsVolumeTarget.messageTemplates) — 템플릿을 받아 %d · %s 를 차례로 채운다(CP5 F-1).
        let OS_VOLUME_TARGET_MESSAGES = {};
        try { OS_VOLUME_TARGET_MESSAGES = JSON.parse(window.OS_VOLUME_TARGET_MESSAGES_JSON || '{}') || {}; }
        catch (e) { console.warn('[settingForm] osVolumeTargetMessagesJson 파싱 실패:', e); }
        function fillTemplate(template, args) {
            let i = 0;
            return String(template || '').replace(/%[ds]/g, () => String(args[i++] != null ? args[i - 1] : ''));
        }
        const MSG_PARTITIONS_OVER = 'OS 설치 파티션 크기 합이 OS 영역 볼륨의 최소 용량을 넘습니다 — RAID 구성 묶음의 용량 · 개수와 파티션 크기를 맞추세요.';
        const DISK_UNIT_BYTES = {GB: 1e9, TB: 1e12};                                   // DiskCapacityUnit — 십진
        const SIZE_UNIT_BYTES = {MB: 1048576, GB: 1073741824, TB: 1099511627776};      // SizeUnit — 이진(MiB · GiB · TiB)

        function raidCardActive() {
            const card = cardOf('RAID_CONFIGURATION');
            return !!card && !card.hidden;
        }
        /** 묶음 행 하나의 볼륨 유효 용량 하한(바이트) — DiskGroupRuleRequest.usableCapacityLowerBoundBytes 의 사본. 모르면 null. */
        function diskGroupLowerBound(row) {
            if (row.querySelector('.dgCapacityMode').value !== 'SPECIFIED') return null;
            const size = intOrNull(row.querySelector('.dgCapacitySize').value);
            const unit = row.querySelector('.dgCapacityUnit').value;
            if (size == null || size < 1 || !DISK_UNIT_BYTES[unit]) return null;
            const perDisk = size * DISK_UNIT_BYTES[unit];
            if (!rowBuildsRaid(row)) return perDisk;
            const levelOpt = selectedOption(row.querySelector('.dgLevel'));
            const count = intOrNull(row.querySelector('.dgCount').value);
            if (!levelOpt || count == null) return null;
            // RaidLevel.usableDisks — 계수 a · b 는 레벨 옵션 data-* 로 서버가 내린다(SSOT = enum)
            const usable = Math.floor(parseFloat(levelOpt.dataset.usableA || '0') * count + parseInt(levelOpt.dataset.usableB || '0', 10));
            return usable <= 0 ? null : perDisk * usable;
        }
        /** 묶음 요약 — OsVolumeTargets.summarize 와 같은 형태. */
        function diskGroupSummary(row) {
            const levelSel = row.querySelector('.dgLevel');
            const cap = row.querySelector('.dgCapacityMode').value === 'SPECIFIED'
                ? (row.querySelector('.dgCapacitySize').value || '?') + ' ' + row.querySelector('.dgCapacityUnit').value
                : '자동 탐지';
            // 모드 표기(개 · 개씩 · 개 이상)는 select 옵션 텍스트 = 서버 DiskCountMode.suffix 그대로(E3.5-7-a)
            const cnt = (row.querySelector('.dgCount').value || '?') + selectedOption(row.querySelector('.dgCountMode')).textContent;
            return [levelSel.value ? selectedOption(levelSel).textContent : 'RAID 없음',
                selectedOption(row.querySelector('.dgType')).textContent,
                selectedOption(row.querySelector('.dgTransport')).textContent, cap, cnt].join(' · ');
        }
        /** 네 분기 판정 — {kind, ruleNo, summary, bound}. bound null = 모름. */
        function describeOsVolumeTarget() {
            if (!raidCardActive() || !rcDiskGroupTbody) return {kind: 'NONE'};
            const rows = diskGroupRows();
            if (!rows.length) return {kind: 'NONE'};
            const fixedIdx = rows.findIndex(r => r.querySelector('.dgRole').value === 'OS');
            const candidates = fixedIdx >= 0 ? [rows[fixedIdx]] : rows.filter(r => r.querySelector('.dgRole').value === 'BY_PRIORITY');
            if (!candidates.length) return {kind: 'NO_CANDIDATE'};
            let bound = Infinity;
            for (const r of candidates) {
                const b = diskGroupLowerBound(r);
                if (b == null) { bound = null; break; }
                bound = Math.min(bound, b);
            }
            return fixedIdx >= 0
                ? {kind: 'FIXED', ruleNo: fixedIdx + 1, summary: diskGroupSummary(rows[fixedIdx]), bound: bound}
                : {kind: 'BY_PRIORITY', bound: bound};
        }
        function fixedPartitionBytes() {
            let sum = 0;
            (oiPartitionTbody ? oiPartitionTbody.querySelectorAll('tr') : []).forEach(row => {
                if (row.querySelector('.pGrow').checked) return;
                const size = intOrNull(row.querySelector('.pSize').value) || 0;
                sum += size * (SIZE_UNIT_BYTES[row.querySelector('.pSizeUnit').value] || 0);
            });
            return sum;
        }
        function hasGrowPartition() {
            return !!oiPartitionTbody && Array.from(oiPartitionTbody.querySelectorAll('tr')).some(r => r.querySelector('.pGrow').checked);
        }
        function formatDecimal(bytes) {
            const trim = v => { const t = v.toFixed(1); return t.endsWith('.0') ? t.slice(0, -2) : t; };
            return bytes >= 1e12 ? trim(bytes / 1e12) + ' TB' : trim(bytes / 1e9) + ' GB';
        }
        function formatBinary(bytes) {
            const t = (bytes / 1073741824).toFixed(1);
            return (t.endsWith('.0') ? t.slice(0, -2) : t) + ' GiB';
        }
        /** 파티션 고정 합이 하한을 넘는가 — SettingSaveRequest.isPartitionsWithinOsVolume 의 사본(하한을 모르면 false). */
        function partitionsOverOsVolume() {
            const target = describeOsVolumeTarget();
            if (target.bound == null || !isFinite(target.bound)) return false;
            const fixed = fixedPartitionBytes();
            return hasGrowPartition() ? fixed >= target.bound : fixed > target.bound;
        }
        function refreshOsVolumeTargetHint() {
            if (!oiOsVolumeTargetKind) return;
            const target = describeOsVolumeTarget();
            const M = OS_VOLUME_TARGET_MESSAGES;
            oiOsVolumeTargetKind.textContent = target.kind === 'FIXED'
                ? fillTemplate(M.FIXED, [target.ruleNo, target.summary])
                : (M[target.kind] || '');
            oiOsVolumeTargetKind.classList.toggle('is-danger', target.kind === 'NO_CANDIDATE');
            const hasTarget = target.kind === 'FIXED' || target.kind === 'BY_PRIORITY';
            oiOsVolumeTargetCapacity.hidden = !hasTarget;
            if (hasTarget) {
                const over = partitionsOverOsVolume();
                oiOsVolumeTargetCapacity.textContent = target.bound == null
                    ? (M.CAPACITY_UNKNOWN || '')
                    : fillTemplate(M.CAPACITY_FORMAT, [formatDecimal(target.bound), formatBinary(fixedPartitionBytes()),
                        hasGrowPartition() ? (M.GROW_SUFFIX || '') : '']) + (over ? (M.OVER_SUFFIX || '') : '');
                oiOsVolumeTargetCapacity.classList.toggle('is-danger', over);
            }
        }

        /** 기본 파티션 자동 생성 — GET /provisioning/setting/default-partitions?osName=... */
        async function loadDefaultPartitions() {
            const opt = selectedOption(oiOsSelect);
            if (!opt || !oiOsSelect.value) return;
            if (oiPartitionTbody.children.length > 0
                && !window.confirm('기존 파티션 구성이 초기화됩니다. 계속하시겠습니까?')) return;

            const url = form.dataset.partitionsEndpoint
                + '?osName=' + encodeURIComponent(opt.dataset.osName || '');
            let resp;
            try {
                resp = await fetch(url, {headers: {'Accept': 'application/json'}});
            } catch (e) {
                if (window.ErrorModal) window.ErrorModal.show({message: '서버와 통신할 수 없습니다: ' + e.message, status: 0});
                return;
            }
            if (!resp.ok) {
                if (window.ErrorModal) await window.ErrorModal.fromResponse(resp, {fallback: '기본 파티션 정보를 불러오지 못했습니다.'});
                return;
            }
            const presets = await resp.json().catch(() => []);
            oiPartitionTbody.innerHTML = '';
            presets.forEach(p => addPartitionRow({
                mountPoint: p.mountPoint,
                fileSystem: p.fileSystem,
                size: p.size,
                sizeUnit: p.sizeUnit,
                isGrow: pickBool(p, 'isGrow', 'grow')
            }));
        }

        if (oiDefaultPartitions) oiDefaultPartitions.addEventListener('click', loadDefaultPartitions);
        const oiAddPartition = document.getElementById('oiAddPartition');
        if (oiAddPartition) oiAddPartition.addEventListener('click', () => addPartitionRow());

        /* ---- RAID 구성 단계 — RAID 카드 + 디스크 묶음 규칙 (U4-1-1 v2) ---- */

        // 판정 재료는 전부 서버가 준다 — 카드는 raidCardMetaJson(hasCache / supportedLevels / blockReasons),
        // 레벨 최소치는 레벨 옵션의 data-min-disks(-cached). 이 JS 는 문구를 짓지 않고 읽어서 잠그기만 한다
        // (SSOT = SupportedRaidLevels.blockReasonFor · RaidLevel.minimumDisks — 서버 DiskGroupRules 가 같은 표로 안전망).
        const RAID_CARD_BY_ID = {};
        (function () {
            let groups = [];
            try { groups = JSON.parse(window.RAID_CARD_META_JSON || '[]'); }
            catch (e) { console.warn('[settingForm] raidCardMetaJson 파싱 실패:', e); }
            (groups || []).forEach(group => (group.cards || []).forEach(card => {
                RAID_CARD_BY_ID[String(card.id)] = card;
            }));
        })();
        const MSG_PICK_CARD_FIRST = 'RAID 카드를 먼저 선택하세요.';
        const MSG_CARD_LOCKED = 'RAID 묶음이 있어 해제할 수 없습니다 — 묶음의 레벨을 먼저 비우세요.';
        const MSG_CARD_REQUIRED = 'RAID 를 구성하는 묶음이 있으므로 RAID 카드를 지정해야 합니다.';
        const MSG_HDD_NO_NVME = 'HDD 에는 NVMe 전송 방식이 없습니다.';
        // U4-1-2 — 역할 · 우선순위 문구. 서버 DiskGroupRules 7 · RaidConfigurationRequest.isVolumePriorityDistinct ·
        // SettingSaveRequest.isOsVolumeDeterminable 과 같은 뜻.
        const MSG_OS_FIXED_ELSEWHERE = '번 묶음이 이미 OS 영역으로 고정되어 있습니다 — OS 영역은 한 묶음만 고정할 수 있습니다.';
        const MSG_NO_OS_CANDIDATE_WITH_INSTALL = 'OS 영역이 될 수 있는 묶음(OS 영역 고정 또는 우선순위에 따름)이 없습니다 — OS 설치 단계가 있으면 저장할 수 없습니다.';
        const MSG_NO_OS_CANDIDATE = 'OS 영역이 될 수 있는 묶음이 없습니다 — OS 영역은 설치기가 자동 선택합니다.';
        const MSG_PRIORITY_DUPLICATE = '이미 있는 조합입니다';
        const MSG_PRIORITY_EXHAUSTED = '종류 · 전송의 유효 조합(5)을 모두 썼습니다 — 행을 지운 뒤 추가하세요.';
        const MSG_PRIORITY_EMPTY_WITH_INSTALL = 'OS 설치 단계가 있으면 OS 영역이 될 수 있는 묶음(OS 고정 또는 우선순위에 따름 + 우선순위 행)이 있어야 합니다.';
        // 표 아래 상시 안내는 짧게 — 제출 시 붙는 오류(위 전문)와 같은 문장이 두 번 보이지 않게(CP5 O-1).
        const HINT_PRIORITY_EMPTY_WITH_INSTALL = 'OS 설치 단계가 있으므로 우선순위 행을 두거나 묶음 하나를 OS 영역으로 고정해야 저장됩니다.';
        const MSG_PRIORITY_EMPTY = '우선순위 행이 없으면 볼륨은 열거 순서대로 놓입니다.';
        const PRIORITY_VALID_COMBOS = 5; // 종류 2 × 전송 3 − HDD×NVMe
        let DEFAULT_VOLUME_PRIORITIES = [];
        try { DEFAULT_VOLUME_PRIORITIES = JSON.parse(window.DEFAULT_VOLUME_PRIORITIES_JSON || '[]') || []; }
        catch (e) { console.warn('[settingForm] defaultVolumePrioritiesJson 파싱 실패:', e); }

        function selectedRaidCard() {
            return rcRaidCard && rcRaidCard.value ? (RAID_CARD_BY_ID[rcRaidCard.value] || null) : null;
        }
        function diskGroupRows() {
            // E3.5-6 — VD 파라미터 서브 행(tr.dgVdRow)은 규칙 행이 아니다: 규칙 번호 · 조립 · 포섭 판정에서 제외
            return rcDiskGroupTbody ? Array.from(rcDiskGroupTbody.querySelectorAll('tr:not(.dgVdRow)')) : [];
        }
        /** 행이 RAID 를 구성하는가 — 레벨 값이 있으면 (OSInstallationRequest.requiresRaidCard 의 행 단위). */
        function rowBuildsRaid(row) {
            return !!row.querySelector('.dgLevel').value;
        }
        function levelMinDisks(levelOpt, hasCache) {
            if (!levelOpt || !levelOpt.value) return 1;
            return parseInt(hasCache ? levelOpt.dataset.minDisksCached : levelOpt.dataset.minDisks, 10) || 1;
        }
        /** 병기형 조사를 앞 글자로 해소한다(HF5 의 KoreanParticle — 서버 RaidLevel.objectParticle 과 같은 규칙: 5 → 를, 0·6·10 → 을). */
        function particles(text) {
            return window.KoreanParticle ? window.KoreanParticle.resolve(text) : text;
        }
        function setOptionState(opt, reason) {
            opt.disabled = !!reason;
            if (reason) opt.title = reason; else opt.removeAttribute('title');
        }

        /**
         * 진리표(plan D7) 를 한 번에 적용한다 — 카드 · 행 어느 쪽이 바뀌어도 이것 하나만 부른다.
         *  카드 미선택      → 각 행의 RAID 옵션 disabled(카드 먼저)
         *  카드 선택됨      → 못 만드는 레벨 disabled(blockReasons) · 개수 하한 = minimumDisks(hasCache)
         *  RAID 묶음 존재   → '선택 안 함' disabled · 그 레벨을 못 만드는 카드 옵션 disabled
         */
        function selectedExistingPolicy() {
            const checked = document.querySelector('input[name="rcExistingPolicyRadio"]:checked');
            return checked ? checked.value : null;
        }

        // E3.5-4 — 축 라디오 잠금. 판정 재료는 서버 @AssertTrue(existingPolicyPresentWhenRequired)와 같다
        // (RAID 를 구성하는 묶음 유무). 잠글 때 선택을 지우지는 않는다 — 값이 있어도 서버가 허용한다.
        function applyExistingPolicyLock(required) {
            document.querySelectorAll('input[name="rcExistingPolicyRadio"]')
                .forEach(radio => { radio.disabled = !required; });
            const lockHint = document.getElementById('rcExistingPolicyHint');
            if (lockHint) lockHint.hidden = required;
            applyDestroyWarning();
        }

        // 파괴 선택은 디스크 데이터 소실로 이어진다 — 선택 즉시 상시 경고(CP6 검수 반영).
        // 잠금 · pre-fill 경로는 applyExistingPolicyLock 이, 직접 클릭은 change 리스너가 부른다.
        function applyDestroyWarning() {
            const destroyWarning = document.getElementById('rcDestroyWarning');
            if (!destroyWarning) return;
            const anyRadio = document.querySelector('input[name="rcExistingPolicyRadio"]');
            destroyWarning.hidden = !anyRadio || anyRadio.disabled || selectedExistingPolicy() !== 'DESTROY';
        }

        // E3.5-4 규칙 8 미러 — DiskGroupRules.covers · countCovers 와 같은 진리표(드리프트 0). 개수 축 3×3(E3.5-7-a D3):
        // '개'(EXACT) 는 한 묶음만 가져가 남기므로 어떤 후행도 포섭하지 않는다 · '개씩'(EACH) 은 같은 n 의 '개씩' 만 ·
        // '개 이상'(AT_LEAST) 은 n ≤ m 인 모든 후행. 겹침(후행 흘림)은 막지 않고 완전 포섭만 알린다.
        function coversRule(prior, later) {
            if (prior.diskType !== 'AUTO' && prior.diskType !== later.diskType) return false;
            if (prior.transport !== 'AUTO' && prior.transport !== later.transport) return false;
            if (prior.capacity.mode === 'SPECIFIED') {
                if (later.capacity.mode !== 'SPECIFIED'
                    || prior.capacity.size !== later.capacity.size
                    || prior.capacity.unit !== later.capacity.unit) return false;
            }
            switch (prior.count.mode) {
                case 'EXACT': return false;
                case 'EACH': return later.count.mode === 'EACH' && prior.count.value === later.count.value;
                default: return prior.count.value <= later.count.value;   // AT_LEAST
            }
        }

        /**
         * 규칙 8 판정 — {unreachable: Set<행 index>, message}. 문구는 첫 발견 쌍만(서버 unreachableRule 의 400 문장과
         * 같은 모양) · 색상은 피포섭 행 전부(CP6 검수). 다섯 축이 완전히 같은 쌍은 규칙 4(중복)의 몫이라 여기서 뺀다 —
         * 서버 validate 도 같은 순서(중복 → 규칙 8)로 보므로 화면이 말하는 이유와 서버가 거절하는 이유가 같아진다(CP5 F-3).
         */
        function unreachableDiskGroupFindings() {
            const rows = diskGroupRows();
            const groups = buildDiskGroups();
            const identities = rows.map(diskGroupIdentity);
            let message = null;
            const unreachable = new Set();
            for (let j = 1; j < groups.length; j++) {
                for (let i = 0; i < j; i++) {
                    if (identities[i] === identities[j]) continue;
                    if (coversRule(groups[i], groups[j])) {
                        unreachable.add(j);
                        if (!message) message = (j + 1) + '번 묶음은 ' + (i + 1) + '번 묶음에 가려 도달할 수 없습니다 — 순서를 바꾸거나 조건을 좁히십시오.';
                        break;
                    }
                }
            }
            return {unreachable: unreachable, message: message};
        }

        function applyUnreachableFindings() {
            const findingsHint = document.getElementById('rcUnreachableHint');
            if (!findingsHint) return;
            const found = unreachableDiskGroupFindings();
            diskGroupRows().forEach((row, idx) => row.classList.toggle('is-unreachable', found.unreachable.has(idx)));
            findingsHint.hidden = !found.message;
            findingsHint.textContent = found.message || '';
        }

        function applyDiskGroupConstraints() {
            if (!rcRaidCard) return;
            const card = selectedRaidCard();
            const raidRows = diskGroupRows().filter(rowBuildsRaid);
            applyExistingPolicyLock(raidRows.length > 0);   // E3.5-4 축 잠금(UI 1차 차단)
            applyUnreachableFindings();                     // E3.5-4 규칙 8 미러
            // E3.5-6 — VD 파라미터 잠금 진리표: 지원 계열(supportsVdParameters) × RAID 구성 행에서만
            const vdSupported = !!(card && card.supportsVdParameters);
            diskGroupRows().forEach(row => applyVdParamsLock(row, vdSupported));

            // 1) 카드 select 의 옵션 잠금
            Array.from(rcRaidCard.options).forEach(opt => {
                if (opt.hasAttribute('data-none')) {
                    setOptionState(opt, raidRows.length ? MSG_CARD_LOCKED : null);
                    return;
                }
                const meta = RAID_CARD_BY_ID[opt.value];
                if (!meta) return;
                let reason = null;
                raidRows.some(row => {
                    const level = row.querySelector('.dgLevel').value;
                    if ((meta.supportedLevels || []).indexOf(level) >= 0) return false;
                    reason = particles((diskGroupRows().indexOf(row) + 1) + '번 묶음의 ' + level + ' 을(를) 만들 수 없는 카드입니다.');
                    return true;
                });
                setOptionState(opt, reason);
            });

            // 2) 각 행 — 순위 · 레벨 옵션 · 개수 하한 · 용량 입력 표시
            diskGroupRows().forEach((row, i) => {
                row.querySelector('.dgRank').textContent = String(i + 1); // 순위 열(E3.5-7-a) — 행 순서 = 적용 순서의 명시
                const levelSel = row.querySelector('.dgLevel');
                Array.from(levelSel.options).forEach(opt => {
                    if (!opt.value) { setOptionState(opt, null); return; }
                    if (!card) { setOptionState(opt, MSG_PICK_CARD_FIRST); return; }
                    setOptionState(opt, (card.blockReasons || {})[opt.value] || null);
                });
                const countInput = row.querySelector('.dgCount');
                const min = rowBuildsRaid(row) && card ? levelMinDisks(selectedOption(levelSel), card.hasCache) : 1;
                countInput.min = String(min);
                // 최소치는 셀 안 상시 문구 대신 placeholder · title 로만 알린다(CP7 검수) — 문구는 서버 tooFewDisks 와 같은 뜻.
                countInput.placeholder = min > 1 ? min + ' 이상' : '1';
                countInput.title = rowBuildsRaid(row) && card && min > 1
                    ? particles(selectedOption(levelSel).textContent + ' 은(는) 디스크 ' + min + '개 이상이 필요합니다'
                        + (card.hasCache ? '' : ' (캐시 없는 카드)') + '.')
                    : '';
                const value = intOrNull(countInput.value);
                const short = value != null && value < min;
                countInput.classList.toggle('has-error', short);
                // 종류 ↔ 전송 정합 — HDD 에는 NVMe 전송이 없다(DiskGroupRules 6 · CP7 검수). SAS SSD 는 실재하므로 막지 않는다.
                const typeSel = row.querySelector('.dgType');
                const transportSel = row.querySelector('.dgTransport');
                Array.from(transportSel.options).forEach(opt => {
                    setOptionState(opt, typeSel.value === 'HDD' && opt.value === 'NVME' ? MSG_HDD_NO_NVME : null);
                });
                transportSel.classList.toggle('has-error', typeSel.value === 'HDD' && transportSel.value === 'NVME');
                const capAuto = row.querySelector('.dgCapacityMode').value !== 'SPECIFIED';
                row.querySelector('.dgCapacityValue').hidden = capAuto; // '지정' 일 때만 아래 줄에 값 · 단위
            });

            // 2-b) 동일 규칙 중복 — 다섯 축이 같은 행을 강조하고 사유를 적는다(DiskGroupRules 4 의 1차 차단, CP5 C5 보강).
            markDuplicateDiskGroups();

            // 2-c) 역할(U4-1-2) — OS 영역 고정은 한 묶음만(DiskGroupRules 7): 어떤 행이 OS 면 다른 행의 OS 옵션을 잠근다.
            const osRow = diskGroupRows().find(row => row.querySelector('.dgRole').value === 'OS') || null;
            const osRowNo = osRow ? diskGroupRows().indexOf(osRow) + 1 : 0;
            diskGroupRows().forEach(row => {
                const roleSel = row.querySelector('.dgRole');
                const osOpt = roleSel.querySelector('option[value="OS"]');
                if (osOpt) setOptionState(osOpt, osRow && row !== osRow ? osRowNo + MSG_OS_FIXED_ELSEWHERE : null);
            });
            // OS 후보(OS 고정 · 우선순위에 따름)가 하나도 없으면 안내 — OS 설치 카드가 있으면 저장이 막힌다(isOsVolumeDeterminable).
            if (rcNoOsCandidateHint) {
                const rows = diskGroupRows();
                const noCandidate = rows.length > 0 && !rows.some(row => ['OS', 'BY_PRIORITY'].indexOf(row.querySelector('.dgRole').value) >= 0);
                rcNoOsCandidateHint.textContent = noCandidate ? (osInstallCardActive() ? MSG_NO_OS_CANDIDATE_WITH_INSTALL : MSG_NO_OS_CANDIDATE) : '';
                rcNoOsCandidateHint.hidden = !noCandidate;
            }
            applyPriorityConstraints();


            // 3) 카드 select 아래 안내 — 카드가 없는데 RAID 묶음이 있으면(pre-fill 소실 등) 요구 사실을 보인다
            if (rcRaidCardHint) {
                const needsCard = !card && raidRows.length > 0;
                rcRaidCardHint.textContent = needsCard ? MSG_CARD_REQUIRED : '';
                rcRaidCardHint.hidden = !needsCard;
            }
            refreshOsVolumeTargetHint(); // OS 설치 카드의 대상 볼륨 안내(U4-1-3) — 역할 · 용량 · 개수 · 행 추가/삭제 · 카드 유무가 재료
        }

        /** 다섯 축의 정규 표기 — 서버 DiskGroupRules.identity 와 같은 축을 본다. */
        function diskGroupIdentity(row) {
            const capMode = row.querySelector('.dgCapacityMode').value;
            return [
                row.querySelector('.dgLevel').value || 'null',
                row.querySelector('.dgType').value,
                row.querySelector('.dgTransport').value,
                capMode === 'SPECIFIED'
                    ? (row.querySelector('.dgCapacitySize').value || '') + row.querySelector('.dgCapacityUnit').value
                    : 'AUTO',
                row.querySelector('.dgCountMode').value + ':' + (row.querySelector('.dgCount').value || '')
            ].join('|');
        }

        /** 같은 규칙인 행 쌍을 찾아 [{row, sameAsNo}] 로 돌려주고, 행의 레벨 select 에 has-error 를 칠한다. */
        function markDuplicateDiskGroups() {
            const seen = new Map();
            const duplicates = [];
            diskGroupRows().forEach((row, i) => {
                const key = diskGroupIdentity(row);
                const levelSel = row.querySelector('.dgLevel');
                if (seen.has(key)) {
                    duplicates.push({row: row, no: i + 1, sameAsNo: seen.get(key)});
                    levelSel.classList.add('has-error');
                    levelSel.title = (i + 1) + '번 묶음이 ' + seen.get(key) + '번 묶음과 같은 규칙입니다.';
                } else {
                    seen.set(key, i + 1);
                    levelSel.classList.remove('has-error');
                    levelSel.removeAttribute('title');
                }
            });
            return duplicates;
        }

        function addDiskGroupRow(data) {
            const row = cloneTemplateRow('tplDiskGroupRow');
            if (!row || !rcDiskGroupTbody) return;
            const d = data || {};
            row.querySelector('.dgLevel').value = d.raidLevel || '';
            if (d.diskType) row.querySelector('.dgType').value = d.diskType;
            if (d.transport) row.querySelector('.dgTransport').value = d.transport;
            const cap = d.capacity || {};
            row.querySelector('.dgCapacityMode').value = cap.mode === 'SPECIFIED' ? 'SPECIFIED' : 'AUTO';
            row.querySelector('.dgCapacitySize').value = cap.size != null ? cap.size : '';
            if (cap.unit) row.querySelector('.dgCapacityUnit').value = cap.unit;
            const cnt = d.count || {};
            row.querySelector('.dgCount').value = cnt.value != null ? cnt.value : '';
            if (cnt.mode) row.querySelector('.dgCountMode').value = cnt.mode;
            // 역할(U4-1-2) — 저장본에 없으면(구 payload) '우선순위에 따름'. 스펙 축이 아니라 해석 규칙이라 기본값이 정당하다.
            row.querySelector('.dgRole').value = d.role || 'BY_PRIORITY';
            // RAID 없음 묶음의 개수는 '1개 이상'(제약 없음 = 그 스펙 디스크 각각이 볼륨)이 중립값이라 비어 있으면 채운다 —
            // 디스크 스펙(종류 · 전송 · 용량)에는 기본값을 두지 않는다(E16). 사용자는 언제든 고칠 수 있다.
            defaultCountForNoRaid(row);

            ['.dgLevel', '.dgCapacityMode', '.dgCount', '.dgCountMode', '.dgType', '.dgTransport', '.dgCapacitySize', '.dgCapacityUnit', '.dgRole']
                .forEach(sel => row.querySelector(sel).addEventListener('change', applyDiskGroupConstraints));
            row.querySelector('.dgCount').addEventListener('input', applyDiskGroupConstraints);
            // 사용자가 개수 · 모드를 직접 만지면 자동값 표식을 지운다 — 이후 레벨 변경이 그 값을 되돌리지 않는다
            row.querySelector('.dgCount').addEventListener('input', () => delete row.dataset.countDefaulted);
            row.querySelector('.dgCountMode').addEventListener('change', () => delete row.dataset.countDefaulted);
            row.querySelector('.dgLevel').addEventListener('change', () => { defaultCountForNoRaid(row); applyDiskGroupConstraints(); });
            bindDiskGroupDrag(row);
            bindRowRemove(row);
            row.querySelector('[data-row-remove]').addEventListener('click', () => setTimeout(applyDiskGroupConstraints, 0));
            rcDiskGroupTbody.appendChild(row);
            attachVdParamsRow(row, d.vdParameters);   // E3.5-6 — 서브 행은 본 행 바로 뒤
            applyDiskGroupConstraints();
        }

        /* ── E3.5-6 VD 파라미터 서브 행 ─────────────────────────────────────
           본 행 바로 뒤 tr.dgVdRow(기본 접힘). 축마다 값이 항상 있다 — 템플릿의 selected 옵션이 서버 enum 의
           DEFAULT(9361-8i HII 기본값)이고, 고르지 않은 축도 그 값으로 명시 전송된다(2026-09-02 미지정 폐지).
           값 잠금(Q1 판정): 지원 밖(카드 미지원 · RAID 없음)이면 화면 값은 보존하고 전송에서만 제외한다 —
           서버 가드(규칙 9)는 direct POST 방어. */
        const VD_AXES = ['vdStripSize', 'vdReadPolicy', 'vdWritePolicy', 'vdIoPolicy',
                         'vdAccessPolicy', 'vdDriveCache', 'vdBackgroundInit', 'vdInitialization'];
        const VD_FIELDS = ['stripSize', 'readPolicy', 'writePolicy', 'ioPolicy',
                           'accessPolicy', 'driveCache', 'backgroundInit', 'initialization'];

        function attachVdParamsRow(row, saved) {
            const vdRow = cloneTemplateRow('tplVdParamsRow');
            if (!vdRow) return;
            row._vdRow = vdRow;
            row.parentNode.insertBefore(vdRow, row.nextSibling);
            VD_AXES.forEach((cls, i) => {
                const sel = vdRow.querySelector('.' + cls);
                // 저장값이 있으면 복원, 없으면 템플릿의 selected(HII 기본값)가 그대로 선다. 선택지에 없는 값은 기본값으로 둔다.
                const v = saved && saved[VD_FIELDS[i]];
                if (v && sel.querySelector('option[value="' + v + '"]')) sel.value = v;
                sel.addEventListener('change', applyDiskGroupConstraints);
            });
            const toggle = row.querySelector('.dgVdToggle');
            toggle.addEventListener('click', () => { vdRow.hidden = !vdRow.hidden; });
            row.querySelector('[data-row-remove]').addEventListener('click', () => vdRow.remove());
        }

        /** 드래그 뒤 서브 행을 제 본 행 뒤로 재정렬 — RowDrag 는 본 행만 옮긴다. */
        function reattachVdRows() {
            diskGroupRows().forEach(row => {
                if (row._vdRow && row.nextSibling !== row._vdRow) {
                    row.parentNode.insertBefore(row._vdRow, row.nextSibling);
                }
            });
        }

        /** 템플릿의 selected 옵션 = 서버 enum 의 DEFAULT(HII 기본값) — 배지 · SSD 잠금 되돌림의 기준. */
        function vdDefaultOf(sel) {
            const opt = Array.from(sel.options).find(o => o.defaultSelected);
            return opt ? opt.value : '';
        }

        /** 기본값과 다른 축 수 — ⚙ 배지의 숫자. 0 이면 8축 전부 HII 기본값으로 전송된다. */
        function vdChangedCount(row) {
            if (!row._vdRow) return 0;
            return VD_AXES.filter(cls => {
                const sel = row._vdRow.querySelector('.' + cls);
                return sel && sel.value !== vdDefaultOf(sel);
            }).length;
        }

        /** 행의 VD 파라미터 전송값 — 지원 계열 × RAID 구성 행이면 8축 전부(기본값 포함) 명시, 잠금이면 null(축 없음). */
        function vdParametersOf(row, cardSupports) {
            if (!cardSupports || !rowBuildsRaid(row) || !row._vdRow) return null;
            const out = {};
            VD_AXES.forEach((cls, i) => { out[VD_FIELDS[i]] = row._vdRow.querySelector('.' + cls).value; });
            return out;
        }

        function applyVdParamsLock(row, cardSupports) {
            const toggle = row.querySelector('.dgVdToggle');
            if (!toggle) return;
            const usable = cardSupports && rowBuildsRaid(row);
            toggle.disabled = !usable;
            // 잠금 사유는 래퍼의 data-tooltip(프로젝트 tooltip) — 네이티브 title 을 쓰지 않는다(CP6 검수)
            const wrap = toggle.closest('.dgVdToggleWrap');
            if (wrap) {
                if (usable) delete wrap.dataset.tooltip;
                else wrap.dataset.tooltip = rowBuildsRaid(row)
                    ? '이 카드 계열은 VD 파라미터를 지원하지 않습니다'
                    : 'RAID 를 구성하지 않는 묶음에는 지정할 수 없습니다';
            }
            if (!usable && row._vdRow) row._vdRow.hidden = true;   // 값은 보존 — 전송만 제외(Q1)
            // Drive Cache — SSD 묶음은 카드가 Unchanged 고정(CP6 검수): 기본값(Unchanged)으로 되돌리고 잠근다(서버 규칙 9 와 같은 판정)
            if (row._vdRow) {
                const dc = row._vdRow.querySelector('.vdDriveCache');
                const ssd = row.querySelector('.dgType').value === 'SSD';
                dc.disabled = ssd;
                if (ssd) dc.value = vdDefaultOf(dc);
                dc.title = ssd ? 'SSD 볼륨의 Drive Cache 는 카드가 Unchanged 로 고정합니다' : '';
            }
            const badge = row.querySelector('.dgVdBadge');
            if (badge) {
                const n = vdChangedCount(row);
                badge.hidden = !(usable && n > 0);
                badge.textContent = n;
            }
        }

        function defaultCountForNoRaid(row) {
            const countInput = row.querySelector('.dgCount');
            const modeSel = row.querySelector('.dgCountMode');
            if (rowBuildsRaid(row)) {
                // 신규 행은 레벨이 'RAID 없음' 으로 시작해 자동값(1 · 개 이상)을 받는다. 레벨을 RAID 로 바꾸는 순간 그 자동값을
                // 물려주면 기본이 '개 이상' 이 된다(CP5 F-1) — 사용자가 손대지 않은 자동값이면 첫 옵션 '개' + 빈 개수로 되돌린다(D1).
                if (row.dataset.countDefaulted === 'noraid') {
                    countInput.value = '';
                    modeSel.selectedIndex = 0;
                    delete row.dataset.countDefaulted;
                }
                return;
            }
            if (countInput.value.trim() !== '') return;
            countInput.value = '1';
            modeSel.value = 'AT_LEAST';
            row.dataset.countDefaulted = 'noraid';
        }

        /* 행 순서 — 맨 앞 ☰ 핸들을 끌어 옮긴다. 묶음 번호(N번)와 중복 판정이 순서를 따르므로 놓은 뒤
           진리표를 다시 적용한다. 드래그 자체의 구현은 공용 모듈(global/row-drag.js)이 갖는다 — 펌웨어
           버전 목록(E2-1-a)과 같은 조작감을 쓰기 위해 E2-1-a 에서 끌어올렸다. */
        function bindDiskGroupDrag(row) {
            bindRowDrag(row, rcDiskGroupTbody, () => { reattachVdRows(); applyDiskGroupConstraints(); });
        }
        /** 표 공용 행 드래그 — 묶음 표(U4-1-1)와 우선순위 표(U4-1-2)가 같은 공용 모듈을 쓴다. */
        function bindRowDrag(row, tbody, onDrop) {
            RowDrag.bind({
                item: row, container: tbody, itemSelector: 'tr',
                handle: '.dgHandle', payload: 'setting-row', onDrop: onDrop
            });
        }

        function buildDiskGroups() {
            const card = selectedRaidCard();
            const vdSupported = !!(card && card.supportsVdParameters);
            return diskGroupRows().map(row => {
                const capMode = row.querySelector('.dgCapacityMode').value;
                return {
                    raidLevel: row.querySelector('.dgLevel').value || null,
                    diskType: row.querySelector('.dgType').value,
                    transport: row.querySelector('.dgTransport').value,
                    capacity: capMode === 'SPECIFIED'
                        ? {mode: 'SPECIFIED', size: intOrNull(row.querySelector('.dgCapacitySize').value), unit: row.querySelector('.dgCapacityUnit').value}
                        : {mode: 'AUTO', size: null, unit: null},
                    count: {mode: row.querySelector('.dgCountMode').value, value: intOrNull(row.querySelector('.dgCount').value)},
                    role: row.querySelector('.dgRole').value,
                    vdParameters: vdParametersOf(row, vdSupported)   // E3.5-6 — 지원 계열 × RAID 행은 8축 명시, 잠금이면 null
                };
            });
        }

        /* ---- 볼륨 우선순위 표 (U4-1-2) — 행 순서 = 우선순위. 종류 · 전송 · 용량 순서 ---- */
        function priorityRows() {
            return rcPriorityTbody ? Array.from(rcPriorityTbody.querySelectorAll('tr')) : [];
        }
        function osInstallCardActive() {
            const card = cardOf('OS_INSTALLATION');
            return !!card && !card.hidden;
        }
        function priorityIdentity(row) {
            return row.querySelector('.vpType').value + '|' + row.querySelector('.vpTransport').value;
        }
        /**
         * 우선순위 진리표 — HDD 행의 NVMe 옵션 잠금 · (종류, 전송) 중복 행 강조 · 유효 조합 소진 시 추가 버튼 잠금 ·
         * 0 행 안내(OS 설치 카드가 있으면 저장 불가 문구). 서버 @AssertTrue 들과 같은 판정.
         */
        function applyPriorityConstraints() {
            if (!rcPriorityTbody) return;
            const rows = priorityRows();
            const seen = new Map();
            rows.forEach((row, i) => {
                row.querySelector('.vpRank').textContent = String(i + 1); // 순위 열 — 행 순서의 명시(CP6 검수)
                const typeSel = row.querySelector('.vpType');
                const transportSel = row.querySelector('.vpTransport');
                Array.from(transportSel.options).forEach(opt => {
                    setOptionState(opt, typeSel.value === 'HDD' && opt.value === 'NVME' ? MSG_HDD_NO_NVME : null);
                });
                const hddNvme = typeSel.value === 'HDD' && transportSel.value === 'NVME';
                const key = priorityIdentity(row);
                const dup = seen.has(key);
                if (!dup) seen.set(key, i + 1);
                transportSel.classList.toggle('has-error', hddNvme || dup);
                transportSel.title = hddNvme ? MSG_HDD_NO_NVME : dup ? MSG_PRIORITY_DUPLICATE + ' (' + seen.get(key) + '번 행)' : '';
            });
            if (rcAddPriority) {
                const exhausted = seen.size >= PRIORITY_VALID_COMBOS && rows.length >= PRIORITY_VALID_COMBOS;
                rcAddPriority.disabled = exhausted;
                rcAddPriority.title = exhausted ? MSG_PRIORITY_EXHAUSTED : '';
            }
            if (rcPriorityHint) {
                const empty = rows.length === 0;
                const osFixed = diskGroupRows().some(row => row.querySelector('.dgRole').value === 'OS');
                rcPriorityHint.textContent = !empty ? '' : (osInstallCardActive() && !osFixed && diskGroupRows().length > 0)
                    ? HINT_PRIORITY_EMPTY_WITH_INSTALL : MSG_PRIORITY_EMPTY;
                rcPriorityHint.hidden = !empty;
            }
        }
        function addPriorityRow(data) {
            const row = cloneTemplateRow('tplPriorityRow');
            if (!row || !rcPriorityTbody) return;
            const d = data || {};
            if (d.diskType) row.querySelector('.vpType').value = d.diskType;
            if (d.transport) row.querySelector('.vpTransport').value = d.transport;
            if (d.capacityOrder) row.querySelector('.vpCapacityOrder').value = d.capacityOrder;
            ['.vpType', '.vpTransport', '.vpCapacityOrder']
                .forEach(sel => row.querySelector(sel).addEventListener('change', applyPriorityConstraints));
            bindRowDrag(row, rcPriorityTbody, applyPriorityConstraints);
            bindRowRemove(row);
            row.querySelector('[data-row-remove]').addEventListener('click', () => setTimeout(applyPriorityConstraints, 0));
            rcPriorityTbody.appendChild(row);
            applyPriorityConstraints();
        }
        function clearPriorityRows() {
            priorityRows().forEach(row => row.remove());
        }
        /** 기본 행으로 — 서버가 내린 defaultVolumePrioritiesJson(SSOT = VolumePriorityRuleRequest.defaults()) 그대로. */
        function resetPriorityRows() {
            clearPriorityRows();
            DEFAULT_VOLUME_PRIORITIES.forEach(row => addPriorityRow(row));
            applyPriorityConstraints();
        }
        function buildVolumePriorities() {
            return priorityRows().map(row => ({
                diskType: row.querySelector('.vpType').value,
                transport: row.querySelector('.vpTransport').value,
                capacityOrder: row.querySelector('.vpCapacityOrder').value
            }));
        }
        /** 중복 (종류, 전송) 행 → [{no, sameAsNo}] — precheck 용. */
        function duplicatePriorityRows() {
            const seen = new Map();
            const duplicates = [];
            priorityRows().forEach((row, i) => {
                const key = priorityIdentity(row);
                if (seen.has(key)) duplicates.push({no: i + 1, sameAsNo: seen.get(key)});
                else seen.set(key, i + 1);
            });
            return duplicates;
        }
        if (rcAddPriority) rcAddPriority.addEventListener('click', () => addPriorityRow());
        if (rcResetPriority) rcResetPriority.addEventListener('click', resetPriorityRows);

        const rcAddDiskGroup = document.getElementById('rcAddDiskGroup');
        if (rcAddDiskGroup) rcAddDiskGroup.addEventListener('click', () => addDiskGroupRow());
        if (rcRaidCard) rcRaidCard.addEventListener('change', applyDiskGroupConstraints);
        document.querySelectorAll('input[name="rcExistingPolicyRadio"]')
            .forEach(radio => radio.addEventListener('change', applyDestroyWarning));

        /* ---- root 비밀번호 (기존 유지 UX) ---- */

        function syncRootKeepState() {
            if (!oiRootKeep || !oiRootPassword) return;
            const keeping = !oiRootKeepWrap.hidden && oiRootKeep.checked;
            oiRootPassword.disabled = keeping;
            if (keeping) oiRootPassword.value = '';
            oiRootPassword.placeholder = keeping ? '기존 비밀번호 유지 중' : '비워두면 root 계정 잠금';
        }

        /** 수정 pre-fill — 기존 root 비밀번호가 있음을 표시하고 유지 체크를 켠다. */
        function markRootHasExisting() {
            if (!oiRootKeepWrap) return;
            oiRootKeepWrap.hidden = false;
            oiRootKeep.checked = true;
            syncRootKeepState();
        }

        if (oiRootKeep) oiRootKeep.addEventListener('change', syncRootKeepState);

        /* ---- Administrator 비밀번호 (E4-1-a-2 · root 비밀번호와 같은 기존 유지 관용구) ---- */

        function syncWinAdminKeepState() {
            if (!oiWinAdminKeep || !oiWinAdminPassword) return;
            const keeping = !oiWinAdminKeepWrap.hidden && oiWinAdminKeep.checked;
            oiWinAdminPassword.disabled = keeping;
            if (keeping) oiWinAdminPassword.value = '';
            oiWinAdminPassword.placeholder = keeping ? '기존 비밀번호 유지 중' : '예: S3rver!2025';
        }

        /** 수정 pre-fill — 저장본에 비밀번호가 있음을 표시하고 유지 체크를 켠다(값은 서버가 이미 제거해 보낸다). */
        function markWinAdminHasExisting() {
            if (!oiWinAdminKeepWrap) return;
            oiWinAdminKeepWrap.hidden = false;
            oiWinAdminKeep.checked = true;
            syncWinAdminKeepState();
        }

        if (oiWinAdminKeep) oiWinAdminKeep.addEventListener('change', syncWinAdminKeepState);

        function buildWinAdminPassword() {
            const keeping = oiWinAdminKeepWrap && !oiWinAdminKeepWrap.hidden && oiWinAdminKeep.checked;
            if (keeping) return {password: null, keepExistingPassword: true};
            const value = oiWinAdminPassword ? oiWinAdminPassword.value : '';
            return {password: value || null, keepExistingPassword: false};
        }

        /* ---- 일반 사용자 행 ---- */

        function addUserRow(data, hasExistingPassword) {
            const row = cloneTemplateRow('tplUserRow');
            if (!row || !oiUserTbody) return;
            const d = data || {};
            row.querySelector('.uUsername').value = d.username || '';
            row.querySelector('.uSudoer').checked = !!d.isSudoer;
            row.querySelector('.uEncrypted').checked = !!d.isPasswordEncrypted;

            const keepWrap = row.querySelector('.uKeepWrap');
            const keepChk = row.querySelector('.uKeep');
            const pwInput = row.querySelector('.uPassword');

            function syncKeep() {
                const keeping = !keepWrap.hidden && keepChk.checked;
                pwInput.disabled = keeping;
                if (keeping) pwInput.value = '';
                pwInput.placeholder = keeping ? '기존 비밀번호 유지 중' : '비밀번호';
            }

            if (hasExistingPassword) keepWrap.hidden = false;
            keepChk.addEventListener('change', syncKeep);
            syncKeep();
            bindRowRemove(row);
            oiUserTbody.appendChild(row);
        }

        const oiAddUser = document.getElementById('oiAddUser');
        if (oiAddUser) oiAddUser.addEventListener('click', () => addUserRow());

        /* ─────────────── OS 설치 ↔ OS 후처리 대상 OS 동기화 (사용자 확정) ─────────────── */

        // 두 단계가 함께 있으면 같은 OS 여야 한다 — 값이 있는 쪽(설치 우선)이 다른 쪽을 같은 값으로 고정한다.
        // backend 는 SettingSaveRequest @AssertTrue(osSelectionConsistent)가 direct POST 안전망.
        let syncingOsSelection = false; // 파생 핸들러 재호출로 인한 재귀 방지
        function syncOsSelectionLock() {
            if (syncingOsSelection) return;
            const osSel = document.getElementById('osOsSelect');
            if (!oiOsSelect || !osSel) return;
            const installCard = cardOf('OS_INSTALLATION');
            const settingCard = cardOf('OS_SETTING');
            const both = installCard && !installCard.hidden && settingCard && !settingCard.hidden;
            syncingOsSelection = true;
            try {
                if (!both) {
                    oiOsSelect.disabled = false;
                    osSel.disabled = false;
                    return;
                }
                if (oiOsSelect.value) {
                    // 설치 쪽이 선택됨 → 후처리 select 를 같은 OS 로 고정
                    if (osSel.value !== oiOsSelect.value) {
                        osSel.value = oiOsSelect.value;
                        onSettingOsChange();
                        commitDeprecatedSelection(osSel); // 프로그램적 고정은 change 미발화 — 뱃지·복원 기준만 동기화
                    }
                    osSel.disabled = true;
                    oiOsSelect.disabled = false;
                } else if (osSel.value) {
                    // 후처리 쪽만 선택됨 → 설치 select 를 같은 OS 로 고정
                    if (oiOsSelect.value !== osSel.value) {
                        oiOsSelect.value = osSel.value;
                        onInstallOsChange();
                        commitDeprecatedSelection(oiOsSelect);
                    }
                    oiOsSelect.disabled = true;
                    osSel.disabled = false;
                } else {
                    oiOsSelect.disabled = false;
                    osSel.disabled = false;
                }
            } finally {
                syncingOsSelection = false;
            }
        }

        /* ─────────────────────────── OS_SETTING ─────────────────────────── */

        const osOsSelect = document.getElementById('osOsSelect');
        const osGuide = document.getElementById('osGuide');
        const osDetailFields = document.getElementById('osDetailFields');
        const osServicesTbody = document.querySelector('#osServicesTable tbody');

        function onSettingOsChange() {
            const osId = osOsSelect ? osOsSelect.value : '';
            if (osGuide) osGuide.hidden = !!osId;
            if (osDetailFields) osDetailFields.hidden = !osId;
            syncOsSelectionLock();
        }

        if (osOsSelect) osOsSelect.addEventListener('change', onSettingOsChange);

        function addServiceRow(data) {
            const row = cloneTemplateRow('tplServiceRow');
            if (!row || !osServicesTbody) return;
            const d = data || {};
            row.querySelector('.svcName').value = d.name || '';
            row.querySelector('.svcAction').value = d.action === 'DISABLE' ? 'DISABLE' : 'ENABLE';
            bindRowRemove(row);
            osServicesTbody.appendChild(row);
        }

        const osAddService = document.getElementById('osAddService');
        if (osAddService) osAddService.addEventListener('click', () => addServiceRow());

        /* ─────────────────────────── Deprecated 자원 선택 안내 ─────────────────────────── */

        // 서버 계약 : disabled(effective) 자원은 옵션에서 아예 배제되므로 프론트는 deprecated 만 다룬다.
        // deprecated 는 저장을 막지 않는다 — modal 은 확인용이고, '그래도 적용' 외의 모든 종료는 이전 값으로 복원한다.
        // 옵션 메타(data-deprecated / data-deprecated-at)는 Thymeleaf 가 렌더한다.

        /** select 의 현재 선택이 deprecated 자원이면 소속 .n-form-group 라벨 옆에 '지원 중단' 뱃지 upsert, 아니면 제거. */
        function refreshDeprecatedBadge(selectEl) {
            if (!selectEl) return;
            const group = selectEl.closest('.n-form-group');
            const label = group ? group.querySelector('label.n-label') : null;
            if (!label) return;
            const opt = selectedOption(selectEl);
            const deprecated = !!opt && opt.dataset.deprecated === 'true';
            let badge = group.querySelector('[data-deprecated-badge]');
            if (deprecated && !badge) {
                badge = document.createElement('span');
                badge.className = 'n-badge n-badge-yellow'; // 자원 도메인의 Deprecated 뱃지 색과 통일
                badge.setAttribute('data-deprecated-badge', '');
                badge.textContent = '지원 중단';
                label.insertAdjacentElement('afterend', badge);
            } else if (!deprecated && badge) {
                badge.remove();
            }
        }

        /**
         * 뱃지 + '취소' 복원 기준값(data-deprecated-prev)을 현재 선택으로 동기화한다.
         * change 이벤트 없이 값이 바뀌는 프로그램적 경로(pre-fill / 보드 변경에 따른 펌웨어 LATEST 리셋)
         * 뒤에 호출해 기준값 드리프트를 막는다 — 이 경로에서는 modal 을 띄우지 않는다(뱃지만).
         */
        function commitDeprecatedSelection(selectEl) {
            if (!selectEl) return;
            selectEl.dataset.deprecatedPrev = selectEl.value;
            refreshDeprecatedBadge(selectEl);
        }

        /** modal 본문에 주입할 자원 정보 표 — 기존 n-detail-table 시각 계약 재사용 (인라인 style 0). */
        function buildDeprecatedInfoTable(resourceTypeLabel, opt) {
            const table = document.createElement('table');
            table.className = 'n-detail-table';
            const tbody = document.createElement('tbody');
            [
                ['유형', resourceTypeLabel],
                ['ID', opt.value],
                ['이름', (opt.textContent || '').trim()],
                ['Deprecated 일시', opt.dataset.deprecatedAt || '—'],
                ['설명', opt.dataset.description || '—']
            ].forEach(pair => {
                const tr = document.createElement('tr');
                const th = document.createElement('th');
                th.textContent = pair[0];
                const td = document.createElement('td');
                td.textContent = pair[1];
                tr.append(th, td);
                tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            return table;
        }

        /**
         * deprecated 옵션 선택 시 확인 modal 을 띄우는 감시를 등록한다.
         *
         * 반드시 신규 change listener 로만 훅한다 — pre-fill 은 .value 대입 + 핸들러 직접 호출로 동작해
         * change 이벤트가 발생하지 않으므로, 기존 핸들러(onBoardModelChange 등) 내부에 modal 을 넣으면
         * 직접 호출 경로(pre-fill)에서도 떠버린다. 별도 listener 는 사용자 조작에서만 발화한다.
         *
         * @param selectEl          감시할 select
         * @param resourceTypeLabel modal 정보 표의 '유형' 표기
         * @param onRevert          복원 직후 재실행할 파생 핸들러 (보드/OS select 전용, 없으면 null)
         */
        function watchDeprecatedSelect(selectEl, resourceTypeLabel, onRevert) {
            if (!selectEl) return;
            commitDeprecatedSelection(selectEl); // 초기 선택을 복원 기준값으로
            selectEl.addEventListener('change', function () {
                const opt = selectedOption(selectEl);
                if (!opt || opt.dataset.deprecated !== 'true' || !window.ConfirmModal) {
                    // 정상 자원 (또는 modal 자산 미로드 방어 — native confirm 금지라 fallback 없이 그대로 적용)
                    commitDeprecatedSelection(selectEl);
                    return;
                }
                const prev = selectEl.dataset.deprecatedPrev || '';
                let confirmed = false;
                window.ConfirmModal.open('deprecatedUse', {
                    title: '지원 중단(Deprecated) 자원',
                    message: '지원이 중단된 자원입니다. 계속 사용하시겠습니까?',
                    confirmLabel: '그래도 적용',
                    confirmClass: 'n-btn-outline-warning',
                    afterOpen: function (ctx) {
                        const messageEl = ctx.modal.querySelector('.cm-message');
                        const info = buildDeprecatedInfoTable(resourceTypeLabel, opt);
                        if (messageEl) messageEl.insertAdjacentElement('afterend', info);
                        // cleanup — base 가 close 모든 경로(확인/취소/backdrop/Escape)에서 호출한다.
                        // 확인이 아닌 종료는 전부 취소로 간주해 이전 값 복원 + 파생 핸들러 재실행.
                        return function () {
                            info.remove();
                            if (confirmed) return;
                            selectEl.value = prev;
                            if (onRevert) onRevert();
                            commitDeprecatedSelection(selectEl);
                        };
                    },
                    beforeConfirm: function () {
                        confirmed = true; // close(→cleanup) 이 onConfirm 보다 먼저 실행되므로 여기서 마킹
                    },
                    onConfirm: function () {
                        commitDeprecatedSelection(selectEl);
                    }
                });
            });
        }

        // 적용 대상 5 select — 환경/패키지그룹 선택지는 lifecycle 비대상이라 제외.
        watchDeprecatedSelect(buBoardModel, '메인보드', function () {
            onBoardModelChange(); // 복원된 보드 기준으로 펌웨어 select 재정렬
            commitDeprecatedSelection(buBios);
            commitDeprecatedSelection(buBmc);
            refreshBsTemplateRules(); // 복원은 change 미발화 — 템플릿 규칙도 복원 보드 기준으로 재평가
        });
        watchDeprecatedSelect(buBios, 'BIOS 펌웨어', null);
        watchDeprecatedSelect(buBmc, 'BMC 펌웨어', null);
        watchDeprecatedSelect(oiOsSelect, 'OS', onInstallOsChange);
        watchDeprecatedSelect(rcRaidCard, 'RAID 카드', applyDiskGroupConstraints); // U4-1-1
        watchDeprecatedSelect(oiIsoSelect, 'ISO', function () {
            filterEnvironmentOptions();
            applyPackageGroupFilter();
        });
        if (oiIsoSelect) oiIsoSelect.addEventListener('change', function () {
            // ISO 가 바뀌면 환경/패키지 그룹의 가용 목록이 바뀐다(comps.xml 스코프).
            filterEnvironmentOptions();
            applyPackageGroupFilter();
        });
        watchDeprecatedSelect(osOsSelect, 'OS', onSettingOsChange);

        // 보드 변경 시 onBoardModelChange 가 BIOS/BMC 를 change 이벤트 없이 LATEST 로 리셋하므로
        // 두 select 의 뱃지·복원 기준값도 함께 정렬한다 (등록 순서상 onBoardModelChange 뒤에 실행됨).
        if (buBoardModel) {
            buBoardModel.addEventListener('change', function () {
                commitDeprecatedSelection(buBios);
                commitDeprecatedSelection(buBmc);
            });
        }

        /* ─────────────────────────── BASIC_SETTING : 템플릿 선택 규칙 ─────────────────────────── */

        // 서버 안전망(SettingSaveRequest @AssertTrue — SPECIFIED ⇒ 1개 등)과 동일 SSOT 의 UI 1차 차단 :
        //   보드 SPECIFIED             ⇒ 그 보드 템플릿만 활성 + 1개(라디오 의미론 — change 핸들러가 교체)
        //   보드 AUTO / 펌웨어 단계 없음 ⇒ 전체 활성하되 보드당 1개(체크된 보드의 나머지 항목 비활성)
        const bsTemplatePanel = document.getElementById('bsTemplatePanel');
        const bsPrefillWarning = document.getElementById('bsPrefillWarning');

        function bsTemplateChecks() {
            return bsTemplatePanel
                ? Array.from(bsTemplatePanel.querySelectorAll('input[type="checkbox"]'))
                : [];
        }

        /** BASIC_UPDATE 카드 활성 + 보드 SPECIFIED 일 때만 보드 id, 그 외(AUTO/카드 없음)는 null. */
        const bsBoardModel = document.getElementById('bsBoardModel');

        /** 템플릿 규칙의 판정 기준 = BASIC_SETTING 자체 selector (2026-07-07 개정 — 공존 시 미러라 동치). */
        function bsSpecifiedBoardId() {
            if (!bsBoardModel || bsBoardModel.value === 'AUTO') return null;
            return bsBoardModel.value;
        }

        /**
         * 보드 selector 동기화(사용자 확정) — 펌웨어 업데이트 단계가 함께 있으면 BASIC_SETTING 의
         * 보드 select 를 그 값으로 미러링해 고정한다(OS 설치↔후처리 syncOsSelectionLock 과 동일 패턴,
         * 서버 @AssertTrue(boardSelectionConsistent) 가 direct POST 안전망).
         */
        function syncBsBoardLock() {
            if (!bsBoardModel) return;
            const buCard = cardOf('BASIC_UPDATE');
            const bsCard = cardOf('BASIC_SETTING');
            const both = buCard && !buCard.hidden && bsCard && !bsCard.hidden;
            if (both) {
                const mirrored = isBoardAuto() ? 'AUTO' : buBoardModel.value;
                if (bsBoardModel.value !== mirrored) {
                    bsBoardModel.value = mirrored;
                    commitDeprecatedSelection(bsBoardModel); // 프로그램적 설정 — 뱃지·복원 기준 동기화
                }
                bsBoardModel.disabled = true;
            } else {
                bsBoardModel.disabled = false;
            }
            refreshBsTemplateRules();
        }

        /**
         * 선택 규칙 재평가 — 활성/비활성 정렬 + 규칙 위반이 된 기존 체크 자동 해제.
         * 훅 : #buBoardModel change / 단계 추가·제거(addStep·removeStep) / 패널 change / pre-fill 말미.
         * pre-fill 경로는 저장본이 이미 규칙(서버 검증 통과분)을 준수하므로 위반 해제가 발동하지 않는다.
         */
        function refreshBsTemplateRules() {
            const checks = bsTemplateChecks();
            if (checks.length === 0) return;
            const specifiedBoardId = bsSpecifiedBoardId();
            if (specifiedBoardId !== null) {
                // SPECIFIED — 대상 보드 외 항목 비활성 + 위반 체크 해제 (1개 제한은 change 라디오 의미론이 담당)
                checks.forEach(chk => {
                    const match = chk.dataset.boardModelId === specifiedBoardId;
                    if (!match && chk.checked) chk.checked = false;
                    chk.disabled = !match;
                });
            } else {
                // AUTO — 전체 활성하되 보드당 1개 : 체크된 보드의 나머지(미체크) 항목만 비활성
                const checkedBoard = {};
                checks.forEach(chk => {
                    if (!chk.checked) return;
                    if (checkedBoard[chk.dataset.boardModelId]) chk.checked = false; // 보드당 2개째 잔존 방어
                    else checkedBoard[chk.dataset.boardModelId] = true;
                });
                checks.forEach(chk => {
                    chk.disabled = !chk.checked && !!checkedBoard[chk.dataset.boardModelId];
                });
            }
            // 비활성 항목 시각 처리 — 숨김(.unavailable)이 아닌 흐림(기존 .n-muted 재사용): 사유 인지 목적
            checks.forEach(chk => {
                const label = chk.closest('label.n-checkbox');
                if (label) label.classList.toggle('n-muted', chk.disabled);
            });
        }

        if (bsTemplatePanel) {
            // 라디오 의미론 — SPECIFIED 상태에서 다른 항목 체크 시 기존 체크를 해제하고 교체
            bsTemplatePanel.addEventListener('change', function (e) {
                const chk = e.target;
                if (!chk || chk.type !== 'checkbox') return;
                if (chk.checked && bsSpecifiedBoardId() !== null) {
                    bsTemplateChecks().forEach(other => {
                        if (other !== chk) other.checked = false;
                    });
                }
                refreshBsTemplateRules();
            });
            // 보드 selector 변경 재평가 — 기존 listener(펌웨어 정렬/deprecated 뱃지) 뒤에 등록해 최종 값 기준으로 실행
            if (buBoardModel) buBoardModel.addEventListener('change', syncBsBoardLock);
            // 단독(BASIC_SETTING 만)일 때의 자체 보드 선택 — 템플릿 필터 재평가. 공존 시엔 disabled 라 미발화.
            if (bsBoardModel) bsBoardModel.addEventListener('change', refreshBsTemplateRules);
            if (bsBoardModel) watchDeprecatedSelect(bsBoardModel, '메인보드', refreshBsTemplateRules);
            refreshBsTemplateRules(); // 초기 정렬 (생성 폼 기본 상태)
        }

        /* ─────────────────────────── 페이로드 조립 ─────────────────────────── */

        function buildRootPassword() {
            const keeping = oiRootKeepWrap && !oiRootKeepWrap.hidden && oiRootKeep.checked;
            const encrypted = document.getElementById('oiRootEncrypted').checked;
            if (keeping) {
                return {password: null, isPasswordEncrypted: encrypted, keepExistingPassword: true};
            }
            const value = oiRootPassword.value;
            if (!value) return null; // root 잠금 설치
            return {password: value, isPasswordEncrypted: encrypted, keepExistingPassword: false};
        }

        function buildPartitions() {
            return Array.from(oiPartitionTbody.querySelectorAll('tr')).map(row => ({
                mountPoint: row.querySelector('.pMountPoint').value.trim(),
                fileSystem: row.querySelector('.pFileSystem').value,
                size: intOrNull(row.querySelector('.pSize').value) || 0,
                sizeUnit: row.querySelector('.pSizeUnit').value,
                isGrow: row.querySelector('.pGrow').checked
            }));
        }

        function buildUsers() {
            return Array.from(oiUserTbody.querySelectorAll('tr')).map(row => {
                const keepWrap = row.querySelector('.uKeepWrap');
                const keeping = keepWrap && !keepWrap.hidden && row.querySelector('.uKeep').checked;
                return {
                    username: row.querySelector('.uUsername').value.trim(),
                    password: keeping ? null : (row.querySelector('.uPassword').value || null),
                    isSudoer: row.querySelector('.uSudoer').checked,
                    isPasswordEncrypted: row.querySelector('.uEncrypted').checked,
                    keepExistingPassword: !!keeping
                };
            });
        }

        // 단계 타입별 페이로드 빌더 — switch 사다리 대신 type 키 맵으로 확장한다.
        // 계열 판별자 리터럴(RHEL_BASED/DEBIAN_BASED)은 OSFamily 상수명과 일치해야 한다
        // (선택지 option 의 data-os-family 도 서버가 같은 상수명으로 렌더).
        /** 펌웨어 select 값 → FirmwareSelectionRequest ({mode, firmwareId}). LATEST 는 id 없음. */
        function buildFirmwareSelector(select) {
            const value = select.value;
            return value === FIRMWARE_LATEST
                ? {mode: 'LATEST', firmwareId: null}
                : {mode: 'SPECIFIED', firmwareId: intOrNull(value)};
        }

        const stepBuilders = {
            BASIC_UPDATE: function () {
                const auto = isBoardAuto();
                return {
                    type: 'BASIC_UPDATE',
                    boardModel: auto
                        ? {mode: 'AUTO', boardModelId: null}
                        : {mode: 'SPECIFIED', boardModelId: intOrNull(buBoardModel.value)},
                    bios: buildFirmwareSelector(buBios),
                    bmc: buildFirmwareSelector(buBmc)
                };
            },
            BASIC_SETTING: function () {
                const bsAuto = !bsBoardModel || bsBoardModel.value === 'AUTO';
                return {
                    type: 'BASIC_SETTING',
                    boardModel: bsAuto
                        ? {mode: 'AUTO'}
                        : {mode: 'SPECIFIED', boardModelId: intOrNull(bsBoardModel.value)},
                    biosSettingTemplateIds: bsTemplateChecks()
                        .filter(chk => chk.checked)
                        .map(chk => intOrNull(chk.value))
                        .filter(id => id != null)
                };
            },
            RAID_CONFIGURATION: function () {
                return {
                    type: 'RAID_CONFIGURATION',
                    raidCardId: rcRaidCard ? intOrNull(rcRaidCard.value) : null,   // 소프트참조(null = 전제 없음)
                    diskGroups: buildDiskGroups(),
                    volumePriorities: buildVolumePriorities(),                       // U4-1-2 — 빈 배열도 명시적 값(열거 순서)
                    existingConfigPolicy: selectedExistingPolicy()                   // E3.5-4 D-7 — 미선택 null(구 저장본 호환)
                };
            },
            OS_INSTALLATION: function () {
                // E4-1-a-2 — 판별자 = 선택 OS 의 계열(data-os-family). Windows 만 설치 이미지 · Administrator 비밀번호를 싣는다.
                const family = installOsFamily();
                const payload = {
                    type: 'OS_INSTALLATION',
                    osFamily: family || null,
                    osMetadataId: intOrNull(oiOsSelect.value),
                    isoId: oiIsoSelect ? intOrNull(oiIsoSelect.value) : null
                };
                if (family === 'WINDOWS') {
                    payload.imageName = oiWindowsImage && oiWindowsImage.value ? oiWindowsImage.value : null;
                    payload.administratorPassword = buildWinAdminPassword();
                }
                return payload;
            },
            OS_SETTING: function () {
                const opt = selectedOption(osOsSelect);
                return {
                    type: 'OS_SETTING',
                    osFamily: opt && osOsSelect.value ? (opt.dataset.osFamily || null) : null,
                    osMetadataId: intOrNull(osOsSelect.value),
                    selinuxMode: document.getElementById('osSelinuxMode').value,
                    services: Array.from(osServicesTbody.querySelectorAll('tr')).map(row => ({
                        name: row.querySelector('.svcName').value.trim(),
                        action: row.querySelector('.svcAction').value
                    })).filter(svc => svc.name.length > 0),
                    additionalPackages: splitCsv(document.getElementById('osAdditionalPackages').value)
                };
            }
        };

        function buildPayload() {
            const processList = [];
            const stepTypeByIndex = [];
            activeCards().forEach(card => {
                const type = card.dataset.processCard;
                const builder = stepBuilders[type];
                if (!builder) return;
                processList.push(builder());
                stepTypeByIndex.push(type);
            });
            return {
                payload: {name: document.getElementById('settingName').value, processList: processList},
                stepTypeByIndex: stepTypeByIndex
            };
        }

        /* ─────────────────────────── 에러 렌더링 ─────────────────────────── */

        /** form-validation.css 계약(.has-error / .field-error-message)으로 인라인 에러를 칠한다. */
        function paintFieldError(target, message) {
            target.classList.add('has-error');
            const group = target.closest('.n-form-group');
            const anchor = group || target.parentElement || target;
            anchor.querySelectorAll(':scope > .field-error-message').forEach(el => el.remove());
            const note = document.createElement('div');
            note.className = 'field-error-message';
            note.textContent = message || '';
            if (group) group.appendChild(note);
            else target.insertAdjacentElement('afterend', note);
            // 에러가 접힌 카드 안에 있으면 펼쳐서 보이게 한다.
            const card = target.closest('details[data-process-card]');
            if (card) card.open = true;
        }

        /**
         * 서버 fieldErrors 를 폼에 매핑한다.
         * "processList[i].local.path" 는 stepTypeByIndex 로 해당 단계 카드를 찾은 뒤
         * 카드 내부의 data-error-field 를 로컬 경로 → 상위 경로 순으로 축약하며 탐색한다
         * (예: partitions[0].mountPoint → partitions[0] → partitions,
         *      boardModel.modeConsistent → boardModel, bios.modeConsistent → bios.
         *      firmwareSelectionCoherent 는 BIOS/BMC 행 컨테이너에 exact-match).
         * 전역 FormError 는 문서 전체 exact-match 라 카드 스코프 중첩 경로에 맞지 않아
         * 본 폼 전용 매퍼를 두되, 시각 계약(.has-error)과 clear 는 FormError 를 재사용한다.
         */
        function resolveErrorTarget(field, stepTypeByIndex) {
            const m = /^processList\[(\d+)\]\.?(.*)$/.exec(field || '');
            if (!m) {
                return form.querySelector('[data-error-field="' + cssEscape(field || '') + '"]');
            }
            const type = stepTypeByIndex[parseInt(m[1], 10)];
            const card = type ? cardOf(type) : null;
            if (!card) return null;
            let local = m[2];
            while (local) {
                const target = card.querySelector('[data-error-field="' + cssEscape(local) + '"]');
                if (target) return target;
                const shorter = local.replace(/(\.[^.\[\]]+|\[\d+\])$/, '');
                if (shorter === local) break;
                local = shorter;
            }
            return card.querySelector('.n-accordion-body') || card;
        }

        function renderServerErrors(body, stepTypeByIndex) {
            if (window.FormError) window.FormError.clear(form);
            const data = body || {};
            const overflow = [];
            let mapped = 0;
            let firstTarget = null;
            (data.fieldErrors || []).forEach(fe => {
                if (!fe) return;
                const target = fe.field ? resolveErrorTarget(fe.field, stepTypeByIndex) : null;
                if (!target) {
                    overflow.push((fe.field ? fe.field + ': ' : '') + (fe.message || ''));
                    return;
                }
                paintFieldError(target, fe.message);
                if (!firstTarget) firstTarget = target;
                mapped++;
            });
            // 전부 인라인 매핑되면 요약 message 는 중복이므로 배너 생략 (FormError 와 동일 규칙)
            const bannerLines = (mapped > 0 && overflow.length === 0) ? [] : [data.message].concat(overflow);
            showBanner(bannerLines);
            if (firstTarget) firstTarget.scrollIntoView({behavior: 'smooth', block: 'center'});
            else if (banner && !banner.hidden) banner.scrollIntoView({behavior: 'smooth', block: 'center'});
        }

        /* ─────────────────────────── 제출 전 최소 정합 검사 ─────────────────────────── */

        /**
         * 서버가 의미 있는 필드 에러로 응답할 수 없는 구조 결함만 사전 차단한다 :
         *  - 단계 0개 (배너)
         *  - OS 단계의 판별자 누락 (osFamily 없이 전송하면 Jackson subtype 해석 실패 → 모호한 400)
         *  - grow 아닌 파티션의 크기 미입력
         *  - BASIC_SETTING 의 템플릿 미선택 (서버 @NotEmpty 400 의 1차 차단 — 즉시 피드백)
         * 그 외 검증은 제출 후 서버 400 fieldErrors 를 인라인 렌더한다.
         */
        function precheck(payload, stepTypeByIndex) {
            if (payload.processList.length === 0) {
                showBanner('최소 하나 이상의 프로비저닝 단계를 추가해야 합니다.');
                return false;
            }
            let ok = true;
            payload.processList.forEach((proc, i) => {
                // R11(CP5 D6) — osFamily 는 더 이상 필수가 아니다: 판별자 부재는 서버의 식별 전용
                // 등록 테이블(PlannedOSInstallationRequest)이 해석한다. OS 선택만 본다.
                if ((proc.type === 'OS_INSTALLATION' || proc.type === 'OS_SETTING')
                    && proc.osMetadataId == null) {
                    const card = cardOf(stepTypeByIndex[i]);
                    const select = card ? card.querySelector('[data-error-field="osMetadataId"]') : null;
                    if (select) paintFieldError(select, 'OS 를 선택해야 합니다.');
                    ok = false;
                }
                if (proc.type === 'OS_INSTALLATION' && proc.osMetadataId != null && proc.isoId == null) {
                    const card = cardOf(stepTypeByIndex[i]);
                    const select = card ? card.querySelector('[data-error-field="isoId"]') : null;
                    if (select) paintFieldError(select, '설치 ISO 를 선택해야 합니다.');
                    ok = false;
                }
                // E4-1-a-2 — Windows 필수 둘(설치 이미지 · Administrator 비밀번호)의 1차 차단. 서버 Layer A 와 같은 문장.
                if (proc.type === 'OS_INSTALLATION' && proc.osFamily === 'WINDOWS') {
                    const card = cardOf(stepTypeByIndex[i]);
                    if (proc.imageName == null) {
                        const select = card ? card.querySelector('[data-error-field="imageName"]') : null;
                        if (select) paintFieldError(select, '설치 이미지를 선택해야 합니다.');
                        ok = false;
                    }
                    const pw = proc.administratorPassword;
                    if (!pw || (!pw.keepExistingPassword && !pw.password)) {
                        const group = card ? card.querySelector('[data-error-field="administratorPassword"]') : null;
                        if (group) paintFieldError(group, 'Administrator 비밀번호를 입력해야 합니다.');
                        ok = false;
                    }
                }
                if (proc.type === 'BASIC_SETTING'
                    && (!proc.biosSettingTemplateIds || proc.biosSettingTemplateIds.length === 0)) {
                    const card = cardOf('BASIC_SETTING');
                    const panel = card ? card.querySelector('[data-error-field="biosSettingTemplateIds"]') : null;
                    if (panel) paintFieldError(panel, 'BIOS 세팅 템플릿을 1개 이상 선택해야 합니다.');
                    ok = false;
                }
            });
            if (stepTypeByIndex.includes('RAID_CONFIGURATION') && rcDiskGroupTbody) {
                // 디스크 묶음(U4-1-1) — 진리표가 막지 못하는 두 상태만 사전 차단: pre-fill 로 카드가 소실된 채 RAID 묶음이 남은 경우 ·
                // 개수 미달 / 미입력. 문구는 서버 @AssertTrue · DiskGroupRules 와 같다.
                const rows = diskGroupRows();
                if (!selectedRaidCard() && rows.some(rowBuildsRaid)) {
                    const container = form.querySelector('[data-error-field="raidCardPresentWhenRequired"]');
                    if (container) paintFieldError(container, MSG_CARD_REQUIRED);
                    ok = false;
                }
                let countError = false;
                rows.forEach(row => {
                    const countInput = row.querySelector('.dgCount');
                    const value = intOrNull(countInput.value);
                    if (value == null || value < (parseInt(countInput.min, 10) || 1)) {
                        countInput.classList.add('has-error');
                        countError = true;
                    }
                    const capMode = row.querySelector('.dgCapacityMode').value;
                    const size = intOrNull(row.querySelector('.dgCapacitySize').value);
                    if (capMode === 'SPECIFIED' && (size == null || size < 1)) {
                        row.querySelector('.dgCapacitySize').classList.add('has-error');
                        countError = true;
                    }
                });
                const hddNvme = rows.filter(row => row.querySelector('.dgType').value === 'HDD' && row.querySelector('.dgTransport').value === 'NVME');
                if (hddNvme.length) {
                    const container = form.querySelector('[data-error-field="diskGroups"]');
                    if (container) paintFieldError(container, (rows.indexOf(hddNvme[0]) + 1) + '번 묶음: ' + MSG_HDD_NO_NVME);
                    ok = false;
                }
                if (countError) {
                    const container = form.querySelector('[data-error-field="diskGroups"]');
                    if (container) paintFieldError(container, '디스크 묶음의 개수(레벨별 최소치 이상)와 직접 지정한 용량을 채워야 합니다.');
                    ok = false;
                }
                const duplicates = markDuplicateDiskGroups();
                if (duplicates.length) {
                    const container = form.querySelector('[data-error-field="diskGroups"]');
                    const first = duplicates[0];
                    if (container) paintFieldError(container, first.no + '번 묶음이 ' + first.sameAsNo + '번 묶음과 같은 규칙입니다 — 축 하나를 바꾸거나 줄을 지우세요.');
                    ok = false;
                }
                // 규칙 8 — 도달 불가 행이 있으면 제출하지 않는다(UI 1차 차단 · CP5 F-5). 문구는 서버 unreachableRule 의 400 문장과 같다.
                const unreachable = unreachableDiskGroupFindings();
                if (unreachable.message) {
                    const container = form.querySelector('[data-error-field="diskGroups"]');
                    if (container) paintFieldError(container, unreachable.message);
                    ok = false;
                }
                // 역할 · 우선순위(U4-1-2) — 진리표가 잠그지만 pre-fill · 경합으로 남을 수 있는 상태의 안전망. 문구는 서버와 같다.
                const osRows = rows.filter(row => row.querySelector('.dgRole').value === 'OS');
                if (osRows.length > 1) {
                    const container = form.querySelector('[data-error-field="diskGroups"]');
                    if (container) paintFieldError(container, (rows.indexOf(osRows[1]) + 1) + '번 묶음: ' + (rows.indexOf(osRows[0]) + 1) + MSG_OS_FIXED_ELSEWHERE);
                    ok = false;
                }
                const priorityDuplicates = duplicatePriorityRows();
                if (priorityDuplicates.length) {
                    const container = form.querySelector('[data-error-field="volumePriorityDistinct"]');
                    if (container) paintFieldError(container, priorityDuplicates[0].no + '번 행이 ' + priorityDuplicates[0].sameAsNo + '번 행과 같은 종류 · 전송 조합입니다 — 하나를 바꾸거나 지우세요.');
                    ok = false;
                }
                const priorityHddNvme = priorityRows().filter(row => row.querySelector('.vpType').value === 'HDD' && row.querySelector('.vpTransport').value === 'NVME');
                if (priorityHddNvme.length) {
                    const container = form.querySelector('[data-error-field="volumePriorities"]');
                    if (container) paintFieldError(container, (priorityRows().indexOf(priorityHddNvme[0]) + 1) + '번 행: ' + MSG_HDD_NO_NVME);
                    ok = false;
                }
                // OS 설치 단계가 있으면 OS 볼륨이 정의서만으로 정해져야 한다(SettingSaveRequest.isOsVolumeDeterminable 의 1차 차단).
                if (stepTypeByIndex.includes('OS_INSTALLATION') && rows.length > 0) {
                    const osFixed = osRows.length > 0;
                    const byPriority = rows.some(row => row.querySelector('.dgRole').value === 'BY_PRIORITY');
                    if (!osFixed && !(byPriority && priorityRows().length > 0)) {
                        const container = form.querySelector('[data-error-field="osVolumeDeterminable"]');
                        if (container) paintFieldError(container, MSG_PRIORITY_EMPTY_WITH_INSTALL);
                        ok = false;
                    }
                }
            }
            if (stepTypeByIndex.includes('OS_INSTALLATION') && oiPartitionTbody) {
                let sizeError = false;
                oiPartitionTbody.querySelectorAll('tr').forEach(row => {
                    const grow = row.querySelector('.pGrow').checked;
                    const size = intOrNull(row.querySelector('.pSize').value) || 0;
                    if (!grow && size <= 0) {
                        row.querySelector('.pSize').classList.add('has-error');
                        sizeError = true;
                    }
                });
                if (sizeError) {
                    const container = form.querySelector('[data-error-field="partitions"]');
                    if (container) paintFieldError(container, '파티션의 크기를 지정하거나 grow 를 체크해야 합니다.');
                    ok = false;
                }
                // U4-1-3 D7 — 고정 파티션 합이 OS 영역 볼륨 하한을 넘으면 막는다(하한을 알 때만 · 서버 isPartitionsWithinOsVolume 과 같은 판정).
                if (stepTypeByIndex.includes('RAID_CONFIGURATION') && partitionsOverOsVolume()) {
                    const container = form.querySelector('[data-error-field="partitionsWithinOsVolume"]');
                    if (container) paintFieldError(container, MSG_PARTITIONS_OVER);
                    ok = false;
                }
            }
            return ok;
        }

        /* ─────────────────────────── 제출 ─────────────────────────── */

        async function submitSetting() {
            if (window.FormError) window.FormError.clear(form);
            showBanner([]);

            const built = buildPayload();
            if (!precheck(built.payload, built.stepTypeByIndex)) return;

            const mode = form.dataset.mode;
            const endpoint = form.dataset.endpoint;
            const url = mode === 'edit' ? endpoint + '/' + form.dataset.settingId : endpoint;
            const method = mode === 'edit' ? 'PUT' : 'POST';

            let resp;
            try {
                resp = await fetch(url, {
                    method: method,
                    headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                    body: JSON.stringify(built.payload)
                });
            } catch (e) {
                showBanner('서버와 통신할 수 없습니다: ' + e.message);
                return;
            }

            if (resp.ok) {
                // 201(생성) 은 Location 헤더가 상세 URL — 그리로 이동. 200(수정) 은 응답 id 로 상세 이동.
                const location = resp.headers.get('Location');
                if (location) {
                    window.location.href = location;
                    return;
                }
                const body = await resp.json().catch(() => null);
                window.location.href = body && body.id != null ? endpoint + '/' + body.id : endpoint;
                return;
            }

            const errBody = await resp.json().catch(() => ({message: '서버 응답 오류 (HTTP ' + resp.status + ')'}));
            renderServerErrors(errBody, built.stepTypeByIndex);
        }

        form.addEventListener('submit', e => {
            e.preventDefault();
            submitSetting();
        });

        /* ─────────────────────────── 수정 폼 pre-fill ─────────────────────────── */

        // 단계 타입별 pre-fill — 비밀번호는 서버가 제거(keepExistingPassword=true 대체)한 상태로 온다.
        /** FirmwareSelectionRequest({mode, firmwareId}) → 펌웨어 select 값 복원. */
        function prefillFirmwareSelector(select, selector) {
            if (!select || select.disabled) return; // 보드 AUTO 잠금 상태면 LATEST 고정이 이미 정답
            select.value = (selector && selector.mode === 'SPECIFIED' && selector.firmwareId != null)
                ? String(selector.firmwareId)
                : FIRMWARE_LATEST;
            const current = selectedOption(select);
            if (!current || current.disabled) select.value = FIRMWARE_LATEST; // 선택지에서 사라진 펌웨어 방어
        }

        const stepPrefillers = {
            BASIC_UPDATE: function (proc) {
                if (!buBoardModel) return;
                const board = proc.boardModel || {};
                buBoardModel.value = (board.mode === 'SPECIFIED' && board.boardModelId != null)
                    ? String(board.boardModelId)
                    : BOARD_AUTO;
                if (!selectedOption(buBoardModel)) buBoardModel.value = BOARD_AUTO; // 선택지에서 사라진 보드 방어
                onBoardModelChange();
                prefillFirmwareSelector(buBios, proc.bios);
                prefillFirmwareSelector(buBmc, proc.bmc);
                // 기존 사용분의 deprecated 표시는 뱃지만 (modal 없음 — change 이벤트를 dispatch 하지 않는 이유)
                commitDeprecatedSelection(buBoardModel);
                commitDeprecatedSelection(buBios);
                commitDeprecatedSelection(buBmc);
            },
            BASIC_SETTING: function (proc) {
                if (bsBoardModel) {
                    // 자체 보드 selector 복원(2026-07-07) — 구 형식(boardModel 없음)은 AUTO 로.
                    bsBoardModel.value = proc.boardModel && proc.boardModel.boardModelId != null
                        ? String(proc.boardModel.boardModelId) : 'AUTO';
                    commitDeprecatedSelection(bsBoardModel);
                }
                const ids = Array.isArray(proc.biosSettingTemplateIds) ? proc.biosSettingTemplateIds : [];
                let missing = false;
                ids.forEach(id => {
                    const chk = bsTemplatePanel
                        ? bsTemplatePanel.querySelector('input[type="checkbox"][value="' + cssEscape(String(id)) + '"]')
                        : null;
                    if (chk) chk.checked = true;
                    else missing = true; // 선택지에서 사라진 템플릿(삭제 레이스) — 무시하고 경고만
                });
                if (missing && bsPrefillWarning) bsPrefillWarning.hidden = false;
                // 저장본은 이미 규칙 준수라 위반 해제는 발동하지 않는다 — 활성/비활성 정렬 목적
                refreshBsTemplateRules();
            },
            RAID_CONFIGURATION: function (proc) {
                // U4-1-1 v2 — 저장본의 카드가 선택지에 없으면(삭제 · 비활성) 경고 줄을 보이고 값은 비운다 —
                // 조용히 다른 카드로 바꾸지 않는다. 그 상태로 저장하면 서버 @AssertTrue 가 잡는다.
                if (rcRaidCard) {
                    if (proc.raidCardId != null) {
                        rcRaidCard.value = String(proc.raidCardId);
                        const lost = rcRaidCard.value !== String(proc.raidCardId);
                        if (rcRaidCardPrefillWarning) rcRaidCardPrefillWarning.hidden = !lost;
                    }
                    commitDeprecatedSelection(rcRaidCard);
                }
                (proc.diskGroups || []).forEach(g => addDiskGroupRow(g));
                // 우선순위(U4-1-2) — 저장본이 있으면 그대로(빈 배열도 명시적 값), 없으면(구 저장본) addStep 이 채운 기본 행을 두고 알린다.
                const legacy = !Array.isArray(proc.volumePriorities) || (proc.diskGroups || []).some(g => !g.role);
                if (Array.isArray(proc.volumePriorities)) {
                    clearPriorityRows();
                    proc.volumePriorities.forEach(row => addPriorityRow(row));
                }
                if (rcPriorityPrefillWarning) rcPriorityPrefillWarning.hidden = !legacy;
                // E3.5-4 — 축 복원. 구 저장본(null)은 값을 임의로 채우지 않고 경고만(D-7 기본값 없는 필수 선택).
                if (proc.existingConfigPolicy) {
                    const policyRadio = document.querySelector('input[name="rcExistingPolicyRadio"][value="' + proc.existingConfigPolicy + '"]');
                    if (policyRadio) policyRadio.checked = true;
                } else if ((proc.diskGroups || []).some(g => g.raidLevel)) {
                    const policyWarn = document.getElementById('rcExistingPolicyPrefillWarning');
                    if (policyWarn) policyWarn.hidden = false;
                }
                applyDiskGroupConstraints();
            },
            OS_INSTALLATION: function (proc) {
                // R11 식별 전용 — 구(상세) 저장본을 열어도 식별만 복원한다(상세는 화면에서 걷힘, D-R6).
                if (proc.osMetadataId != null && oiOsSelect) {
                    oiOsSelect.value = String(proc.osMetadataId);
                    onInstallOsChange();
                }
                if (proc.isoId != null && oiIsoSelect) {
                    oiIsoSelect.value = String(proc.isoId); // 소실 시 매칭 실패 → placeholder 유지
                    commitDeprecatedSelection(oiIsoSelect);
                }
                // E4-1-a-2 — Windows 계열: 설치 이미지(소스에 없으면 경고) · 비밀번호(서버가 값을 제거하고 keepExistingPassword=true 로 보낸다).
                if (proc.osFamily === 'WINDOWS') {
                    syncWindowsBlock();
                    if (oiWindowsImage && proc.imageName != null) {
                        oiWindowsImage.value = proc.imageName;
                        const matched = oiWindowsImage.value === proc.imageName;
                        if (!matched) oiWindowsImage.value = ''; // 소스에 없는 이미지 — 빈 칸이 아니라 placeholder 로(CP5 O-2)
                        if (oiWindowsImagePrefillWarning) {
                            oiWindowsImagePrefillWarning.hidden = matched;
                            if (!matched) {
                                oiWindowsImagePrefillWarning.textContent =
                                    '저장된 설치 이미지가 현재 설치 소스에 없습니다: ' + proc.imageName + '. 다시 선택하십시오.';
                            }
                        }
                    }
                    if (proc.administratorPassword && proc.administratorPassword.keepExistingPassword) markWinAdminHasExisting();
                }
                commitDeprecatedSelection(oiOsSelect); // 기존 사용분 deprecated 뱃지 (modal 없음)
            },
            OS_SETTING: function (proc) {
                if (proc.osMetadataId != null && osOsSelect) {
                    osOsSelect.value = String(proc.osMetadataId);
                    onSettingOsChange();
                }
                if (proc.selinuxMode) document.getElementById('osSelinuxMode').value = proc.selinuxMode;
                (proc.services || []).forEach(svc => addServiceRow({
                    name: svc && svc.name ? svc.name : '',
                    action: svc && svc.action ? svc.action : 'ENABLE'
                }));
                document.getElementById('osAdditionalPackages').value =
                    (proc.additionalPackages || []).join(', ');
                commitDeprecatedSelection(osOsSelect); // 기존 사용분 deprecated 뱃지 (modal 없음)
            }
        };

        function initEditPrefill() {
            if (form.dataset.mode !== 'edit') return;
            const initial = window.SETTING_INITIAL;
            if (!initial || !initial.json) return;
            let data;
            try {
                data = JSON.parse(initial.json);
            } catch (e) {
                console.warn('[settingForm] initialSettingJson 파싱 실패:', e);
                showBanner('기존 정의서 데이터를 불러오지 못했습니다. 새로 입력해 주세요.');
                return;
            }
            if (data.name) document.getElementById('settingName').value = data.name;
            (Array.isArray(data.processList) ? data.processList : []).forEach(proc => {
                const type = proc && proc.type;
                if (!type || !cardOf(type)) return;
                addStep(type);
                const prefiller = stepPrefillers[type];
                if (prefiller) prefiller(proc);
            });
            // pre-fill 말미 재평가 — BASIC_UPDATE 의 보드 복원은 change 미발화(프로그램적 대입)라
            // 단계 pre-fill 순서와 무관하게 최종 보드 상태 기준으로 템플릿 규칙을 정렬한다
            // (저장본은 서버 검증 통과분이라 위반 해제는 발동하지 않는다).
            refreshBsTemplateRules();
        }

        initEditPrefill();
    });
})();
