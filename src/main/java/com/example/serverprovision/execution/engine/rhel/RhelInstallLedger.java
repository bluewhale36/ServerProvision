package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.osinstall.InstallConfirmation;
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
 * 첫 부팅 완료 보고(E4-1-b-5)는 같은 행을 서빙 meta 를 둔 채 SUCCEEDED 로 닫는다. RHEL 에만 있는 실패 사유는
 * {@link RhelInstallFailure} 다. meta 에 kickstart · 해시 · 토큰은 없다.
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
        return textOf(row, "targetDisk");
    }

    /** 서빙 때 적은 등록 OS 화면 이름 — 없으면 null. */
    public String releaseOf(ProvisioningHistory row) {
        return textOf(row, "release");
    }

    /** 첫 부팅 완료 보고로 열린 서빙 행을 닫는다. 이미 닫힌 행이면 false. */
    public boolean closeSucceeded(ProvisioningHistory row, RhelCompletion c, LocalDateTime now) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("osId", c.osId());
        facts.put("osVersion", c.osVersion());
        facts.put("osPrettyName", c.osPrettyName());
        facts.put("kernel", c.kernel());
        facts.put("hostname", c.hostname());
        facts.put("installedRootDisk", c.installedRootDisk());
        facts.put("diskConfirmed", c.diskConfirmation().metaValue());
        facts.put("reportedSystemUuid", c.reportedSystemUuid());
        facts.put("identityConfirmed", c.identityConfirmation().metaValue());
        return closeCompleted(row, facts, "설치 완료 · 디스크 " + c.diskConfirmation().getDescription()
                + " · 대상 서버 " + c.identityConfirmation().getDescription(), now);
    }

    /** 완료 보고로 닫힌 행의 내용. 완료 행이 아니면 empty. */
    public Optional<RhelCompletion> completionOf(ProvisioningHistory row) {
        if (!isCompletedRow(row)) {
            return Optional.empty();
        }
        return Optional.of(new RhelCompletion(textOf(row, "osId"), textOf(row, "osVersion"), textOf(row, "osPrettyName"),
                textOf(row, "kernel"), textOf(row, "hostname"), textOf(row, "installedRootDisk"),
                confirmationOf(row, "diskConfirmed"), textOf(row, "reportedSystemUuid"),
                confirmationOf(row, "identityConfirmed")));
    }

    /**
     * 첫 부팅 완료 보고가 실은 사실과 사후 확증. 보고하지 못한 값은 null 이다.
     *
     * @param installedRootDisk    설치된 OS 의 루트 디스크 by-id 이름(예 wwn-0x…)
     * @param diskConfirmation     루트 디스크가 서빙 때 적은 대상 디스크인가
     * @param reportedSystemUuid   보고한 기계의 DMI 시스템 UUID
     * @param identityConfirmation 그 UUID 가 이 게스트 서버의 것인가
     */
    public record RhelCompletion(String osId, String osVersion, String osPrettyName, String kernel, String hostname,
                                 String installedRootDisk, InstallConfirmation diskConfirmation,
                                 String reportedSystemUuid, InstallConfirmation identityConfirmation) {

        /** 스크립트는 읽지 못한 값을 빈 문자열로 보낸다. 원장과 화면이 '미보고' 하나로 다루도록 null 로 맞춘다. */
        public RhelCompletion {
            osId = reportedOrNull(osId);
            osVersion = reportedOrNull(osVersion);
            osPrettyName = reportedOrNull(osPrettyName);
            hostname = reportedOrNull(hostname);
            installedRootDisk = reportedOrNull(installedRootDisk);
            reportedSystemUuid = reportedOrNull(reportedSystemUuid);
        }

        private static String reportedOrNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }
}
