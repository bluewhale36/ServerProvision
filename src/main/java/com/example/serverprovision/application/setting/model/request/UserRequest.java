package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * OS 설치 시 생성할 일반 사용자 계정 정보를 담는 Request DTO이다.
 *
 * <p>역할: {@link OSInstallationRequest#getUsers()}의 각 항목 타입이다.
 * Kickstart {@code user} 명령의 파라미터에 대응하며, 사용자명·비밀번호·sudo 권한·
 * 암호화 여부를 포함한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}가
 * 이 DTO를 {@link com.example.serverprovision.domain.os.model.installation.User} 도메인
 * 값 객체로 변환한다. 사용자명은 POSIX 패턴(소문자·숫자·밑줄·하이픈, 소문자 또는 밑줄로 시작,
 * 최대 32자), 비밀번호는 ASCII printable 비공백 문자만 허용하며, 이는 도메인 계층에서 검증된다.
 * {@code rootPassword}가 {@code null}이고 이 목록도 비어 있으면
 * 도메인 검증에서 {@code NO_ACCESSIBLE_USER} 예외가 발생한다.</p>
 *
 * <p>확장 가이드: 사용자 계정에 SSH 공개키 등 추가 속성이 필요하면 이 클래스에 필드를 추가하고
 * {@code @JsonCreator} 생성자에 파라미터를 추가한다. 도메인 값 객체
 * {@link com.example.serverprovision.domain.os.model.installation.User}에도 동일하게
 * 반영해야 하며, Resolver의 변환 코드와 Kickstart 스크립트 생성 로직도 함께 수정해야 한다.</p>
 */
@Getter
public class UserRequest {

    /** 생성할 사용자 계정명이다. Kickstart {@code user --name} 파라미터에 해당한다. */
    @NotBlank(message = "사용자 이름은 필수 입력값입니다.")
    private final String username;

    /** 사용자 계정 비밀번호이다. Kickstart {@code user --password} 파라미터에 해당한다. */
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private final String password;

    /**
     * sudo 권한 부여 여부이다. {@code true}이면 Kickstart {@code %post} 섹션에서
     * {@code wheel} 그룹 추가 등 sudo 설정을 적용한다. {@code null}이면 미설정으로 처리된다.
     */
    private final Boolean isSudoer;

    /**
     * {@code password} 값이 이미 암호화된 해시인지 여부이다.
     * {@code true}이면 Kickstart {@code user --iscrypted} 옵션이 적용된다.
     */
    private final boolean isPasswordEncrypted;

    @JsonCreator
    public UserRequest(
            @JsonProperty("username")            String username,
            @JsonProperty("password")            String password,
            @JsonProperty("isSudoer")            Boolean isSudoer,
            @JsonProperty("isPasswordEncrypted") boolean isPasswordEncrypted) {
        this.username            = username;
        this.password            = password;
        this.isSudoer            = isSudoer;
        this.isPasswordEncrypted = isPasswordEncrypted;
    }
}
