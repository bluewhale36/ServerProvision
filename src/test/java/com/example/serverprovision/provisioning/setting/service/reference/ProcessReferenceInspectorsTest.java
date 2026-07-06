package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * U2-3-1 CP4 — 1급 컬렉션의 등록 규약: 중복 target 기동 실패 / 미등록 fail-fast (D4 개정).
 */
class ProcessReferenceInspectorsTest {

    private ProcessReferenceInspector inspectorOf(SettingProcessType target) {
        ProcessReferenceInspector inspector = Mockito.mock(ProcessReferenceInspector.class);
        given(inspector.target()).willReturn(target);
        return inspector;
    }

    @Test
    @DisplayName("미등록 타입 조회 → IllegalStateException (설정 결함 조기 노출)")
    void inspectorFor_missingType_failsFast() {
        ProcessReferenceInspector basicSetting = inspectorOf(SettingProcessType.BASIC_SETTING);
        ProcessReferenceInspectors registry = new ProcessReferenceInspectors(List.of(basicSetting));

        assertThat(registry.inspectorFor(SettingProcessType.BASIC_SETTING)).isSameAs(basicSetting);
        assertThatThrownBy(() -> registry.inspectorFor(SettingProcessType.BASIC_UPDATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BASIC_UPDATE");
    }

    @Test
    @DisplayName("같은 target 중복 등록 → 구성 시점 실패 (조기 결함 노출)")
    void duplicateTarget_failsAtConstruction() {
        assertThatThrownBy(() -> new ProcessReferenceInspectors(
                List.of(inspectorOf(SettingProcessType.BASIC_SETTING), inspectorOf(SettingProcessType.BASIC_SETTING))))
                .isInstanceOf(IllegalStateException.class);
    }
}
