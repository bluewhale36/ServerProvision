package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.setting.BiosRegistryCapturePort;
import com.example.serverprovision.execution.engine.setting.RegistryCheck;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.global.redfish.RedfishBiosService;
import com.example.serverprovision.global.redfish.RedfishRegistry;
import com.example.serverprovision.global.redfish.RedfishTarget;
import com.example.serverprovision.global.redfish.RedfishUpdateService;
import com.example.serverprovision.provisioning.biossetting.entity.BiosRegistrySnapshot;
import com.example.serverprovision.provisioning.biossetting.repository.BiosRegistrySnapshotRepository;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.biossetting.vo.BiosStaleValue;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.parser.BiosRegistryParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 채집 · 대조 SPI 구현(E3-3) — BMC 가 보고한 실제 BIOS 버전으로 (보드, 버전) 스냅샷을 적립하고, 그 허용값으로
 * 목표를 대조한다. 판정 규칙은 템플릿과 같은 {@link BiosSettingValues#staleAgainst} 하나다.
 *
 * <p>어떤 실패도 밖으로 내지 않는다(Q2) — 버전 판독 · 체인 · 파싱 · 저장 어느 단계가 막혀도 {@link RegistryCheck#unavailable()}
 * 로 답하고 집행은 종전 경로(PATCH → BMC 판정)로 간다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiosRegistryCapturePortImpl implements BiosRegistryCapturePort {

    private final GuestServerDetailRepository guestServerDetailRepository;
    private final BiosRegistrySnapshotRepository snapshotRepository;
    private final RedfishUpdateService updateService;
    private final RedfishBiosService biosService;
    private final BiosRegistryParser registryParser;

    @Override
    @Transactional
    public RegistryCheck captureAndCheck(UUID guestServerId, RedfishTarget target, Map<String, Object> attributes) {
        try {
            Captured captured = capture(guestServerId, target);
            if (captured == null) {
                return RegistryCheck.unavailable();
            }
            BiosRegistryParser.ParsedRegistry registry = registryParser.parse(
                    new ByteArrayInputStream(captured.snapshot().getRegistryJson().getBytes(StandardCharsets.UTF_8)));
            List<String> violations = toValues(attributes).staleAgainst(registry.attributes()).stream()
                    .map(BiosStaleValue::message)
                    .toList();
            return new RegistryCheck(true, captured.snapshot().getBiosVersion(), captured.fresh(), violations);
        } catch (RuntimeException e) {
            log.warn("[setting] {} — 레지스트리 채집 · 대조 불가, BMC 판정에 맡김 : {}", guestServerId, e.getMessage());
            return RegistryCheck.unavailable();
        }
    }

    @Override
    @Transactional
    public void captureIfAbsent(UUID guestServerId, RedfishTarget target) {
        try {
            capture(guestServerId, target);
        } catch (RuntimeException e) {
            log.warn("[flash] {} — 레지스트리 채집 불가 : {}", guestServerId, e.getMessage());
        }
    }

    private record Captured(BiosRegistrySnapshot snapshot, boolean fresh) {
    }

    private Captured capture(UUID guestServerId, RedfishTarget target) {
        GuestServerDetail detail = guestServerDetailRepository.findByServerIdWithBoardModel(guestServerId).orElse(null);
        if (detail == null || detail.getBoardModel() == null) {
            return null;
        }
        String version = updateService.firmwareInventory(target, FirmwareAxis.BIOS.getInventoryMember())
                .path("Version").asString("").trim();
        if (version.isBlank()) {
            return null;
        }
        Long boardId = detail.getBoardModel().getId();
        Optional<BiosRegistrySnapshot> existing = snapshotRepository.findByBoardModel_IdAndBiosVersion(boardId, version);
        if (existing.isPresent()) {
            return new Captured(existing.get(), false);
        }
        RedfishRegistry registry = biosService.registry(target);
        int count = registryParser.parse(new ByteArrayInputStream(registry.rawJson().getBytes(StandardCharsets.UTF_8)))
                .attributes().size();
        BiosRegistrySnapshot snapshot = BiosRegistrySnapshot.builder()
                .boardModel(detail.getBoardModel())
                .biosVersion(version)
                .capturedAt(LocalDateTime.now())
                .sourceBmcIp(target.bmcIp())
                .guestServerId(guestServerId)
                .attributeCount(count)
                .registryJson(registry.rawJson())
                .build();
        try {
            BiosRegistrySnapshot saved = snapshotRepository.saveAndFlush(snapshot);
            log.info("[setting] {} — BIOS 레지스트리 채집 : board={}, version={}, attributes={}, bmc={}",
                    guestServerId, detail.getBoardModel().getModelName(), version, count, target.bmcIp());
            return new Captured(saved, true);
        } catch (DataIntegrityViolationException raced) {
            // 같은 (보드, 버전)을 다른 게스트가 먼저 적립했다 — 그것을 쓴다.
            return snapshotRepository.findByBoardModel_IdAndBiosVersion(boardId, version)
                    .map(s -> new Captured(s, false))
                    .orElse(null);
        }
    }

    /** 집행 목표(Redfish 바디 모양)를 템플릿 값 모양으로 — 판정 규칙을 하나로 두기 위한 변환. */
    private static BiosSettingValues toValues(Map<String, Object> attributes) {
        Map<BiosAttributeName, BiosAttributeValue> entries = new LinkedHashMap<>();
        attributes.forEach((name, value) -> entries.put(BiosAttributeName.of(name), toValue(value)));
        return new BiosSettingValues(entries);
    }

    private static BiosAttributeValue toValue(Object value) {
        if (value instanceof Boolean b) {
            return BiosAttributeValue.ofBoolean(b);
        }
        if (value instanceof Number n) {
            return BiosAttributeValue.ofLong(n.longValue());
        }
        return BiosAttributeValue.ofString(String.valueOf(value));
    }
}
