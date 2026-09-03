package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceCondition;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OS 설치 phase 의 준비도 진리표(E4-1-a-3 D-4) — 의존 0 인 정적 판정. 게이트(대기 사다리) · 대기 스크립트(wire) ·
 * 상세 카드(notes)가 같은 함수를 부르므로 사유 문구가 세 곳으로 갈라지지 않는다. 저장하지 않고 매 폴링 재계산한다.
 * DEGRADED 는 쓰지 않는다 — 절반만 갖춘 설치란 없다.
 */
public final class WindowsInstallReadiness {

    private static final String DASHBOARD = "대시보드 Windows 설치 소스 영역";

    private WindowsInstallReadiness() {
    }

    public static PhaseReadiness judge(Optional<WindowsInstallTarget> target, InstallSourceSnapshot snapshot,
                                       WindowsInstallProperties props, WindowsInstallAssets assets) {
        if (target.isEmpty()) {
            return PhaseReadiness.ready();                                  // 창 밖 — 판정 대상 아님
        }
        WindowsInstallTarget t = target.get();
        List<String> notes = new ArrayList<>();
        List<String> wire = new ArrayList<>();

        if (!t.windows()) {
            add(notes, wire, t.unsupportedFamily() + " 설치는 지원하지 않습니다 — Windows 정의서로 교체하세요",
                    "linux install not supported");
        } else {
            if (!t.hasImage()) {
                add(notes, wire, "설치 이미지 미지정 — 정의서를 수정하세요", "image not chosen");
            }
            if (!t.hasPassword()) {
                add(notes, wire, "Administrator 비밀번호 미지정 — 정의서를 수정하세요", "administrator password missing");
            }
        }

        Optional<WindowsImage> image = Optional.empty();
        if (!props.configured()) {
            add(notes, wire, "설치 소스 미설정 — " + DASHBOARD, "install source not configured");
        } else {
            if (snapshot.condition() == InstallSourceCondition.UNREADABLE) {
                add(notes, wire, "install.wim 읽기 실패 — " + DASHBOARD, "install.wim unreadable");
            } else if (!snapshot.ready()) {
                add(notes, wire, "install.wim 없음 — " + DASHBOARD, "install.wim missing");
            } else if (t.hasImage()) {
                image = snapshot.find(t.imageName());
                if (image.isEmpty()) {
                    add(notes, wire, "선택한 이미지가 소스 install.wim 에 없습니다 — " + DASHBOARD, "image not in source");
                }
            }
            if (!assets.bootWimPresent()) {
                add(notes, wire, "boot.wim 없음 — " + DASHBOARD, "boot.wim missing");
            }
            if (!assets.setupExePresent()) {
                add(notes, wire, "setup.exe 없음 — " + DASHBOARD, "setup.exe missing");
            }
            if (!assets.wimbootPresent()) {
                add(notes, wire, "wimboot 없음 — 소스 루트에 배치하세요", "wimboot missing");
            }
        }

        if (!props.shareConfigured()) {
            add(notes, wire, "공유 접속 정보 미설정 — 환경변수", "share credentials missing");
        } else if (!InstallBatRenderer.isBatchSafe(props.sharePassword())) {
            add(notes, wire, "공유 비밀번호에 배치 금지 문자가 있습니다 — 환경변수", "share password has batch-unsafe chars");
        }

        if (image.isPresent() && props.productKeysOrEmpty().forEdition(image.get().editionId()).isEmpty()) {
            add(notes, wire, "제품 키 " + image.get().editionId() + " 미설정 — 환경변수",
                    "product key missing for " + image.get().editionId());
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
