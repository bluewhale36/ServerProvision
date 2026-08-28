package com.example.serverprovision.provisioning.biossetting.enums;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 편집기 · 상세가 지금 보고 있는 레지스트리의 출처(E3-3 R3). 배지 문구는 상수가 스스로 만든다 —
 * 화면이 출처마다 분기해 문장을 조립하면 세 화면에 같은 분기가 복제된다.
 */
public enum BiosRegistrySource {

    /** 굽기 목표 버전의 채집본 — 편집 · 검증 · 집행이 같은 것을 본다. */
    SNAPSHOT_TARGET {
        @Override
        public String label(String biosVersion, String targetVersion, LocalDateTime capturedAt, String sourceBmcIp) {
            return biosVersion + " · " + DATE.format(capturedAt) + " 채집 · " + sourceBmcIp;
        }
    },
    /** 목표 버전은 아직 채집되지 않아 보드의 최신 채집본으로 대신한다. */
    SNAPSHOT_LATEST {
        @Override
        public String label(String biosVersion, String targetVersion, LocalDateTime capturedAt, String sourceBmcIp) {
            return "최신 채집 " + biosVersion + " · 목표 " + (targetVersion == null ? "미정" : targetVersion) + " 미채집";
        }
    },
    /** 채집본이 없어 자료 파일 — 어느 BIOS 버전의 것인지 알 수 없다. */
    FILE {
        @Override
        public String label(String biosVersion, String targetVersion, LocalDateTime capturedAt, String sourceBmcIp) {
            return "파일 · 버전 미상";
        }
    };

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public abstract String label(String biosVersion, String targetVersion, LocalDateTime capturedAt, String sourceBmcIp);

    public boolean isSnapshot() {
        return this != FILE;
    }
}
