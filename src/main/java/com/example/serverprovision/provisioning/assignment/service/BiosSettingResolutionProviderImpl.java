package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.setting.BiosSettingResolutionProvider;
import com.example.serverprovision.execution.engine.setting.BiosSettingTarget;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcessSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * BIOS 설정 목표 공급(E3-1 D-3) — 활성 스냅샷의 BASIC_SETTING 동결 템플릿 중 <b>감지 보드와 일치하는 것만</b>
 * 선언 순서로 병합한다(같은 속성은 후행 우선). {@code FrozenBiosTemplate.boardModelId} 가 이 용도로 예약된
 * 값이다. 다른 보드의 속성 키를 PATCH 하면 BMC 가 거절하거나 엉뚱한 속성을 건드린다 — AMI 키는 보드마다 다르다.
 */
@Component
@RequiredArgsConstructor
public class BiosSettingResolutionProviderImpl implements BiosSettingResolutionProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;
    private final GuestServerDetailRepository guestServerDetailRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BiosSettingTarget> resolveFor(UUID guestServerId) {
        Optional<FrozenBiosSettings> frozen = assignmentRepository
                .findByGuestServer_IdAndSupersededAtIsNull(guestServerId)
                .flatMap(snapshot -> snapshot.getProcesses().stream()
                        .map(AssignedProcessSnapshot::getFrozenBiosSettings)
                        .filter(Objects::nonNull)
                        .findFirst());
        if (frozen.isEmpty()) {
            return Optional.empty();   // 활성 할당이 없거나 정의서에 BIOS 설정이 없다 — 창 밖
        }
        Long boardModelId = guestServerDetailRepository.findByServerIdWithBoardModel(guestServerId)
                .map(detail -> detail.getBoardModel().getId())
                .orElse(null);
        Map<String, Object> merged = new LinkedHashMap<>();
        if (boardModelId != null) {
            frozen.get().templates().stream()
                    .filter(template -> boardModelId.equals(template.boardModelId()))
                    .forEach(template -> template.values().entries()
                            .forEach((name, value) -> merged.put(name.value(), value.jsonValue())));
        }
        return Optional.of(new BiosSettingTarget(merged));
    }
}
