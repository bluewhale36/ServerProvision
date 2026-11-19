package com.example.serverprovision.management.subprogram.dto.response;

import com.example.serverprovision.management.subprogram.enums.SubprogramKind;

/**
 * 목록 Miller 의 선택 복원 위치(HF30-3 D11). 드라이버와 유틸리티가 한 화면의 두 Miller 라 selectId 만으로는 어느 쪽인지 모른다.
 *
 * @param kind     어느 Miller(드라이버 또는 유틸리티)
 * @param scopeKey Miller 첫 열의 키('common' 또는 메인보드 id)
 */
public record SubprogramSelectionResponse(SubprogramKind kind, String scopeKey) {
}
