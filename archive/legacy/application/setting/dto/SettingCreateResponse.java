package com.example.serverprovision.application.setting.dto;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.model.enums.SettingStatus;

/**
 * 세팅 주문서 생성 성공 시 반환되는 Response DTO이다.
 *
 * <p>역할: {@link ServerSetting} 엔티티에서 클라이언트에게 노출할 필드({@code id},
 * {@code name}, {@code status})만 추출하여 HTTP 응답 본문으로 사용한다.
 * 서비스 계층이 엔티티를 그대로 반환하더라도 HTTP 경계에서 DTO로 매핑해
 * 프레젠테이션 책임을 컨트롤러에 한정한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.controller.SettingController#createSetting}에서
 * {@link #from(ServerSetting)} 팩토리 메소드로 변환되어 201 Created 응답 본문으로 반환된다.
 * {@code status}는 생성 직후 항상 {@link com.example.serverprovision.application.setting.model.enums.SettingStatus#PENDING}이다.</p>
 *
 * <p>확장 가이드: 클라이언트에게 추가 정보(예: 프로세스 단계 수, 생성 일시)를 전달해야 하면
 * 이 레코드에 필드를 추가하고 {@link #from(ServerSetting)} 메소드에도 반영한다.
 * {@link ServerSetting}에 없는 정보가 필요하면 서비스 계층에서 별도 조회하여 전달한다.</p>
 */
public record SettingCreateResponse(
        /** 생성된 세팅 주문서의 DB 기본키이다. Location 헤더 URI 생성에 사용된다. */
        Long id,
        /** 세팅 주문서 명칭이다. */
        String name,
        /** 현재 상태이다. 생성 직후 항상 {@link SettingStatus#PENDING}이다. */
        SettingStatus status
) {

    /**
     * {@link ServerSetting} 엔티티에서 Response DTO를 생성하는 팩토리 메소드이다.
     *
     * @param entity 저장된 {@link ServerSetting} 엔티티 (id가 채워진 상태)
     * @return 변환된 {@link SettingCreateResponse}
     */
    public static SettingCreateResponse from(ServerSetting entity) {
        return new SettingCreateResponse(entity.getId(), entity.getName(), entity.getStatus());
    }
}
