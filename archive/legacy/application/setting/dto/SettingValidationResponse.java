package com.example.serverprovision.application.setting.dto;

import java.util.List;

/**
 * 주문서 사전 검증({@code POST /pxe/v1/setting/api/validate}) 응답 DTO.
 *
 * <p>Bean Validation 및 Resolver 강성 검증을 모두 통과했을 때 200 OK 로 반환된다.
 * {@code warnings} 가 비어 있으면 저장 진행에 아무 문제가 없는 상태이며, 프론트는 바로
 * {@code /api/new} 또는 {@code /api/{id}} 를 호출한다. 비어 있지 않으면 사용자에게 모달로
 * 고지하고 확인 후 본 저장 요청으로 진행한다.</p>
 */
public record SettingValidationResponse(List<ValidationWarning> warnings) {
}
