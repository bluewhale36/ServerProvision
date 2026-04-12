package com.example.serverprovision.application.setting.dto;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.model.enums.SettingStatus;

/**
 * 세팅 주문서 수정 성공 시 반환되는 Response DTO이다.
 *
 * <p>역할: {@link ServerSetting} 엔티티에서 클라이언트에게 노출할 필드({@code id},
 * {@code name}, {@code status})만 추출하여 HTTP 응답 본문으로 사용한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.controller.SettingController#updateSetting}에서
 * {@link #from(ServerSetting)} 팩토리 메소드로 변환되어 200 OK 응답 본문으로 반환된다.
 * 수정 후에도 {@code status}는 {@link SettingStatus#PENDING}을 유지한다.</p>
 */
public record SettingUpdateResponse(
        /** 수정된 세팅 주문서의 DB 기본키이다. */
        Long id,
        /** 세팅 주문서 명칭이다. */
        String name,
        /** 현재 상태이다. 수정 후에도 {@link SettingStatus#PENDING}을 유지한다. */
        SettingStatus status
) {

    /**
     * {@link ServerSetting} 엔티티에서 Response DTO를 생성하는 팩토리 메소드이다.
     *
     * @param entity 수정된 {@link ServerSetting} 엔티티
     * @return 변환된 {@link SettingUpdateResponse}
     */
    public static SettingUpdateResponse from(ServerSetting entity) {
        return new SettingUpdateResponse(entity.getId(), entity.getName(), entity.getStatus());
    }
}
