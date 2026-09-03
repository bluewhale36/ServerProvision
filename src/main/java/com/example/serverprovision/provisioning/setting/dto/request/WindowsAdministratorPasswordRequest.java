package com.example.serverprovision.provisioning.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * Windows 내장 Administrator 계정의 비밀번호(정의서 필수 입력 — 사용자 결정 2026-09-02 CP1).
 * {@code RootPasswordRequest} 와 같은 "기존 유지" 관용구를 쓰되, 사전 암호화 개념은 없다 — 응답 파일의
 * Base64 인코딩은 서버가 렌더 시(E4-1-a-3) 수행한다.
 */
@Getter
public class WindowsAdministratorPasswordRequest {

    public static final int MAX_LENGTH = 127;

    /**
     * 평문 비밀번호. 수정 폼 pre-fill 에는 절대 포함되지 않는다({@link #withoutSecret()}).
     * 패턴은 빈 값을 통과시킨다({@code *}) — 빈 값의 거절은 {@link #isProvidedOrKept()} 한 곳이 맡아 문구가 겹치지 않는다(CP5 O-1).
     */
    @Size(max = MAX_LENGTH, message = "Administrator 비밀번호는 127자를 넘을 수 없습니다.")
    @Pattern(regexp = "^[\\x21-\\x7E]*$", message = "비밀번호에는 공백 · 제어문자를 쓸 수 없습니다.")
    private final String password;

    /** 수정 시 기존 비밀번호를 유지할지 — true 면 {@code password} 는 무시되고 서버가 저장본에서 복사한다. */
    private final boolean keepExistingPassword;

    @JsonCreator
    public WindowsAdministratorPasswordRequest(
            @JsonProperty("password") String password,
            // boxed + null-coalesce: Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성 대응 (누락=false).
            @JsonProperty("keepExistingPassword") Boolean keepExistingPassword
    ) {
        this.password = password;
        this.keepExistingPassword = keepExistingPassword != null && keepExistingPassword;
    }

    /** 값이 있거나 기존 유지여야 한다 — 둘 다 아니면 Layer A 400(필드 administratorPassword). */
    @JsonIgnore
    @AssertTrue(message = "Administrator 비밀번호를 입력하거나 기존 비밀번호 유지를 선택해야 합니다.")
    public boolean isProvidedOrKept() {
        return keepExistingPassword || hasPassword();
    }

    @JsonIgnore
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    /** pre-fill 사본 — 값 제거 + 기존 유지 플래그. */
    public WindowsAdministratorPasswordRequest withoutSecret() {
        return new WindowsAdministratorPasswordRequest(null, true);
    }

    /** 저장본의 값을 이어받은 사본(keep 정규화). */
    public WindowsAdministratorPasswordRequest retaining(String existingPassword) {
        return new WindowsAdministratorPasswordRequest(existingPassword, false);
    }
}
