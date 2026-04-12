package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * OS 설치 시 root 계정 비밀번호를 담는 Request DTO이다.
 *
 * <p>역할: {@link OSInstallationRequest#getRootPassword()}의 타입이다.
 * Kickstart {@code rootpw} 명령에 대응한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}가
 * 이 DTO를 {@link com.example.serverprovision.domain.os.model.installation.RootPassword} 도메인
 * 값 객체로 변환한다. {@link OSInstallationRequest#getRootPassword()}가 {@code null}이면
 * root 계정이 잠긴 상태({@code rootpw --lock})로 설치되며, 이 경우 {@code users}에 최소
 * 1명 이상의 일반 사용자가 있어야 도메인 검증({@link com.example.serverprovision.domain.os.model.installation.LinuxInstallation})을 통과한다.
 * 비밀번호의 문자 제약(ASCII printable 비공백 문자만 허용)은 도메인 계층에서 검증된다.</p>
 *
 * <p>수정 모드: {@code keepExistingPassword}가 {@code true}이면 비밀번호 필드를 비워 제출해도
 * {@link com.example.serverprovision.application.setting.service.SettingService#update}가
 * 기존 저장 비밀번호를 주입하므로 유효성 검사에서 배제된다.</p>
 *
 * <p>확장 가이드: root 계정 관련 추가 설정(예: 만료일 설정)이 필요하면 이 클래스에 필드를 추가하고
 * {@code @JsonCreator} 생성자에 파라미터를 추가한다. 도메인 값 객체
 * {@link com.example.serverprovision.domain.os.model.installation.RootPassword}에도 동일하게
 * 반영해야 하며, Kickstart 스크립트 생성 코드도 함께 수정해야 한다.</p>
 */
@Getter
public class RootPasswordRequest {

    /**
     * root 계정 비밀번호 문자열이다. Kickstart {@code rootpw} 명령의 값으로 사용된다.
     * ASCII printable 비공백 문자만 허용되며, 이는 도메인 계층에서 검증된다.
     * {@code keepExistingPassword}가 {@code true}이면 {@code null}을 허용한다.
     */
    private final String password;

    /**
     * {@code password} 값이 이미 암호화된 해시인지 여부이다.
     * {@code true}이면 Kickstart {@code rootpw --iscrypted} 옵션이 적용된다.
     */
    private final boolean isPasswordEncrypted;

    /**
     * 기존 저장 비밀번호를 유지할지 여부이다.
     * 수정 폼에서 비밀번호 필드를 비워 제출할 때 {@code true}로 설정된다.
     * 서비스 레이어가 이 플래그를 보고 기존 비밀번호를 주입한 뒤 Resolver에 전달한다.
     */
    private final boolean keepExistingPassword;

    @JsonCreator
    public RootPasswordRequest(
            @JsonProperty("password")            String password,
            @JsonProperty("isPasswordEncrypted") boolean isPasswordEncrypted,
            @JsonProperty("keepExistingPassword") boolean keepExistingPassword) {
        this.password            = password;
        this.isPasswordEncrypted = isPasswordEncrypted;
        this.keepExistingPassword = keepExistingPassword;
    }
}
