package com.example.serverprovision.application.setting.model.request;

/**
 * OS 후처리 설정 단계에 대한 프론트엔드 요청 DTO이다.
 *
 * <p>역할: {@code "type": "OS_SETTING"}으로 Jackson 다형성 역직렬화에 사용된다.
 * 현재 {@link com.example.serverprovision.application.setting.model.OSSetting}이
 * 필드 없는 스텁이므로, 이 Request도 필드 없는 스텁 상태이다.</p>
 *
 * <p>유스케이스: {@code POST /pxe/v1/setting/api/new} 요청의 {@code processList} 항목 중
 * {@code "type": "OS_SETTING"}에 해당하는 항목으로 역직렬화된다.
 * {@link com.example.serverprovision.application.setting.service.resolver.OSSettingResolver}가
 * 이 Request를 받아 빈 {@link com.example.serverprovision.application.setting.model.OSSetting}
 * 인스턴스를 반환한다.</p>
 *
 * <p>확장 가이드: {@link com.example.serverprovision.application.setting.model.OSSetting}에
 * OS 후처리 설정 필드(예: SELinux 모드, 활성화할 서비스 목록)가 추가될 때 이 클래스에도
 * 동일한 필드와 {@code @JsonCreator} 생성자를 추가한다.</p>
 */
public class OSSettingRequest extends AbstractProcessRequest {
}
