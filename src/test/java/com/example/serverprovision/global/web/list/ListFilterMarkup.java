package com.example.serverprovision.global.web.list;

import java.util.List;
import java.util.regex.Pattern;

/**
 * S8-4 — 렌더된 목록 화면에서 조회 조건 창(fragments/ui/list-filter)의 안과 밖을 가르는 테스트 도우미.
 * 창은 {@code id="listFilterPanel"} 부터 하단 버튼 줄({@code n-filter-panel-actions}) 앞까지다.
 */
public final class ListFilterMarkup {

    private static final String PANEL_START = "id=\"listFilterPanel\"";
    private static final String PANEL_END = "n-filter-panel-actions";
    private static final Pattern ROW_LABEL = Pattern.compile("<span class=\"n-filter-row-label\">([^<]+)</span>");

    private ListFilterMarkup() {
    }

    public static String panel(String html) {
        int start = html.indexOf(PANEL_START);
        int end = html.indexOf(PANEL_END, start);
        if (start < 0 || end < 0) {
            throw new AssertionError("조회 조건 창이 그려지지 않았습니다");
        }
        return html.substring(start, end);
    }

    public static String outsidePanel(String html) {
        return html.replace(panel(html), "");
    }

    public static List<String> rowLabels(String html) {
        return ROW_LABEL.matcher(panel(html)).results().map(m -> m.group(1).trim()).toList();
    }
}
