package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.FirmwareResolution;
import com.example.serverprovision.execution.engine.FirmwareResolutionProvider;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcessSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 펌웨어 해석 공급자 구현(E2-1-b) — 엔진이 선언한 확장점을 할당 도메인이 채운다. 방향이 이렇게
 * 뒤집혀 있는 이유는 {@code execution → provisioning} 참조를 만들지 않기 위해서이며,
 * {@code OwnedPhasesProviderImpl} 이 같은 이유로 먼저 쓴 방식이다.
 *
 * <p>입력 둘을 잇는 것이 이 클래스의 일이다 — ① 활성 할당 스냅샷에 동결된 펌웨어 갱신 단계 payload
 * ② 게스트가 실제로 꽂고 있는 보드(등록 시점에 확정된 하드 FK). 판정 자체는 {@link FirmwareResolver}
 * 가 하고, 여기서는 재료를 모아 넘기고 "해당 없음"(할당 없음 · 그 단계 없음)을 empty 로 구분한다.</p>
 */
@Component
@RequiredArgsConstructor
public class FirmwareResolutionProviderImpl implements FirmwareResolutionProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final FirmwareResolver firmwareResolver;

    @Override
    @Transactional(readOnly = true)
    public Optional<FirmwareResolution> resolveFor(UUID guestServerId) {
        Optional<BasicUpdateRequest> firmware = assignmentRepository
                .findByGuestServer_IdAndSupersededAtIsNull(guestServerId)
                .flatMap(FirmwareResolutionProviderImpl::firmwareStepOf);
        if (firmware.isEmpty()) {
            return Optional.empty();   // 할당이 없거나 정의서에 펌웨어 갱신 단계가 없다 — 판정 대상 아님
        }
        // 보드 모델은 상세 1:1 행이 든다. 상세가 없는 것은 등록 트랜잭션의 1:1 불변 위반이라
        // 판정을 지어내지 않고 "해당 없음" 으로 물러선다(그 데이터 손상은 다른 경로가 드러낸다).
        return guestServerDetailRepository.findByServerIdWithBoardModel(guestServerId)
                .map(detail -> firmwareResolver.resolve(firmware.get(), detail.getBoardModel().getId()));
    }

    private static Optional<BasicUpdateRequest> firmwareStepOf(
            com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot snapshot) {
        return snapshot.getProcesses().stream()
                .map(AssignedProcessSnapshot::getPayload)
                .map(payload -> payload.request())
                .filter(BasicUpdateRequest.class::isInstance)
                .map(BasicUpdateRequest.class::cast)
                .findFirst();
    }
}
