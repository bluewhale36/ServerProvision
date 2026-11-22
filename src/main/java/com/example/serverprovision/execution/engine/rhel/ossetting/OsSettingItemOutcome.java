package com.example.serverprovision.execution.engine.rhel.ossetting;

/**
 * OS 후처리 판정의 한 항목. 원장 meta 에 그대로 적고 서버 상세가 읽는다.
 *
 * @param label    항목 이름(서비스 · 패키지 이름, 스크립트 파일 이름)
 * @param expected 정의서 값. 스크립트는 비어 있다
 * @param observed 관측 값
 * @param failure  실패 사유 문장. 성공이면 null
 */
public record OsSettingItemOutcome(Kind kind, String label, String expected, String observed, boolean ok, String failure) {

    /** 화면이 '기본 설정' 표와 '스크립트' 표로 나눠 그린다. */
    public enum Kind { SELINUX, SERVICE, PACKAGE, SCRIPT }

    static OsSettingItemOutcome passed(Kind kind, String label, String expected, String observed) {
        return new OsSettingItemOutcome(kind, label, expected, observed, true, null);
    }

    static OsSettingItemOutcome failed(Kind kind, String label, String expected, String observed, String failure) {
        return new OsSettingItemOutcome(kind, label, expected, observed, false, failure);
    }
}
