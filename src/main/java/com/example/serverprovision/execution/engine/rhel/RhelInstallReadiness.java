package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RHEL 설치 준비도 판정(E4-1-b-4-2 순서표 1 ~ 4 행). 부수효과가 없는 정적 판정이라 화면과 게이트가 몇 번을 불러도 같다.
 * 해당하는 사유를 모두 모아 보이고(운영자가 한 번에 고치게), wire 는 "; " 로 잇는다. 디스크가 RAID 구성 뒤에 정해지는 경우는
 * 막지 않는다 — 서빙 직전에 설치기가 다시 본다.
 */
public final class RhelInstallReadiness {

    private RhelInstallReadiness() {
    }

    /**
     * @param treeProblem 설치 트리를 확보하기 전에 정해진 실패 사유(정의서가 막혔으면 보지 않으므로 empty)
     * @param disk        대상 디스크 판정(정의서가 막혀도 계산한다 — 디스크 사유도 함께 보이게)
     */
    public static PhaseReadiness judge(RhelResolution resolution, boolean baseUrlConfigured,
                                       Optional<TreeFailureReason> treeProblem, RhelDiskSelection.Selection disk) {
        List<String> notes = new ArrayList<>();
        List<String> wire = new ArrayList<>();
        if (resolution instanceof RhelResolution.Blocked blocked) {
            add(notes, wire, blocked.message(), "rhel definition blocked");
        }
        if (!baseUrlConfigured) {
            add(notes, wire, "PXE 서버 주소가 설정되지 않았습니다. 관리자에게 확인해야 합니다.", "pxe base url missing");
        }
        treeProblem.ifPresent(reason -> add(notes, wire, reason.message(), "install tree " + reason.name().toLowerCase()));
        if (disk instanceof RhelDiskSelection.Selection.Blocked blocked) {
            add(notes, wire, blocked.note(), blocked.wire());
        }
        if (notes.isEmpty()) {
            return PhaseReadiness.ready();
        }
        return PhaseReadiness.of(ReadinessGrade.BLOCKED, notes, String.join("; ", wire));
    }

    private static void add(List<String> notes, List<String> wire, String note, String code) {
        notes.add(note);
        wire.add(code);
    }
}
