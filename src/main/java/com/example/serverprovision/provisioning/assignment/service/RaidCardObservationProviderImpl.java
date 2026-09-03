package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.management.raidcard.service.RaidCardObservationProvider;
import com.example.serverprovision.management.raidcard.vo.RaidCardObservation;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 카드 관측 공급(E3.5-5-b D1) — 활성 스냅샷(supersededAt null)의 RAID 구성 payload 가 대상 카드를 지정한 게스트를 모으고,
 * 그 게스트의 저장 인벤토리({@code guest_server_detail.raid_inventory_json})에서 감지 카드를 읽는다. 저장하지 않으므로
 * 재할당 · 재진단이 곧 관측의 갱신이다. 인벤토리 없음 · 카드 미감지 · JSON 손상은 관측 없음으로 건너뛴다(관용).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaidCardObservationProviderImpl implements RaidCardObservationProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<RaidCardObservation>> observationsByCard(Set<Long> raidCardIds) {
        if (raidCardIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> cardByGuest = new LinkedHashMap<>();
        Map<UUID, GuestServer> guestById = new HashMap<>();
        for (SettingAssignmentSnapshot snapshot : assignmentRepository.findBySupersededAtIsNull()) {
            snapshot.processRequestOf(RaidConfigurationRequest.class)
                    .map(RaidConfigurationRequest::getRaidCardId)
                    .filter(raidCardIds::contains)
                    .ifPresent(cardId -> {
                        cardByGuest.put(snapshot.getGuestServer().getId(), cardId);
                        guestById.put(snapshot.getGuestServer().getId(), snapshot.getGuestServer());
                    });
        }
        if (cardByGuest.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RaidCardObservation>> byCard = new HashMap<>();
        for (GuestServerDetail detail : guestServerDetailRepository.findAllByServerIdInWithBoardModel(List.copyOf(cardByGuest.keySet()))) {
            UUID guestId = detail.getGuestServer().getId();
            String observed = detectedSubsystemOf(guestId, detail.getRaidInventoryJson());
            if (observed == null) {
                continue;
            }
            byCard.computeIfAbsent(cardByGuest.get(guestId), k -> new ArrayList<>())
                    .add(new RaidCardObservation(guestId, labelOf(guestById.get(guestId)), observed));
        }
        return byCard;
    }

    private String detectedSubsystemOf(UUID guestId, String raidInventoryJson) {
        if (raidInventoryJson == null || raidInventoryJson.isBlank()) {
            return null;
        }
        try {
            RaidInventory inventory = objectMapper.readValue(raidInventoryJson, RaidInventory.class);
            return inventory.card() == null ? null : inventory.card().pciSubsystemId();
        } catch (RuntimeException e) {
            log.warn("저장 RAID 인벤토리 해석 불가 — 관측에서 제외 : guestServerId={}", guestId, e);
            return null;
        }
    }

    /**
     * 화면 라벨 — 운영자 이름 → 시리얼 → systemUUID 끝 세그먼트(U6 가 게스트 식별 표기로 확정한 값) 순.
     * CP5 F-1: id(UUIDv7) 앞자리는 시각부라 같은 시각대에 등록된 서버들이 통째로 겹쳤다 — 관측이 생기는 시점이
     * 등록 직후(이름 미부여)라 그 폴백이 기본 경로였다.
     */
    static String labelOf(GuestServer guest) {
        if (guest.getName() != null && !guest.getName().isBlank()) {
            return guest.getName();
        }
        if (guest.getSerialNumber() != null && !guest.getSerialNumber().isBlank()) {
            return guest.getSerialNumber();
        }
        return guest.systemUUIDSuffix();
    }
}
