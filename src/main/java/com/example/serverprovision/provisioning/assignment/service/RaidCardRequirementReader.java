package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.provisioning.assignment.vo.RequiredRaidCard;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAID 카드 대조의 두 재료를 읽는 자리(R20) — 정의서가 지정한 카드와 서버에 저장된 RAID 인벤토리.
 *
 * <p>할당 판정 · 그룹 미리보기 · 집행 쪽 지정 카드 해석({@code RaidConfigurationResolutionProviderImpl}) ·
 * 카드 관측 공급({@code RaidCardObservationProviderImpl})이 같은 변환을 쓴다. 판정은 여기서 하지 않는다 —
 * {@code AssignmentEligibility} 가 집행과 같은 {@code RaidCardMatch.judge} 로 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaidCardRequirementReader {

    private final SettingDefinitionRepository definitionRepository;
    private final RaidCardRepository raidCardRepository;
    private final ObjectMapper objectMapper;

    /** RAID 구성 요청이 지정한 카드. 카드 자원은 soft-delete 여도 Subsystem 이 남아 있어 대조에 쓴다. */
    @Transactional(readOnly = true)
    public RequiredRaidCard requiredOf(RaidConfigurationRequest raid) {
        Long raidCardId = raid.getRaidCardId();
        if (raidCardId == null) {
            return RequiredRaidCard.unspecified();
        }
        return raidCardRepository.findById(raidCardId)
                .map(card -> new RequiredRaidCard(raidCardId, subsystemOf(card), card.getModelName(), true))
                .orElseGet(() -> new RequiredRaidCard(raidCardId, null, null, false));
    }

    /** 단계 요청 목록에서 RAID 구성 단계의 지정 카드 — 그 단계가 없으면 {@code null}. */
    @Transactional(readOnly = true)
    public RequiredRaidCard requiredOf(List<? extends AbstractProcessRequest> requests) {
        return requests.stream()
                .filter(RaidConfigurationRequest.class::isInstance)
                .map(RaidConfigurationRequest.class::cast)
                .findFirst()
                .map(this::requiredOf)
                .orElse(null);
    }

    /**
     * 정의서마다 지정 카드 — RAID 구성 단계가 없는 정의서는 맵에 없다.
     * 모달 · 그룹 미리보기가 정의서 × 서버 조합마다 판정하므로 정의서 쪽 재료는 여기서 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public Map<Long, RequiredRaidCard> requiredByDefinition(Collection<Long> definitionIds) {
        Map<Long, RequiredRaidCard> byDefinition = new HashMap<>();
        for (SettingDefinition definition : definitionRepository.findAllById(definitionIds)) {
            RequiredRaidCard required = requiredOf(definition.getProcesses().stream()
                    .map(SettingProcess::getPayload)
                    .map(payload -> payload.request())
                    .toList());
            if (required != null) {
                byDefinition.put(definition.getId(), required);
            }
        }
        return byDefinition;
    }

    /**
     * 서버에 저장된 RAID 인벤토리 — 진단 시점 RAID 채집(E3.5-5)이 채운다. 수집 전이거나 JSON 이 손상됐으면
     * {@code null}(관용 — 판정은 이를 "대조 전" 으로 다룬다).
     */
    public RaidInventory inventoryOf(GuestServerDetail detail) {
        if (detail == null || detail.getRaidInventoryJson() == null || detail.getRaidInventoryJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(detail.getRaidInventoryJson(), RaidInventory.class);
        } catch (RuntimeException e) {
            log.warn("저장 RAID 인벤토리 해석 불가 — 수집 전으로 다룸 : guestServerId={}", detail.getGuestServer().getId(), e);
            return null;
        }
    }

    private static String subsystemOf(RaidCard card) {
        return card.getPciSubsystemId() == null ? null : card.getPciSubsystemId().toDisplay();
    }
}
