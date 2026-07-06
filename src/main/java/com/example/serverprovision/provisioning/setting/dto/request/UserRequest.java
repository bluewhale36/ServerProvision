package com.example.serverprovision.provisioning.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * 생성할 사용자 계정 (리눅스 스코프 — sudoer 는 리눅스 계정 모델의 표현).
 */
@Getter
public class UserRequest {

    @NotBlank(message = "사용자 이름은 필수 입력값입니다.")
    @Pattern(regexp = "^[a-z_][a-z0-9_-]{0,31}$",
            message = "사용자 이름은 소문자/숫자/밑줄/하이픈(소문자·밑줄 시작, 최대 32자)만 가능합니다.")
    private final String username;

    /** 평문 또는 사전 암호화된 비밀번호. 수정 폼 pre-fill 에는 절대 포함되지 않는다. */
    @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "비밀번호에는 공백·제어문자를 쓸 수 없습니다.")
    private final String password;

    /** sudo 권한 부여 여부. {@code Boolean} 으로 미전송(null)과 명시적 false 를 구별한다. */
    private final Boolean isSudoer;

    /** {@code password} 가 이미 암호화된 값인지 여부. */
    private final boolean isPasswordEncrypted;

    /** 수정 시 기존 비밀번호를 유지할지 여부 — true 면 {@code password} 는 무시된다. */
    private final boolean keepExistingPassword;

    @JsonCreator
    public UserRequest(
            @JsonProperty("username")             String username,
            @JsonProperty("password")             String password,
            @JsonProperty("isSudoer")             Boolean isSudoer,
            // boxed + null-coalesce: Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성 대응 (누락=false).
            @JsonProperty("isPasswordEncrypted")  Boolean isPasswordEncrypted,
            @JsonProperty("keepExistingPassword") Boolean keepExistingPassword
    ) {
        this.username             = username;
        this.password             = password;
        this.isSudoer             = isSudoer;
        this.isPasswordEncrypted  = isPasswordEncrypted != null && isPasswordEncrypted;
        this.keepExistingPassword = keepExistingPassword != null && keepExistingPassword;
    }

    // 직렬화 키를 wire 계약("isPasswordEncrypted")에 고정 — Jackson 기본 명명은 is-접두를 벗겨버린다.
    @JsonProperty("isPasswordEncrypted")
    public boolean isPasswordEncrypted() {
        return isPasswordEncrypted;
    }
}
