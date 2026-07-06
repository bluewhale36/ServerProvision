package com.example.serverprovision.provisioning.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * root 계정 비밀번호 설정 (리눅스 스코프 — root 는 리눅스 계정 모델의 표현).
 */
@Getter
public class RootPasswordRequest {

    /** 평문 또는 사전 암호화된 비밀번호. 수정 폼 pre-fill 에는 절대 포함되지 않는다. */
    // Kickstart 스크립트 주입 방지 — ASCII printable 비공백만 (legacy INVALID_ROOT_PASSWORD 이관).
    @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "root 비밀번호에는 공백·제어문자를 쓸 수 없습니다.")
    private final String password;

    /** {@code password} 가 이미 암호화된 값인지 여부. */
    private final boolean isPasswordEncrypted;

    /** 수정 시 기존 비밀번호를 유지할지 여부 — true 면 {@code password} 는 무시된다. */
    private final boolean keepExistingPassword;

    @JsonCreator
    public RootPasswordRequest(
            @JsonProperty("password")             String password,
            // boxed + null-coalesce: Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성 대응 (누락=false).
            @JsonProperty("isPasswordEncrypted")  Boolean isPasswordEncrypted,
            @JsonProperty("keepExistingPassword") Boolean keepExistingPassword
    ) {
        this.password             = password;
        this.isPasswordEncrypted  = isPasswordEncrypted != null && isPasswordEncrypted;
        this.keepExistingPassword = keepExistingPassword != null && keepExistingPassword;
    }

    // 직렬화 키를 wire 계약("isPasswordEncrypted")에 고정 — Jackson 기본 명명은 is-접두를 벗겨버린다.
    @JsonProperty("isPasswordEncrypted")
    public boolean isPasswordEncrypted() {
        return isPasswordEncrypted;
    }
}
