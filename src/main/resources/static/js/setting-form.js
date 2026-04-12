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
        const currentOrder = parseInt(changedCheckbox.getAttribute('data-order'));
        const isChecked = changedCheckbox.checked;

        document.querySelectorAll('.step-enable-chk').forEach(chk => {
            const order = parseInt(chk.getAttribute('data-order'));
            let shouldBeChecked = chk.checked;
            if (isChecked && order <= currentOrder) shouldBeChecked = true;
            if (!isChecked && order >= currentOrder) shouldBeChecked = false;

            if (chk.checked !== shouldBeChecked || chk === changedCheckbox) {
                chk.checked = shouldBeChecked;
                const contentArea = document.getElementById('content_' + chk.value);
                const collapseEl = document.getElementById('collapse_' + chk.value);

                if (contentArea && collapseEl) {
                    if (chk.checked) {
                        contentArea.style.opacity = '1';
                        contentArea.style.pointerEvents = 'auto';
                        contentArea.querySelectorAll('.target-req').forEach(el => el.required = true);
                        if (!collapseEl.classList.contains('show')) {
                            (bootstrap.Collapse.getInstance(collapseEl) || new bootstrap.Collapse(collapseEl, {toggle: false})).show();
                        }
                    } else {
                        contentArea.style.opacity = '0.4';
                        contentArea.style.pointerEvents = 'none';
                        contentArea.querySelectorAll('.target-req').forEach(el => {
                            el.required = false;
                            if (el.tagName === 'INPUT' && el.type !== 'checkbox') el.value = '';
                            if (el.tagName === 'SELECT') el.selectedIndex = 0;
                        });
                        if (collapseEl.classList.contains('show')) {
                            (bootstrap.Collapse.getInstance(collapseEl) || new bootstrap.Collapse(collapseEl, {toggle: false})).hide();
                        }
                    }
                }
            }
        });
    }

    // 1단계: OS 종류 선택 → 버전 셀렉트 동적 갱신 + 기본 파티션 버튼 활성화
    function onOsNameChange(selectedOsName) {
        // OS 가 선택된 경우에만 기본 파티션 자동 생성 버튼 활성화
        const btnDefault = document.getElementById('btnDefaultPartitions');
        if (btnDefault) btnDefault.disabled = !selectedOsName;

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

    // 2단계: 버전 선택 → 환경 셀렉트 동적 갱신
    function onVersionChange(selectedMetadataId) {
        const envSelect = document.getElementById('environmentId');
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
                const pkgGroupIds = Array.from(
                    document.querySelectorAll('.pkg-group-row:not(.unavailable) input[type=checkbox]:checked')
                ).map(cb => parseInt(cb.value));
                processList.push({
                    type: "OS_INSTALLATION",
                    osMetadataId: parseInt(document.getElementById('osMetadataId').value),
                    isKDumpEnabled: document.getElementById('isKDumpEnabled').checked,
                    timezone: {
                        timezone: document.getElementById('timezone').value,
                        isUTC: document.getElementById('isUTC').checked
                    },
                    environmentId: parseInt(document.getElementById('environmentId').value),
                    packageGroupIds: pkgGroupIds,
                    partitions,
                    rootPassword,
                    users
                });
                stepTypeByIndex.push(stepName);
            } else if (stepName === 'OS_SETTING') {
                processList.push({type: "OS_SETTING"});
                stepTypeByIndex.push(stepName);
            }
        });

        const payload = {name: document.getElementById('settingName').value, processList};
        return {payload, stepTypeByIndex};
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
        buildSettingPayload
    };

})(window);
