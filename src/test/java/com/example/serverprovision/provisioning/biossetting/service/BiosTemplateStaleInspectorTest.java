package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.enums.BiosRegistrySource;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.biossetting.vo.ResolvedBiosRegistry;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.exception.BiosBoardNotFoundException;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** E3-3 R5 — 정의서 → 서버 보드의 BIOS 템플릿 → 해석된 레지스트리 대조. 문구는 할당 tooltip 이자 409 사유다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BiosTemplateStaleInspectorTest {

    @Mock SettingDefinitionRepository definitionRepository;
    @Mock BiosSettingTemplateRepository templateRepository;
    @Mock BiosRegistryResolver resolver;
    @InjectMocks BiosTemplateStaleInspector inspector;

    private static BoardModel board(long id) {
        BoardModel b = mock(BoardModel.class);
        given(b.getId()).willReturn(id);
        return b;
    }

    private static BiosSettingTemplate template(String name, BoardModel board, String value) {
        return BiosSettingTemplate.builder().name(name).boardModel(board)
                .values(new BiosSettingValues(Map.of(BiosAttributeName.of("Whitley0000"), BiosAttributeValue.ofString(value))))
                .build();
    }

    private static ResolvedBiosRegistry f44() {
        BiosAttribute attr = new BiosAttribute(BiosAttributeName.of("Whitley0000"), BiosAttributeType.ENUMERATION,
                "SpeedStep", null, null, false, false, "Enable",
                List.of(new BiosEnumOption("Disable", "Disable"), new BiosEnumOption("Enable", "Enable")), null, null);
        BiosSetupMenu menu = new BiosSetupMenu("MD72-HB3", List.of(), Map.of(), Map.of(attr.name(), attr), List.of());
        return new ResolvedBiosRegistry(menu, BiosRegistrySource.SNAPSHOT_TARGET, "F44", "F44",
                java.time.LocalDateTime.of(2026, 8, 27, 15, 31), "192.168.1.130");
    }

    private void definitionWithTemplates(long... ids) {
        SettingProcess process = mock(SettingProcess.class);
        given(process.getTemplateRefs()).willReturn(new java.util.LinkedHashSet<>(
                java.util.Arrays.stream(ids).boxed().toList()));
        SettingDefinition definition = mock(SettingDefinition.class);
        given(definition.getProcesses()).willReturn(List.of(process));
        given(definitionRepository.findById(7L)).willReturn(Optional.of(definition));
    }

    @Test
    @DisplayName("서버 보드의 템플릿 값이 허용 밖이면 차단 문구 — 템플릿명 · 레지스트리 출처 · 어긋난 속성")
    void stale_returnsReason() {
        BoardModel md72 = board(5L);
        BoardModel ms03 = board(1L);
        definitionWithTemplates(5L, 1L);
        List<BiosSettingTemplate> templates = List.of(
                template("MD72-HB3 공장 표준 세팅", md72, "Disabled"),
                template("MS03 표준", ms03, "Disabled"));   // 다른 보드 — 대조 대상 아님
        ResolvedBiosRegistry f44 = f44();
        given(templateRepository.findAllById(any())).willReturn(templates);
        given(resolver.resolve(md72)).willReturn(f44);

        String reason = inspector.reasonFor(7L, 5L);

        assertThat(reason).isEqualTo("BIOS 템플릿 'MD72-HB3 공장 표준 세팅' 의 값이 보드 레지스트리(F44 · 2026-08-27 채집 · 192.168.1.130)"
                + "와 어긋납니다 — Whitley0000 = Disabled — 허용 {Disable, Enable}");
    }

    @Test
    @DisplayName("정합 · 보드 미확정 · 그 보드의 템플릿 없음 · 자료 없는 보드 — 전부 null(막지 않는다)")
    void notStale_isNull() {
        BoardModel md72 = board(5L);
        definitionWithTemplates(5L);
        List<BiosSettingTemplate> templates = List.of(template("T", md72, "Disable"));
        ResolvedBiosRegistry f44 = f44();
        given(templateRepository.findAllById(any())).willReturn(templates);
        given(resolver.resolve(md72)).willReturn(f44);

        assertThat(inspector.reasonFor(7L, null)).isNull();   // 보드 미확정 — 해석기를 부르지도 않는다
        assertThat(inspector.reasonFor(7L, 9L)).isNull();     // 보드 9 의 템플릿이 없다
        verify(resolver, never()).resolve(any());

        assertThat(inspector.reasonFor(7L, 5L)).isNull();     // 정합

        given(resolver.resolve(md72)).willThrow(new BiosBoardNotFoundException("MD72-HB3"));
        assertThat(inspector.reasonFor(7L, 5L)).isNull();
    }
}
