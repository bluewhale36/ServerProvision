package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * OS 설치 시 적용할 Timezone 설정 정보를 담는 Request DTO이다.
 *
 * <p>역할: {@link OSInstallationRequest#getTimezone()}의 타입이다.
 * Kickstart {@code timezone} 명령의 파라미터에 대응한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}가
 * 이 DTO를 {@link com.example.serverprovision.domain.os.model.installation.Timezone} 도메인
 * 값 객체로 변환한다. {@code timezone}은 Kickstart가 인식하는 Olson 시간대 문자열
 * (예: {@code "Asia/Seoul"})이어야 한다. {@code isUTC=true}이면 Kickstart {@code --utc} 옵션이 적용된다.</p>
 *
 * <p>확장 가이드: NTP 서버 설정 등 추가 Timezone 관련 속성이 필요하면 이 클래스에 필드를 추가하고
 * 도메인 값 객체 {@link com.example.serverprovision.domain.os.model.installation.Timezone}에도
 * 반영한다. Kickstart 스크립트 생성 코드도 함께 수정해야 한다.</p>
 */
@Getter
public class TimezoneRequest {

    /**
     * Olson 형식의 시간대 문자열이다. Kickstart {@code timezone} 명령의 첫 번째 인자에 해당한다.
     * 예: {@code "Asia/Seoul"}, {@code "UTC"}
     */
    @NotBlank(message = "시간대는 필수 입력값입니다.")
    private final String timezone;

    /**
     * 시스템 시계를 UTC로 설정할지 여부이다. Kickstart {@code timezone --utc} 옵션에 해당한다.
     */
    private final boolean isUTC;

    @JsonCreator
    public TimezoneRequest(
            @JsonProperty("timezone") String timezone,
            @JsonProperty("isUTC")    boolean isUTC) {
        this.timezone = timezone;
        this.isUTC    = isUTC;
    }
}
