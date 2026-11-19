package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.osinstall.OsInstallLedger;
import com.example.serverprovision.execution.engine.osinstall.OsInstallOrigin;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RHEL 설치 원장(E4-1-b-4-2). RUNNING 행 하나가 서빙 한 사이클이고, 열 때 ISO · 등록 OS · 서빙 시각 · 대상 디스크를 적는다.
 * RHEL 에만 있는 실패 사유는 {@link RhelInstallFailure} 다. meta 에 kickstart · 해시 · 토큰은 없다.
 */
@Component
public class RhelInstallLedger extends OsInstallLedger {

    public RhelInstallLedger(ProvisioningHistoryRecorder recorder, ProvisioningHistoryRepository historyRepository,
                             ObjectMapper objectMapper) {
        super(recorder, historyRepository, objectMapper);
    }

    @Override
    public OsInstallOrigin origin() {
        return OsInstallOrigin.RHEL_INSTALL;
    }

    /** 첫 서빙 — 스크립트를 내준 사실이 착수다(E4-1-a-3 D-1). */
    public ProvisioningHistory openServed(GuestServer server, RhelServing serving, LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("origin", origin().wire());
        meta.put("iso", serving.media().isoId());
        meta.put("release", serving.media().osLabel());
        meta.put("tree", serving.tree().release().label());
        meta.put("served", now.toString());
        meta.put("reentries", 0);
        meta.put("targetDisk", serving.disk().byIdName());
        return recorder.openRunning(server, STEP, now, write(meta));
    }

    /** 서빙 때 적은 ISO id — 없으면(단발 실패 행) empty. */
    public Optional<Long> isoOf(ProvisioningHistory row) {
        Object v = read(row).get("iso");
        return v instanceof Number n ? Optional.of(n.longValue()) : Optional.empty();
    }

    /** 서빙 때 적은 대상 디스크 by-id 이름 — 없으면 null. */
    public String targetDiskOf(ProvisioningHistory row) {
        Object v = read(row).get("targetDisk");
        return v == null ? null : v.toString();
    }

    /** 서빙 때 적은 등록 OS 화면 이름 — 없으면 null. */
    public String releaseOf(ProvisioningHistory row) {
        Object v = read(row).get("release");
        return v == null ? null : v.toString();
    }
}
