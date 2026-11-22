package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartRenderer;
import com.example.serverprovision.execution.engine.rhel.ossetting.OsSettingItemOutcome;
import com.example.serverprovision.execution.engine.rhel.ossetting.OsSettingVerdict;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 서버 상세의 OS 설정 패널(E4-1-b-7). 기본 설정 표와 스크립트 표로 나눠 그린다.
 *
 * @param message 성공이면 직접 확인 안내(스크립트가 없으면 null), 실패면 실패 사유
 */
public record OsSettingOutcomeResponse(boolean passed, List<Row> basics, List<Row> scripts, String message) {

    /**
     * @param expected 스크립트 행은 null
     * @param log      스크립트 행의 실행 기록 경로(설치된 서버 안). 기본 설정 행은 null
     */
    public record Row(String label, String expected, String observed, boolean ok, String log) {
    }

    public static OsSettingOutcomeResponse of(OsSettingVerdict verdict) {
        return new OsSettingOutcomeResponse(verdict.passed(),
                rows(verdict, false), rows(verdict, true), verdict.message());
    }

    private static List<Row> rows(OsSettingVerdict verdict, boolean scripts) {
        List<OsSettingItemOutcome> items = verdict.items().stream()
                .filter(item -> (item.kind() == OsSettingItemOutcome.Kind.SCRIPT) == scripts)
                .toList();
        return IntStream.range(0, items.size())
                .mapToObj(i -> new Row(items.get(i).label(), items.get(i).expected(), items.get(i).observed(),
                        items.get(i).ok(), scripts ? KickstartRenderer.USER_SCRIPT_LOG_PREFIX + (i + 1) + ".log" : null))
                .toList();
    }
}
