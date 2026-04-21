/**
 * 세팅 주문서 작성/수정 폼 공유 로직.
 * new.html 과 edit.html 이 공통으로 사용하는 함수 12개 + FS_CONSTRAINT 상수를 담는다.
 * window.SettingForm 네임스페이스로 노출된다.
 */
(function (window) {
    'use strict';

    /**
     * 마운트포인트-파일시스템 조합 제약 (백엔드 LinuxInstallation.validatePartitionFileSystems 와 동기화).
     *   /boot/efi → EFI 강제
     *   swap      → SWAP 강제
     *   그 외     → EFI·SWAP 비활성화, EXT3·EXT4·XFS 만 선택 가능
     */
    const FS_CONSTRAINT = {
        FIXED: {'/boot/efi': 'EFI', 'swap': 'SWAP'},
        BLOCKED: ['EFI', 'SWAP']
    };

    function checkIfBiosAndBmcAvailable(selectedBoardModelId) {
        const biosSelect = document.querySelector('select#boardBIOSId');
        const bmcSelect = document.querySelector('select#boardBMCId');
        const biosInfoOption = document.querySelector('select#boardBIOSId > option.info-option');
        const bmcInfoOption = document.querySelector('select#boardBMCId > option.info-option');

        if (selectedBoardModelId) {
            biosSelect.disabled = false;
            bmcSelect.disabled = false;
            biosInfoOption.innerText = 'BIOS 버전을 선택하세요';
            biosInfoOption.selected = true;
            bmcInfoOption.innerText = 'BMC 버전을 선택하세요';
            bmcInfoOption.selected = true;

            document.querySelectorAll('select#boardBIOSId > option[data-compatible-board-model-id]').forEach(opt => {
                const match = opt.dataset.compatibleBoardModelId === selectedBoardModelId;
                opt.classList.toggle('unavailable', !match);
                opt.classList.toggle('available', match);
                opt.disabled = !match;
            });
            document.querySelectorAll('select#boardBMCId > option[data-compatible-board-model-id]').forEach(opt => {
                const match = opt.dataset.compatibleBoardModelId === selectedBoardModelId;
                opt.classList.toggle('unavailable', !match);
                opt.classList.toggle('available', match);
                opt.disabled = !match;
            });
        } else {
            biosSelect.disabled = true;
            bmcSelect.disabled = true;
            biosInfoOption.innerText = '보드 모델을 먼저 선택하세요';
            biosInfoOption.selected = true;
            bmcInfoOption.innerText = '보드 모델을 먼저 선택하세요';
            bmcInfoOption.selected = true;
        }
    }

    function toggleStep(changedCheckbox) {
        // OS_INSTALLATION 과 OS_SETTING 은 독립적으로 켜고 끌 수 있다.
        // OS_SETTING 이 단독으로 켜진 경우, 내부의 standalone OS/버전 선택 패널이 대상 OS 를 지정한다.
        var affected = [changedCheckbox];

        affected.forEach(function(chk) {
            var contentArea = document.getElementById('content_' + chk.value);
            var collapseEl = document.getElementById('collapse_' + chk.value);
            if (!contentArea || !collapseEl) return;

            if (chk.checked) {
                contentArea.style.opacity = '1';
                contentArea.style.pointerEvents = 'auto';
                contentArea.querySelectorAll('.target-req').forEach(function(el) { el.required = true; });
                if (!collapseEl.classList.contains('show')) {
                    (bootstrap.Collapse.getInstance(collapseEl) || new bootstrap.Collapse(collapseEl, {toggle: false})).show();
                }
                // OS_INSTALLATION 활성화 시 현재 선택된 OS 패밀리에 맞춰 pane 가시성/필수속성을 재정렬
                if (chk.value === 'OS_INSTALLATION') {
                    var osNameSelect = document.getElementById('osNameSelect');
                    var osKey = osNameSelect ? osNameSelect.value : '';
                    dispatchOsFamilyPane(osKey);
                    var verSelect = document.getElementById('osMetadataId');
                    var selOpt = verSelect && verSelect.selectedIndex >= 0
                        ? verSelect.options[verSelect.selectedIndex] : null;
                    dispatchVersionSpecificBox(osKey, selOpt ? (selOpt.dataset.osVersion || '') : '');
                    var selVer = verSelect ? verSelect.value : '';
                    var df = document.getElementById('osDetailFields');
                    var vg = document.getElementById('osVersionGuide');
                    if (df) df.style.display = selVer ? '' : 'none';
                    if (vg) vg.style.display = selVer ? 'none' : '';
                }
                // OS_SETTING 활성화 시 OS 선택 상태에 따라 상세 필드 가시성 결정
                if (chk.value === 'OS_SETTING') {
                    refreshOsSettingVisibility();
                }
                // OS_INSTALLATION 활성화 시에도 OS_SETTING 쪽 standalone panel 표시 여부 갱신
                if (chk.value === 'OS_INSTALLATION') {
                    refreshOsSettingVisibility();
                }
            } else {
                contentArea.style.opacity = '0.4';
                contentArea.style.pointerEvents = 'none';
                contentArea.querySelectorAll('.target-req').forEach(function(el) {
                    el.required = false;
                    if (el.tagName === 'INPUT' && el.type !== 'checkbox') el.value = '';
                    if (el.tagName === 'SELECT') el.selectedIndex = 0;
                });
                // OS_INSTALLATION 비활성화 시 OS 설치의 pane + 상세 필드를 숨김으로 복귀.
                // OS_SETTING 의 가시성은 standalone 패널 기준으로 refreshOsSettingVisibility 가 재계산한다.
                if (chk.value === 'OS_INSTALLATION') {
                    document.querySelectorAll('.os-family-pane').forEach(function(p) { p.hidden = true; });
                    document.querySelectorAll('.os-version-specific-box').forEach(function(b) { b.hidden = true; });
                    var df2 = document.getElementById('osDetailFields');
                    var vg2 = document.getElementById('osVersionGuide');
                    if (df2) df2.style.display = 'none';
                    if (vg2) vg2.style.display = '';
                    refreshOsSettingVisibility();
                }
                // OS_SETTING 비활성화 시 내부 필드/패널 모두 숨김
                if (chk.value === 'OS_SETTING') {
                    var osd3 = document.getElementById('osSettingDetailFields');
                    var osg3 = document.getElementById('osSettingGuide');
                    var osp  = document.getElementById('osSettingStandalonePanel');
                    if (osd3) osd3.style.display = 'none';
                    if (osg3) osg3.style.display = '';
                    if (osp)  osp.style.display  = 'none';
                }
                if (collapseEl.classList.contains('show')) {
                    (bootstrap.Collapse.getInstance(collapseEl) || new bootstrap.Collapse(collapseEl, {toggle: false})).hide();
                }
            }
        });
    }

    /**
     * OS_SETTING 섹션 내부 UI 상태 재계산.
     * - OS_INSTALLATION 이 켜져 있으면: standalone 패널 숨김, 가시성은 OS 설치의 osNameSelect 기준
     * - OS_INSTALLATION 이 꺼져 있으면: standalone 패널 표시, 가시성은 osSettingOsName 기준
     * - OS_SETTING step 자체가 꺼져 있으면 모든 내부 요소 숨김
     */
    function refreshOsSettingVisibility() {
        var osSettingChk   = document.getElementById('chk_OS_SETTING');
        var osInstallChk   = document.getElementById('chk_OS_INSTALLATION');
        var standalonePanel = document.getElementById('osSettingStandalonePanel');
        var guide           = document.getElementById('osSettingGuide');
        var guideTitle      = document.getElementById('osSettingGuideTitle');
        var detailFields    = document.getElementById('osSettingDetailFields');

        var settingOn = !!(osSettingChk && osSettingChk.checked);
        var installOn = !!(osInstallChk && osInstallChk.checked);

        // OS_SETTING step 자체가 꺼진 경우 모두 숨김
        if (!settingOn) {
            if (standalonePanel) standalonePanel.style.display = 'none';
            if (guide)           guide.style.display           = '';
            if (detailFields)    detailFields.style.display    = 'none';
            return;
        }

        var hasOs;
        if (installOn) {
            // OS 설치 연동 모드: standalone 숨김, 설치의 OS 선택 여부로 가시성 결정
            if (standalonePanel) standalonePanel.style.display = 'none';
            var osNS = document.getElementById('osNameSelect');
            hasOs = !!(osNS && osNS.value);
            if (guideTitle) guideTitle.textContent = 'OS 설치 단계에서 OS를 먼저 선택해 주세요';
        } else {
            // 단독 모드: standalone 표시, standalone 의 OS 선택 여부로 가시성 결정
            if (standalonePanel) standalonePanel.style.display = '';
            var saOs = document.getElementById('osSettingOsName');
            hasOs = !!(saOs && saOs.value);
            if (guideTitle) guideTitle.textContent = '대상 OS를 먼저 선택해 주세요';
        }

        if (guide)        guide.style.display        = hasOs ? 'none' : '';
        if (detailFields) detailFields.style.display = hasOs ? '' : 'none';
    }

    // OS_SETTING standalone: OS 종류 선택 시 버전 셀렉트 동적 갱신
    function onOsSettingOsNameChange(selectedOsName) {
        var verSelect = document.getElementById('osSettingOsVersion');
        var infoOption = verSelect ? verSelect.querySelector('.info-os-setting-version-option') : null;

        if (!verSelect || !infoOption) {
            refreshOsSettingVisibility();
            return;
        }

        if (!selectedOsName) {
            verSelect.disabled = true;
            verSelect.value = '';
            infoOption.innerText = 'OS 종류를 먼저 선택하세요';
            infoOption.selected = true;
            verSelect.querySelectorAll('option[data-os-name]').forEach(function(o) {
                o.classList.add('unavailable');
                o.classList.remove('available');
                o.disabled = true;
            });
        } else {
            verSelect.disabled = false;
            verSelect.value = '';
            infoOption.innerText = '버전을 선택하세요';
            infoOption.selected = true;
            verSelect.querySelectorAll('option[data-os-name]').forEach(function(o) {
                var match = o.dataset.osName === selectedOsName;
                o.classList.toggle('unavailable', !match);
                o.classList.toggle('available', match);
                o.disabled = !match;
            });
        }
        refreshOsSettingVisibility();
    }

    // OS_SETTING standalone: 버전 선택 시 가시성 갱신
    function onOsSettingVersionChange(_selectedMetadataId) {
        refreshOsSettingVisibility();
    }

    /**
     * OS_INSTALLATION step 이 현재 활성 상태(체크박스 ON)인지 반환한다.
     * dispatchOsFamilyPane 의 required 복원 조건으로 사용된다.
     */
    function isOsInstallationStepEnabled() {
        const chk = document.getElementById('chk_OS_INSTALLATION');
        return !!(chk && chk.checked);
    }

    /**
     * 선택된 OS 의 family 에 매칭되는 .os-family-pane 하나만 표시하고, 나머지는 숨긴다.
     * 숨겨진 pane 의 .target-req 는 required=false 로 해제하여 폼 검증 누수를 방지한다.
     * OS_INSTALLATION step 이 비활성화 상태이면 보이는 pane 도 required 를 적용하지 않는다.
     *
     * @param {string} osNameKey 선택된 OS 의 OSName key (예: "ROCKY_LINUX", "UBUNTU") 혹은 빈 문자열
     */
    function dispatchOsFamilyPane(osNameKey) {
        const osNameSelect = document.getElementById('osNameSelect');
        const selectedOption = (osNameKey && osNameSelect)
            ? osNameSelect.querySelector(`option[value="${osNameKey}"]`)
            : null;
        const family = selectedOption ? (selectedOption.dataset.osFamily || '') : '';
        const stepOn = isOsInstallationStepEnabled();

        document.querySelectorAll('.os-family-pane').forEach(pane => {
            const matches = !!family && pane.dataset.osFamily === family;
            pane.hidden = !matches;
            pane.querySelectorAll('.target-req').forEach(el => {
                el.required = matches && stepOn;
            });
        });
    }

    /**
     * 활성화된 .os-family-pane 내부의 .os-version-specific-box 가시성을 토글한다.
     * box 의 data-os-name-key 와 data-os-version-prefix 가 선택된 OS/버전과 일치할 때만 표시.
     * 예: box name="ROCKY_LINUX" prefix="10." + osName="ROCKY_LINUX" version="10.0" → 표시,
     *     osName="CENTOS" version="10.0" → 숨김.
     * box 에 data-os-name-key 가 없으면 OS 이름 제약 없음 (family 내 공통 박스).
     *
     * @param {string} osNameKey     선택된 OSName key (예: "ROCKY_LINUX", "UBUNTU")
     * @param {string} versionString 선택된 os_metadata.version (예: "10.0", "9.5", "22.04.5")
     */
    function dispatchVersionSpecificBox(osNameKey, versionString) {
        const majorPrefix = versionString ? (versionString.split('.')[0] + '.') : '';
        document.querySelectorAll('.os-family-pane .os-version-specific-box').forEach(box => {
            const paneHidden = box.closest('.os-family-pane').hidden;
            const expectedName = box.dataset.osNameKey || '';
            const expectedPrefix = box.dataset.osVersionPrefix || '';
            const nameMatches = !expectedName || (!!osNameKey && osNameKey === expectedName);
            const prefixMatches = !!expectedPrefix && majorPrefix === expectedPrefix;
            box.hidden = paneHidden || !nameMatches || !prefixMatches;
            // 숨겨진 box 내부 required 필드는 검증 누수 방지를 위해 해제
            if (box.hidden) {
                box.querySelectorAll('.target-req').forEach(el => el.required = false);
            } else if (isOsInstallationStepEnabled()) {
                box.querySelectorAll('.target-req').forEach(el => el.required = true);
            }
        });
    }

    // 1단계: OS 종류 선택 → 버전 셀렉트 동적 갱신 + 기본 파티션 버튼 활성화 + family pane 디스패치
    function onOsNameChange(selectedOsName) {
        // OS 가 선택된 경우에만 기본 파티션 자동 생성 버튼 활성화
        const btnDefault = document.getElementById('btnDefaultPartitions');
        if (btnDefault) btnDefault.disabled = !selectedOsName;

        // family pane 디스패치 (선택 해제 시 모든 pane 숨김)
        dispatchOsFamilyPane(selectedOsName);

        // OS_SETTING 단계 가시성: OS 설치와 연동 모드이면 이 선택값이 반영됨
        refreshOsSettingVisibility();

        const verSelect = document.getElementById('osMetadataId');
        const infoOption = verSelect.querySelector('.info-version-option');
        if (!selectedOsName) {
            verSelect.disabled = true;
            infoOption.innerText = 'OS 종류를 먼저 선택하세요';
            infoOption.selected = true;
            document.querySelectorAll('#osMetadataId option[data-os-name]').forEach(o => {
                o.classList.add('unavailable');
                o.classList.remove('available');
                o.disabled = true;
            });
            onVersionChange('');
            return;
        }
        verSelect.disabled = false;
        infoOption.innerText = '버전을 선택하세요';
        infoOption.selected = true;
        document.querySelectorAll('#osMetadataId option[data-os-name]').forEach(o => {
            const match = o.dataset.osName === selectedOsName;
            o.classList.toggle('unavailable', !match);
            o.classList.toggle('available', match);
            o.disabled = !match;
        });
        onVersionChange('');
    }

    // 2단계: 버전 선택 → 상세 필드 표시 + 환경 셀렉트 동적 갱신 + version-specific box 디스패치
    function onVersionChange(selectedMetadataId) {
        // OS 상세 설정 영역 가시성 토글
        var detailFields = document.getElementById('osDetailFields');
        var versionGuide = document.getElementById('osVersionGuide');
        if (detailFields) detailFields.style.display = selectedMetadataId ? '' : 'none';
        if (versionGuide) versionGuide.style.display = selectedMetadataId ? 'none' : '';

        const envSelect = document.getElementById('environmentId');
        const verSelect = document.getElementById('osMetadataId');
        const osNameSelect = document.getElementById('osNameSelect');
        // 선택된 버전 옵션에서 실제 version 문자열을 추출하여 version-specific box 가시성 갱신
        const selOpt = selectedMetadataId
            ? verSelect.querySelector(`option[value="${selectedMetadataId}"]`)
            : null;
        const versionString = selOpt ? (selOpt.dataset.osVersion || '') : '';
        dispatchVersionSpecificBox(osNameSelect ? osNameSelect.value : '', versionString);

        const infoOption = envSelect.querySelector('.info-env-option');
        if (!selectedMetadataId) {
            envSelect.disabled = true;
            infoOption.innerText = '버전을 먼저 선택하세요';
            infoOption.selected = true;
            document.querySelectorAll('#environmentId option[data-metadata-id]').forEach(o => {
                o.classList.add('unavailable');
                o.classList.remove('available');
                o.disabled = true;
            });
            onEnvironmentChange('');
            return;
        }
        envSelect.disabled = false;
        infoOption.innerText = '환경을 선택하세요';
        infoOption.selected = true;
        let defaultEnvId = null;
        document.querySelectorAll('#environmentId option[data-metadata-id]').forEach(o => {
            const match = o.dataset.metadataId === selectedMetadataId;
            o.classList.toggle('unavailable', !match);
            o.classList.toggle('available', match);
            o.disabled = !match;
            if (match && o.dataset.default === 'true' && !defaultEnvId) defaultEnvId = o.value;
        });
        if (defaultEnvId) {
            envSelect.value = defaultEnvId;
            onEnvironmentChange(defaultEnvId);
        } else onEnvironmentChange('');
    }

    // 3단계: 환경 선택 → 패키지 그룹 체크박스 동적 갱신
    function onEnvironmentChange(selectedEnvId) {
        document.querySelectorAll('.pkg-group-row').forEach(row => {
            const match = selectedEnvId && row.dataset.envId === selectedEnvId;
            row.classList.toggle('unavailable', !match);
            row.classList.toggle('available', match);
            const chk = row.querySelector('input[type=checkbox]');
            if (chk) chk.checked = match && chk.dataset.default === 'true';
        });
        document.getElementById('pkgGroupPlaceholder').style.display = selectedEnvId ? 'none' : '';
    }

    function applyFsConstraint(row) {
        const mp = row.querySelector('.mountPoint').value.trim();
        const fsSelect = row.querySelector('.fileSystem');
        const forced = FS_CONSTRAINT.FIXED[mp];

        Array.from(fsSelect.options).forEach(opt => {
            if (forced) {
                // 강제 마운트포인트: 해당 파일시스템만 활성화
                opt.disabled = opt.value !== forced;
            } else {
                // 일반 마운트포인트: EFI·SWAP 비활성화
                opt.disabled = FS_CONSTRAINT.BLOCKED.includes(opt.value);
            }
        });

        if (forced) {
            fsSelect.value = forced;
        } else if (FS_CONSTRAINT.BLOCKED.includes(fsSelect.value)) {
            // 이미 선택된 값이 blocked 목록이면 EXT4 로 초기화
            fsSelect.value = 'EXT4';
        }
    }

    /**
     * grow 체크박스 변경 시:
     * - 체크: 크기 입력 비활성화·초기화, 같은 디스크 그룹의 다른 행 grow 해제 및 재활성화
     * - 해제: 크기 입력 활성화
     * 백엔드 LinuxInstallation.validateGrowConstraint 와 동일한 디스크 그룹핑 규칙.
     */
    function onGrowChange(checkbox) {
        const row = checkbox.closest('tr');
        const sizeInput = row.querySelector('.size');

        if (checkbox.checked) {
            // grow 활성화: 크기 입력 불필요 → 비활성화 및 에러 스타일 초기화
            sizeInput.disabled = true;
            sizeInput.value = '';
            sizeInput.classList.remove('has-error');

            // 같은 디스크 그룹의 다른 행 grow 해제 (디스크당 grow 1개 제한)
            const diskName = row.querySelector('.diskName').value.trim();
            document.querySelectorAll('#partitionTable tbody tr').forEach(otherRow => {
                if (otherRow === row) return;
                const otherDisk = otherRow.querySelector('.diskName').value.trim();
                if (diskName === otherDisk) {
                    const otherGrow = otherRow.querySelector('.isGrow');
                    if (otherGrow.checked) {
                        otherGrow.checked = false;
                        otherRow.querySelector('.size').disabled = false;
                    }
                }
            });
        } else {
            // grow 해제: 크기 입력 다시 활성화
            sizeInput.disabled = false;
        }
    }

    /**
     * 파티션 크기 클라이언트 사전 검증.
     * grow 미체크이면서 크기가 0 이하인 행의 size input 에 has-error 마킹 후 true 반환.
     * 에러 없으면 false 반환.
     */
    function hasPartitionSizeError() {
        let found = false;
        document.querySelectorAll('#partitionTable tbody tr').forEach(row => {
            const sizeInput = row.querySelector('.size');
            const isGrow = row.querySelector('.isGrow').checked;
            if (!isGrow && (parseInt(sizeInput.value) || 0) <= 0) {
                sizeInput.classList.add('has-error');
                // 값 입력 시 해당 input 에러 스타일만 제거
                sizeInput.addEventListener('input', function clear() {
                    sizeInput.classList.remove('has-error');
                    sizeInput.removeEventListener('input', clear);
                });
                found = true;
            }
        });
        if (found) {
            const partitionsDiv = document.querySelector('[data-error-field="partitions"]');
            FormError.paintField(partitionsDiv, '파티션의 크기는 반드시 지정되거나 grow 가 지정되어야 합니다.');
            partitionsDiv?.scrollIntoView({behavior: 'smooth', block: 'center'});
        }
        return found;
    }

    /**
     * 백엔드 API 에서 OS별 기본 파티션 프리셋을 가져와 파티션 테이블을 초기화한다.
     * 기존 행이 있으면 사용자에게 확인 후 교체한다.
     * size/sizeUnit 이 null 인 파티션(예: /)은 크기 입력란을 비워 사용자가 직접 입력하게 한다.
     */
    async function generateDefaultPartitions() {
        const osName = document.getElementById('osNameSelect').value;
        if (!osName) return;

        const tbody = document.querySelector('#partitionTable tbody');
        if (tbody.rows.length > 0 &&
            !confirm('기존 파티션 구성이 초기화됩니다. 계속하시겠습니까?')) return;

        let presets;
        try {
            const res = await fetch(`/pxe/v1/setting/api/default-partitions?osName=${encodeURIComponent(osName)}`);
            if (!res.ok) {
                alert('기본 파티션 정보를 불러오지 못했습니다.');
                return;
            }
            presets = await res.json();
        } catch (e) {
            alert('네트워크 오류가 발생했습니다.');
            return;
        }

        // 기존 행 제거 후 프리셋 행 삽입
        tbody.innerHTML = '';
        const fsOptions = ['EXT3', 'EXT4', 'XFS', 'EFI', 'SWAP'];
        presets.forEach(preset => {
            const fsOpts = fsOptions.map(v =>
                `<option value="${v}" ${v === preset.fileSystem ? 'selected' : ''}>${v.toLowerCase()}</option>`
            ).join('');
            const sizeVal = preset.size != null ? preset.size : '';
            // option value 는 SizeUnit enum 이름(MB/GB/TB)으로 유지하고, 표시 레이블만 IEC 이진 표기 사용
            const IEC_LABEL = {MB: 'MiB', GB: 'GiB', TB: 'TiB'};
            const unitSel = ['MB', 'GB', 'TB'].map(u =>
                `<option value="${u}" ${u === preset.sizeUnit ? 'selected' : ''}>${IEC_LABEL[u]}</option>`
            ).join('');
            tbody.insertAdjacentHTML('beforeend', `
                <tr>
                    <td><input type="text" class="form-control form-control-sm mountPoint"
                               value="${preset.mountPoint}"
                               oninput="SettingForm.applyFsConstraint(this.closest('tr'))"></td>
                    <td><select class="form-select form-select-sm fileSystem">${fsOpts}</select></td>
                    <td><input type="text" class="form-control form-control-sm diskName" placeholder="sda"></td>
                    <td><input type="number" class="form-control form-control-sm size"
                               min="1" value="${sizeVal}" placeholder="10"></td>
                    <td><select class="form-select form-select-sm sizeUnit">${unitSel}</select></td>
                    <td class="n-td-center">
                        <input type="checkbox" class="form-check-input isGrow"
                               onchange="SettingForm.onGrowChange(this)"></td>
                    <td class="n-td-center" style="white-space: nowrap;"><button type="button" class="btn btn-outline-danger btn-sm"
                                onclick="this.closest('tr').remove()">삭제</button></td>
                </tr>`);
            applyFsConstraint(tbody.lastElementChild);
        });
    }

    // 파티션 행 동적 추가
    function addPartitionRow() {
        const fsOptions = ['EXT3', 'EXT4', 'XFS', 'EFI', 'SWAP'];
        const tbody = document.querySelector('#partitionTable tbody');
        tbody.insertAdjacentHTML('beforeend', `
            <tr>
                <td><input type="text" class="form-control form-control-sm mountPoint"
                           placeholder="/boot"
                           oninput="SettingForm.applyFsConstraint(this.closest('tr'))"></td>
                <td><select class="form-select form-select-sm fileSystem">${fsOptions.map(v => `<option value="${v}">${v.toLowerCase()}</option>`).join('')}</select></td>
                <td><input type="text" class="form-control form-control-sm diskName" placeholder="sda"></td>
                <td><input type="number" class="form-control form-control-sm size" min="1" placeholder="10"></td>
                <td><select class="form-select form-select-sm sizeUnit">
                        <option value="MB">MiB</option>
                        <option value="GB" selected>GiB</option>
                        <option value="TB">TiB</option>
                    </select></td>
                <td class="n-td-center">
                    <input type="checkbox" class="form-check-input isGrow"
                           onchange="SettingForm.onGrowChange(this)"></td>
                <td class="n-td-center" style="white-space: nowrap;"><button type="button" class="btn btn-outline-danger btn-sm" onclick="this.closest('tr').remove()">삭제</button></td>
            </tr>`);
        // 새로 추가된 행에 초기 제약 적용 (기본값 EXT4, EFI·SWAP 미선택 상태에서도 옵션 상태 정합)
        applyFsConstraint(tbody.lastElementChild);
    }

    // 일반 사용자 행 동적 추가
    function addUserRow() {
        document.querySelector('#userTable tbody').insertAdjacentHTML('beforeend', `
            <tr>
                <td><input type="text" class="form-control form-control-sm username" placeholder="사용자명"></td>
                <td><input type="password" class="form-control form-control-sm password"></td>
                <td class="n-td-center"><input type="checkbox" class="form-check-input isSudoer"></td>
                <td class="n-td-center"><input type="checkbox" class="form-check-input isPasswordEncrypted"></td>
                <td class="n-td-center" style="white-space: nowrap;"><button type="button" class="btn btn-outline-danger btn-sm" onclick="this.closest('tr').remove()">삭제</button></td>
            </tr>`);
    }

    // OS_SETTING: 추가 패키지 행 동적 추가
    function addPackageRow() {
        document.querySelector('#additionalPackagesTable tbody').insertAdjacentHTML('beforeend', `
            <tr>
                <td><input type="text" class="form-control form-control-sm packageName" placeholder="예: vim, curl, wget"></td>
                <td class="n-td-center" style="white-space: nowrap;"><button type="button" class="btn btn-outline-danger btn-sm" onclick="this.closest('tr').remove()">삭제</button></td>
            </tr>`);
    }

    // OS_INSTALLATION (Ubuntu): APT 패키지 행 동적 추가
    function addUbuntuPackageRow() {
        const tbody = document.querySelector('#ubuntuPackagesTable tbody');
        if (!tbody) return;
        tbody.insertAdjacentHTML('beforeend', `
            <tr>
                <td><input type="text" class="form-control form-control-sm ubuntuPackageName" placeholder="예: vim, curl, openssh-server"></td>
                <td class="n-td-center" style="white-space: nowrap;"><button type="button" class="btn btn-outline-danger btn-sm" onclick="this.closest('tr').remove()">삭제</button></td>
            </tr>`);
    }

    // OS_SETTING: 서비스 지시(enable/disable) 행 동적 추가
    function addServiceRow(defaults) {
        const d = defaults || {};
        const enableSelected  = d.action === 'DISABLE' ? '' : 'selected';
        const disableSelected = d.action === 'DISABLE' ? 'selected' : '';
        const nameValue       = d.name ? String(d.name).replace(/"/g, '&quot;') : '';
        document.querySelector('#servicesTable tbody').insertAdjacentHTML('beforeend', `
            <tr>
                <td><input type="text" class="form-control form-control-sm serviceName"
                           value="${nameValue}"
                           placeholder="예: nginx, firewalld, sshd"></td>
                <td><select class="form-select form-select-sm serviceAction">
                        <option value="ENABLE" ${enableSelected}>활성화 (enable)</option>
                        <option value="DISABLE" ${disableSelected}>비활성화 (disable)</option>
                    </select></td>
                <td class="n-td-center" style="white-space: nowrap;"><button type="button" class="btn btn-outline-danger btn-sm" onclick="this.closest('tr').remove()">삭제</button></td>
            </tr>`);
    }

    /**
     * OS 이름 {@code <select>} 요소의 현재 선택값에서 {@code data-os-family} 속성을 읽어 반환한다.
     * 선택이 없거나 속성이 없으면 빈 문자열.
     */
    function readSelectedOsFamily(osNameSelect) {
        if (!osNameSelect) return '';
        const opt = osNameSelect.querySelector(`option[value="${osNameSelect.value}"]`);
        return opt ? (opt.dataset.osFamily || '') : '';
    }

    /** 체크된 step 들을 순서대로 순회하며 페이로드와 stepTypeByIndex 를 동시에 만든다. */
    function buildSettingPayload() {
        const processList = [];
        // 제출 시점의 processList 인덱스 ↔ step.name() 매핑.
        // FormError.renderResponse 가 "processList[i]" 경로를 DOM 스코프로 역매핑할 때 사용.
        const stepTypeByIndex = [];

        document.querySelectorAll('.step-enable-chk:checked').forEach(chk => {
            const stepName = chk.value;
            if (stepName === 'BASIC_UPDATE') {
                processList.push({
                    type: "BASIC_UPDATES",
                    boardModelId: parseInt(document.querySelector('#boardModelId').value),
                    boardBIOSId: parseInt(document.querySelector('#boardBIOSId').value),
                    boardBMCId: parseInt(document.querySelector('#boardBMCId').value)
                });
                stepTypeByIndex.push(stepName);
            } else if (stepName === 'BASIC_SETTING') {
                processList.push({type: "BASIC_SETTING"});
                stepTypeByIndex.push(stepName);
            } else if (stepName === 'OS_INSTALLATION') {
                const partitions = Array.from(document.querySelectorAll('#partitionTable tbody tr')).map(row => ({
                    mountPoint: row.querySelector('.mountPoint').value,
                    fileSystem: row.querySelector('.fileSystem').value,
                    diskName: row.querySelector('.diskName').value || null,
                    size: parseInt(row.querySelector('.size').value) || 0,
                    sizeUnit: row.querySelector('.sizeUnit').value,
                    isGrow: row.querySelector('.isGrow').checked
                }));
                const rootPwValue = document.getElementById('rootPassword').value;
                // edit.html 전용: 기존 비밀번호 힌트가 표시되어 있고 필드가 비어 있으면 keepExistingPassword 전송
                const rootPasswordExistHint = document.getElementById('rootPasswordExistHint');
                const keepExistingRootPw = rootPasswordExistHint &&
                    rootPasswordExistHint.style.display !== 'none' &&
                    !rootPwValue;
                let rootPassword;
                if (rootPwValue) {
                    rootPassword = {
                        password: rootPwValue,
                        isPasswordEncrypted: document.getElementById('rootPasswordEncrypted').checked
                    };
                } else if (keepExistingRootPw) {
                    rootPassword = {
                        keepExistingPassword: true,
                        isPasswordEncrypted: document.getElementById('rootPasswordEncrypted').checked
                    };
                } else {
                    rootPassword = null;
                }
                const users = Array.from(document.querySelectorAll('#userTable tbody tr')).map(row => {
                    const pwInput = row.querySelector('.password');
                    // edit.html 전용: data-has-existing-password 가 있고 필드가 비어 있으면 keepExistingPassword 전송
                    const keepExistingPw = pwInput.dataset.hasExistingPassword === 'true' && !pwInput.value;
                    return {
                        username: row.querySelector('.username').value,
                        password: keepExistingPw ? null : pwInput.value,
                        isSudoer: row.querySelector('.isSudoer').checked,
                        isPasswordEncrypted: row.querySelector('.isPasswordEncrypted').checked,
                        keepExistingPassword: keepExistingPw
                    };
                });

                // 선택된 OS 의 family 판별자를 option data attribute 로부터 추출
                const osFamily = readSelectedOsFamily(document.getElementById('osNameSelect'));

                const osInstallation = {
                    type: "OS_INSTALLATION",
                    osFamily,
                    osMetadataId: parseInt(document.getElementById('osMetadataId').value),
                    timezone: {
                        timezone: document.getElementById('timezone').value,
                        isUTC: document.getElementById('isUTC').checked
                    },
                    partitions,
                    rootPassword,
                    users
                };

                // OS_ENUMS 는 new.html / edit.html 의 th:inline 스크립트에서 Java OSFamily/OSName
                // enum 값을 직접 주입받는다. 하드코딩된 'RHEL_BASED' 등 리터럴은 백엔드와 동기화 보장이
                // 없으므로 항상 OS_ENUMS 경유로 비교한다.
                const FAMILY = window.OS_ENUMS.FAMILY;
                const NAME   = window.OS_ENUMS.NAME;
                if (osFamily === FAMILY.RHEL_BASED) {
                    osInstallation.environmentId = parseInt(document.getElementById('environmentId').value);
                    osInstallation.packageGroupIds = Array.from(
                        document.querySelectorAll('.pkg-group-row:not(.unavailable) input[type=checkbox]:checked')
                    ).map(cb => parseInt(cb.value));
                    osInstallation.isKDumpEnabled = document.getElementById('isKDumpEnabled').checked;
                    // Rocky 10 전용 allowSshRoot: 버전 box 가 표시되어 있을 때만 포함.
                    // box 가 숨겨져 있으면 백엔드에서 null/기본값 처리됨.
                    const allowSshBox = document.querySelector(
                        `.os-version-specific-box[data-os-name-key="${NAME.ROCKY_LINUX}"][data-os-version-prefix="10."]`
                    );
                    if (allowSshBox && !allowSshBox.hidden) {
                        const allowSshChk = document.getElementById('allowSshRoot');
                        osInstallation.allowSshRoot = !!(allowSshChk && allowSshChk.checked);
                    }
                } else if (osFamily === FAMILY.DEBIAN_BASED) {
                    const hostnameInput = document.getElementById('ubuntuHostname');
                    osInstallation.hostname = hostnameInput ? hostnameInput.value.trim() : '';
                    osInstallation.packages = Array.from(
                        document.querySelectorAll('#ubuntuPackagesTable tbody .ubuntuPackageName')
                    ).map(inp => inp.value.trim()).filter(v => v.length > 0);
                }

                processList.push(osInstallation);
                stepTypeByIndex.push(stepName);
            } else if (stepName === 'OS_SETTING') {
                // 대상 OS 결정:
                //  - OS 설치 단계가 켜져 있으면 설치 섹션의 osNameSelect/osMetadataId 를 사용
                //  - 꺼져 있으면 OS 설정 내부의 standalone 패널 값 사용
                const installOnChk = document.getElementById('chk_OS_INSTALLATION');
                const installOn = !!(installOnChk && installOnChk.checked);

                const osNameEl = document.getElementById(installOn ? 'osNameSelect' : 'osSettingOsName');
                const osVersionEl = document.getElementById(installOn ? 'osMetadataId' : 'osSettingOsVersion');

                const osFamilyForSetting = readSelectedOsFamily(osNameEl);
                const osMetadataIdForSetting = osVersionEl && osVersionEl.value
                        ? parseInt(osVersionEl.value)
                        : null;

                // SELinux 모드 수집
                const selinuxModeEl = document.getElementById('selinuxMode');
                const selinuxMode = selinuxModeEl ? selinuxModeEl.value : 'enforcing';

                // 추가 패키지 목록 수집 (빈 값 제외)
                const additionalPackages = Array.from(
                    document.querySelectorAll('#additionalPackagesTable tbody tr')
                ).map(row => row.querySelector('.packageName').value.trim())
                 .filter(v => v.length > 0);

                // 서비스 지시 목록 수집 ({name, action} 구조, 빈 name 제외)
                const services = Array.from(
                    document.querySelectorAll('#servicesTable tbody tr')
                ).map(row => {
                    const nameEl   = row.querySelector('.serviceName');
                    const actionEl = row.querySelector('.serviceAction');
                    const name = nameEl ? nameEl.value.trim() : '';
                    const action = actionEl && actionEl.value ? actionEl.value : 'ENABLE';
                    return {name, action};
                }).filter(d => d.name.length > 0);

                // RHEL 계열만 구체 서브타입이 준비되어 있다. Debian/Windows 는 추후 확장.
                const osSettingPayload = {
                    type: "OS_SETTING",
                    osFamily: osFamilyForSetting,
                    osMetadataId: osMetadataIdForSetting
                };
                if (osFamilyForSetting === window.OS_ENUMS.FAMILY.RHEL_BASED) {
                    osSettingPayload.selinuxMode        = selinuxMode;
                    osSettingPayload.additionalPackages = additionalPackages;
                    osSettingPayload.services           = services;
                }

                processList.push(osSettingPayload);
                stepTypeByIndex.push(stepName);
            }
        });

        const payload = {name: document.getElementById('settingName').value, processList};
        return {payload, stepTypeByIndex};
    }

    /**
     * buildSettingPayload 가 생성한 payload 를 서버에 보내기 전에 클라이언트에서 구조적으로 검증한다.
     * 특히 다형성 DTO (OSInstallationRequest / OSSettingRequest) 가 Jackson 역직렬화 단계에서
     * subtype 을 결정하지 못해 500 스택트레이스로 떨어지는 케이스를 사용자 친화적인 필드 에러로 변환한다.
     *
     * 반환값:
     *   {errors: [{field: "processList[i]." + localField, message}]}
     *   errors 가 비어 있으면 서버로 제출 가능.
     */
    function validateClientSide(payload, stepTypeByIndex) {
        const errors = [];
        const FAMILY = window.OS_ENUMS && window.OS_ENUMS.FAMILY ? window.OS_ENUMS.FAMILY : {};

        (payload.processList || []).forEach((p, i) => {
            if (p.type === 'OS_INSTALLATION') {
                if (!p.osFamily) {
                    errors.push({
                        field: 'processList[' + i + '].osMetadataId',
                        message: 'OS 설치 단계를 사용하려면 OS 종류와 버전을 선택해야 합니다.'
                    });
                } else if (p.osMetadataId == null || Number.isNaN(p.osMetadataId)) {
                    errors.push({
                        field: 'processList[' + i + '].osMetadataId',
                        message: '버전이 선택되지 않았습니다.'
                    });
                }
            } else if (p.type === 'OS_SETTING') {
                if (!p.osFamily) {
                    errors.push({
                        field: 'processList[' + i + '].osMetadataId',
                        message: 'OS 설정 단계를 사용하려면 대상 OS 와 버전을 먼저 선택해야 합니다.'
                    });
                } else if (p.osMetadataId == null || Number.isNaN(p.osMetadataId)) {
                    errors.push({
                        field: 'processList[' + i + '].osMetadataId',
                        message: '버전이 선택되지 않았습니다.'
                    });
                } else if (FAMILY.RHEL_BASED && p.osFamily !== FAMILY.RHEL_BASED) {
                    errors.push({
                        field: 'processList[' + i + '].osMetadataId',
                        message: 'OS 설정 단계는 현재 RHEL 계열(Rocky Linux, CentOS) 에서만 지원됩니다. 다른 계열은 지원 추가 예정입니다.'
                    });
                }
            }
        });

        return {errors};
    }

    /**
     * 주문서 저장 전 사전 검증을 실행한다.
     * 서버 {@code POST /api/validate} 로 payload 를 전송해 OS 저장소 인덱스 기반 경고 목록을 받는다.
     *
     * @param {object} payload SettingForm.buildSettingPayload() 로 생성된 본 요청 payload
     * @param {number|null} editId 수정 폼이면 대상 세팅 ID, 생성이면 null
     * @returns {Promise<{ok: boolean, warnings?: Array, errorBody?: object, status?: number}>}
     *          ok=true 이면 warnings(가능, 빈 배열 포함)를 포함. ok=false 이면 Bean Validation/
     *          Resolver 예외 등 본 요청 경로에서 재발생할 에러이므로 바로 렌더링에 사용한다.
     */
    async function preValidate(payload, editId) {
        const url = editId ? `/pxe/v1/setting/api/validate?id=${editId}` : '/pxe/v1/setting/api/validate';
        let response;
        try {
            response = await fetch(url, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(payload)
            });
        } catch (networkErr) {
            return {ok: false, errorBody: {message: '서버와 통신할 수 없습니다: ' + networkErr.message}};
        }

        if (!response.ok) {
            let errBody;
            try { errBody = await response.json(); }
            catch (_) { errBody = {message: '서버 응답 오류 (' + response.status + ')'}; }
            return {ok: false, errorBody: errBody, status: response.status};
        }

        const data = await response.json();
        return {ok: true, warnings: Array.isArray(data.warnings) ? data.warnings : []};
    }

    /**
     * 사전 검증 경고를 사용자에게 고지하는 모달. 확인 시 resolve(true), 취소 시 resolve(false).
     * 모달 DOM 은 new.html / edit.html 에 포함된 {@code #settingWarningModal} 를 재사용한다.
     */
    function showWarningModal(warnings) {
        return new Promise(resolve => {
            const modalEl = document.getElementById('settingWarningModal');
            if (!modalEl) {
                // 모달이 없는 환경이면 저장을 막지 않고 단순히 진행.
                resolve(true);
                return;
            }
            const listEl = modalEl.querySelector('#settingWarningList');
            if (listEl) {
                listEl.innerHTML = '';
                warnings.forEach(w => {
                    const li = document.createElement('li');
                    const value = document.createElement('code');
                    value.textContent = w.value || '';
                    const field = document.createElement('small');
                    field.className = 'text-muted ms-2';
                    field.textContent = w.field || '';
                    const msg = document.createElement('div');
                    msg.textContent = w.message || '';
                    li.appendChild(value);
                    li.appendChild(field);
                    li.appendChild(msg);
                    listEl.appendChild(li);
                });
            }

            const confirmBtn = modalEl.querySelector('#settingWarningConfirm');
            const cancelBtn = modalEl.querySelector('#settingWarningCancel');
            const bsModal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);

            function cleanup() {
                confirmBtn.removeEventListener('click', onConfirm);
                cancelBtn.removeEventListener('click', onCancel);
                modalEl.removeEventListener('hidden.bs.modal', onHidden);
            }
            function onConfirm() { cleanup(); bsModal.hide(); resolve(true); }
            function onCancel()  { cleanup(); bsModal.hide(); resolve(false); }
            function onHidden()  { cleanup(); resolve(false); }

            confirmBtn.addEventListener('click', onConfirm);
            cancelBtn.addEventListener('click', onCancel);
            modalEl.addEventListener('hidden.bs.modal', onHidden);
            bsModal.show();
        });
    }

    window.SettingForm = {
        checkIfBiosAndBmcAvailable,
        toggleStep,
        onOsNameChange,
        onVersionChange,
        onEnvironmentChange,
        applyFsConstraint,
        onGrowChange,
        hasPartitionSizeError,
        generateDefaultPartitions,
        addPartitionRow,
        addUserRow,
        addPackageRow,
        addUbuntuPackageRow,
        addServiceRow,
        buildSettingPayload,
        validateClientSide,
        preValidate,
        showWarningModal,
        // OS_SETTING 단독 모드 핸들러
        onOsSettingOsNameChange,
        onOsSettingVersionChange,
        refreshOsSettingVisibility,
        // OS family/version 디스패처 (edit.html pre-fill 초기화 경로에서도 호출 가능)
        dispatchOsFamilyPane,
        dispatchVersionSpecificBox
    };

})(window);
