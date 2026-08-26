package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.setting.BiosSettingTarget;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcessSnapshot;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings.FrozenBiosTemplate;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * E3-1 D-3 — 목표 공급. empty(창 밖)와 빈 목표(NO_TARGET)를 가르는 것, 그리고 <b>감지 보드와 일치하는 템플릿만</b>
 * 선언 순서로 병합하고 같은 속성은 뒤가 이긴다는 것을 고정한다 — 다른 보드의 AMI 키를 PATCH 하면 BMC 가
 * 거절하거나 엉뚱한 속성을 건드린다.
 */
@ExtendWith(MockitoExtension.class)
class BiosSettingResolutionProviderImplTest {

    private static final UUID GUEST = UUID.randomUUID();
    private static final long BOARD = 3L;

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @Mock GuestServerDetailRepository detailRepository;
    @InjectMocks BiosSettingResolutionProviderImpl provider;

    @Test
    @DisplayName("활성 할당이 없으면 empty — 창 밖(판정 대상 아님)")
    void noActiveAssignment_isEmpty() {
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(Optional.empty());

        assertThat(provider.resolveFor(GUEST)).isEmpty();
    }

    @Test
    @DisplayName("할당은 있어도 BASIC_SETTING 동결이 없으면 empty — 정의서에 BIOS 설정 단계가 없다")
    void assignmentWithoutBiosSettings_isEmpty() {
        SettingAssignmentSnapshot active = snapshot((FrozenBiosSettings) null);
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(Optional.of(active));

        assertThat(provider.resolveFor(GUEST)).isEmpty();
    }

    @Test
    @DisplayName("보드가 하나도 맞지 않으면 빈 목표 — 창 안이지만 할 일이 없다(NO_TARGET)")
    void noMatchingBoard_isEmptyTarget() {
        SettingAssignmentSnapshot active = snapshot(frozen(template(1L, 9L, Map.of("BootMode", "Legacy"))));
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(Optional.of(active));
        given(detailRepository.findByServerIdWithBoardModel(GUEST)).willReturn(Optional.of(detail(BOARD)));

        Optional<BiosSettingTarget> target = provider.resolveFor(GUEST);

        assertThat(target).isPresent();
        assertThat(target.get().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("보드 일치 템플릿만 선언 순서로 병합하고 같은 속성은 뒤가 이긴다")
    void mergesMatchingTemplatesLaterWins() {
        SettingAssignmentSnapshot active = snapshot(frozen(
                        template(1L, BOARD, ordered("BootMode", "UEFI", "NumLock", "On")),
                        template(2L, 9L, Map.of("BootMode", "Legacy", "Other", "x")),
                        template(3L, BOARD, ordered("NumLock", "Off", "Csm", false))));
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(Optional.of(active));
        given(detailRepository.findByServerIdWithBoardModel(GUEST)).willReturn(Optional.of(detail(BOARD)));

        BiosSettingTarget target = provider.resolveFor(GUEST).orElseThrow();

        assertThat(target.attributes()).containsExactlyInAnyOrderEntriesOf(
                Map.of("BootMode", "UEFI", "NumLock", "Off", "Csm", false));
    }

    @Test
    @DisplayName("진단 상세(보드)가 없으면 빈 목표 — 어느 보드인지 모르면 아무 키도 쓰지 않는다")
    void noDetail_isEmptyTarget() {
        SettingAssignmentSnapshot active = snapshot(frozen(template(1L, BOARD, Map.of("BootMode", "UEFI"))));
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(Optional.of(active));
        given(detailRepository.findByServerIdWithBoardModel(any())).willReturn(Optional.empty());

        Optional<BiosSettingTarget> target = provider.resolveFor(GUEST);

        assertThat(target).isPresent();
        assertThat(target.get().isEmpty()).isTrue();
    }

    // ---- 픽스처 --------------------------------------------------------------

    /** 엔티티는 생성 경로가 길어 mock — 이 테스트가 보는 것은 getProcesses · getFrozenBiosSettings 뿐이다. */
    private static SettingAssignmentSnapshot snapshot(FrozenBiosSettings frozen) {
        AssignedProcessSnapshot process = mock(AssignedProcessSnapshot.class);
        given(process.getFrozenBiosSettings()).willReturn(frozen);
        SettingAssignmentSnapshot snapshot = mock(SettingAssignmentSnapshot.class);
        given(snapshot.getProcesses()).willReturn(List.of(process));
        return snapshot;
    }

    private static FrozenBiosSettings frozen(FrozenBiosTemplate... templates) {
        return new FrozenBiosSettings(Arrays.asList(templates));
    }

    private static FrozenBiosTemplate template(long id, long boardId, Map<String, Object> values) {
        Map<BiosAttributeName, BiosAttributeValue> entries = new LinkedHashMap<>();
        values.forEach((k, v) -> entries.put(BiosAttributeName.of(k), new BiosAttributeValue(v)));
        return new FrozenBiosTemplate(id, boardId, new BiosSettingValues(entries));
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    private static GuestServerDetail detail(long boardId) {
        return GuestServerDetail.builder().boardModel(BoardModel.builder().id(boardId).build()).build();
    }
}
