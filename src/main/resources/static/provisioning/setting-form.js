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
 *   OS 항목의 2단 판별자 osFamily: RHEL_BASED | DEBIAN_BASED
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

        function osFamilyPanes() {
            return Array.from(form.querySelectorAll('#oiDetailFields .n-os-family-pane'));
        }

        function onInstallOsChange() {
            const osId = oiOsSelect ? oiOsSelect.value : '';
            const opt = selectedOption(oiOsSelect);
            const family = opt && osId ? (opt.dataset.osFamily || '') : '';

            if (oiGuide) oiGuide.hidden = !!osId;
            if (oiDetailFields) oiDetailFields.hidden = !osId;
            if (oiDefaultPartitions) oiDefaultPartitions.disabled = !osId;

            // 계열 pane 하나만 표시 (RHEL_BASED / DEBIAN_BASED)
            osFamilyPanes().forEach(pane => {
                pane.hidden = !(family && pane.dataset.osFamily === family);
            });
            // root 비밀번호는 RHEL 계열 전용 — Ubuntu 는 root 잠금 기본(identity 사용자 필수).
            const rootGroup = document.getElementById('oiRootPasswordGroup');
            if (rootGroup) rootGroup.hidden = family !== 'RHEL_BASED';

            filterEnvironmentOptions(osId);
            applyPackageGroupFilter();
            dispatchVersionSpecificBox(opt && osId ? opt : null);
            syncOsSelectionLock();
        }

        /** 설치 환경 옵션을 선택된 osMetadataId(data-os-id) 로 필터링한다. */
        function filterEnvironmentOptions(osId) {
            if (!oiEnvironment) return;
            const placeholder = oiEnvironment.querySelector('option[data-placeholder]');
            oiEnvironment.disabled = !osId;
            if (placeholder) {
                placeholder.textContent = osId ? '환경을 선택하세요' : 'OS 를 먼저 선택하세요';
                placeholder.selected = true;
            }
            oiEnvironment.querySelectorAll('option[data-os-id]').forEach(opt => {
                const match = !!osId && opt.dataset.osId === osId;
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
            const osId = oiOsSelect ? oiOsSelect.value : '';
            const envOpt = oiEnvironment ? oiEnvironment.selectedOptions[0] : null;
            const allowed = envOpt && envOpt.dataset.groupIds != null
                ? envOpt.dataset.groupIds.split(',').filter(Boolean)
                : null; // null = 환경 미선택
            let visible = 0;
            form.querySelectorAll('.n-pkg-group').forEach(row => {
                const chk = row.querySelector('input[type="checkbox"]');
                const match = !!osId && row.dataset.osId === osId
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

        /** grow 체크 시 크기 입력 비활성화 + 같은 디스크 그룹의 다른 grow 해제 (디스크당 1개). */
        function onGrowChange(checkbox) {
            const row = checkbox.closest('tr');
            const sizeInput = row.querySelector('.pSize');
            if (checkbox.checked) {
                sizeInput.disabled = true;
                sizeInput.value = '';
                sizeInput.classList.remove('has-error');
                const diskName = row.querySelector('.pDiskName').value.trim();
                oiPartitionTbody.querySelectorAll('tr').forEach(other => {
                    if (other === row) return;
                    if (other.querySelector('.pDiskName').value.trim() !== diskName) return;
                    const otherGrow = other.querySelector('.pGrow');
                    if (otherGrow.checked) {
                        otherGrow.checked = false;
                        other.querySelector('.pSize').disabled = false;
                    }
                });
            } else {
                sizeInput.disabled = false;
            }
        }

        function addPartitionRow(data) {
            const row = cloneTemplateRow('tplPartitionRow');
            if (!row || !oiPartitionTbody) return;
            const d = data || {};
            row.querySelector('.pMountPoint').value = d.mountPoint || '';
            if (d.fileSystem) row.querySelector('.pFileSystem').value = d.fileSystem;
            row.querySelector('.pDiskName').value = d.diskName || '';
            row.querySelector('.pSizeUnit').value = d.sizeUnit || 'GB';
            const grow = !!d.isGrow;
            row.querySelector('.pGrow').checked = grow;
            const sizeInput = row.querySelector('.pSize');
            sizeInput.value = (!grow && d.size != null && d.size !== 0) ? d.size : '';
            sizeInput.disabled = grow;

            row.querySelector('.pMountPoint').addEventListener('input', () => applyFsConstraint(row));
            row.querySelector('.pGrow').addEventListener('change', e => onGrowChange(e.target));
            bindRowRemove(row);
            oiPartitionTbody.appendChild(row);
            applyFsConstraint(row);
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
                diskName: row.querySelector('.pDiskName').value.trim() || null,
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
            OS_INSTALLATION: function () {
                const opt = selectedOption(oiOsSelect);
                const osFamily = opt && oiOsSelect.value ? (opt.dataset.osFamily || null) : null;
                const payload = {
                    type: 'OS_INSTALLATION',
                    osFamily: osFamily,
                    osMetadataId: intOrNull(oiOsSelect.value),
                    timezone: {
                        timezone: document.getElementById('oiTimezone').value.trim(),
                        isUTC: document.getElementById('oiIsUtc').checked
                    },
                    partitions: buildPartitions(),
                    users: buildUsers()
                };
                if (osFamily === 'RHEL_BASED') {
                    payload.rootPassword = buildRootPassword(); // RHEL 전용 계약(Kickstart rootpw)
                    payload.environmentId = intOrNull(oiEnvironment.value);
                    payload.packageGroupIds = Array.from(
                        form.querySelectorAll('.n-pkg-group:not(.unavailable) input[type="checkbox"]:checked')
                    ).map(chk => intOrNull(chk.value)).filter(v => v != null);
                    payload.isKDumpEnabled = document.getElementById('oiKdump').checked;
                    // Rocky 10 전용 — 박스가 표시된 경우에만 포함 (미표시 = 미전송(null))
                    if (oiAllowSshBox && !oiAllowSshBox.hidden) {
                        payload.allowSshRoot = document.getElementById('oiAllowSshRoot').checked;
                    }
                } else if (osFamily === 'DEBIAN_BASED') {
                    payload.hostname = document.getElementById('oiHostname').value.trim();
                    payload.packages = splitCsv(document.getElementById('oiPackages').value);
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
                if ((proc.type === 'OS_INSTALLATION' || proc.type === 'OS_SETTING')
                    && (!proc.osFamily || proc.osMetadataId == null)) {
                    const card = cardOf(stepTypeByIndex[i]);
                    const select = card ? card.querySelector('[data-error-field="osMetadataId"]') : null;
                    if (select) paintFieldError(select, 'OS 를 선택해야 합니다.');
                    ok = false;
                }
                if (proc.type === 'BASIC_SETTING'
                    && (!proc.biosSettingTemplateIds || proc.biosSettingTemplateIds.length === 0)) {
                    const card = cardOf('BASIC_SETTING');
                    const panel = card ? card.querySelector('[data-error-field="biosSettingTemplateIds"]') : null;
                    if (panel) paintFieldError(panel, 'BIOS 세팅 템플릿을 1개 이상 선택해야 합니다.');
                    ok = false;
                }
            });
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
            OS_INSTALLATION: function (proc) {
                if (proc.osMetadataId != null && oiOsSelect) {
                    oiOsSelect.value = String(proc.osMetadataId);
                    onInstallOsChange();
                }
                const tz = proc.timezone;
                if (tz) {
                    document.getElementById('oiTimezone').value = tz.timezone || '';
                    document.getElementById('oiIsUtc').checked = pickBool(tz, 'isUTC', 'utc', 'UTC');
                }
                (proc.partitions || []).forEach(p => addPartitionRow({
                    mountPoint: p.mountPoint,
                    fileSystem: p.fileSystem,
                    diskName: p.diskName,
                    size: p.size,
                    sizeUnit: p.sizeUnit,
                    isGrow: pickBool(p, 'isGrow', 'grow')
                }));
                if (proc.osFamily === 'RHEL_BASED' && proc.rootPassword) {
                    markRootHasExisting();
                    document.getElementById('oiRootEncrypted').checked =
                        pickBool(proc.rootPassword, 'isPasswordEncrypted', 'passwordEncrypted');
                }
                (proc.users || []).forEach(u => addUserRow({
                    username: u.username,
                    isSudoer: pickBool(u, 'isSudoer', 'sudoer'),
                    isPasswordEncrypted: pickBool(u, 'isPasswordEncrypted', 'passwordEncrypted')
                }, true));
                if (proc.osFamily === 'RHEL_BASED') {
                    if (proc.environmentId != null && oiEnvironment) {
                        oiEnvironment.value = String(proc.environmentId);
                        applyPackageGroupFilter(); // 환경 확정 후 허용 그룹 행을 노출시켜야 체크 복원이 유효
                    }
                    const pkgIds = Array.isArray(proc.packageGroupIds) ? proc.packageGroupIds : [];
                    form.querySelectorAll('.n-pkg-group:not(.unavailable) input[type="checkbox"]').forEach(chk => {
                        chk.checked = pkgIds.indexOf(parseInt(chk.value, 10)) >= 0;
                    });
                    document.getElementById('oiKdump').checked =
                        pickBool(proc, 'isKDumpEnabled', 'kdumpEnabled', 'kDumpEnabled', 'KDumpEnabled');
                    if (oiAllowSshBox && !oiAllowSshBox.hidden && typeof proc.allowSshRoot === 'boolean') {
                        document.getElementById('oiAllowSshRoot').checked = proc.allowSshRoot;
                    }
                } else if (proc.osFamily === 'DEBIAN_BASED') {
                    document.getElementById('oiHostname').value = proc.hostname || '';
                    document.getElementById('oiPackages').value = (proc.packages || []).join(', ');
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
