package com.example.serverprovision.execution.dto.response;

import java.util.List;

/**
 * 서버 목록 보드 셀렉트의 제조사 묶음(S8-2 CP6 임시 조치) — {@code <optgroup>} 1 개에 대응한다.
 * 정의서 작성 폼의 {@code SettingBoardOptionGroupResponse} 와 같은 모양이다. 보드 · 그룹처럼 자기 조회 화면이 있는
 * 대상의 필터 표현은 S8-4(상세 조회 창)에서 다시 설계한다.
 */
public record BoardOptionGroupResponse(String vendor, List<BoardOptionResponse> boards) {
}
