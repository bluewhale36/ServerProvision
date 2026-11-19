package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.vo.LinuxPassword;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 생성할 사용자 계정 (리눅스 스코프 — sudoer 는 리눅스 계정 모델의 표현).
 * 비밀번호의 평문 · 해시 규칙은 {@link RootPasswordRequest} 와 같다(E4-1-b-2 D-18).
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserRequest {

    @NotBlank(message = "사용자 이름은 필수 입력값입니다.")
    @Pattern(regexp = "^[a-z_][a-z0-9_-]{0,31}$",
            message = "사용자 이름은 소문자, 숫자, 밑줄, 하이픈만 쓸 수 있으며 소문자나 밑줄로 시작해야 합니다(최대 32자).")
    private final String username;

    /** 입력 평문. 수정 폼 pre-fill 에는 절대 포함되지 않는다. */
    @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "비밀번호에는 공백이나 제어문자를 쓸 수 없습니다.")
    private final String password;

    /** 저장된 SHA-512 crypt 해시. */
    private final String passwordHash;

    /** sudo 권한 부여 여부. {@code Boolean} 으로 미전송(null)과 명시적 false 를 구별한다. */
    private final Boolean isSudoer;

    /** 수정 시 기존 비밀번호를 유지할지 여부 — true 면 {@code password} 는 무시된다. */
    private final boolean keepExistingPassword;

    @JsonCreator
    public UserRequest(
            @JsonProperty("username")             String username,
            @JsonProperty("password")             String password,
            @JsonProperty("passwordHash")         String passwordHash,
            @JsonProperty("isSudoer")             Boolean isSudoer,
            // 옛 저장본 해석 전용 — true 면 password 칸의 값이 이미 해시였다.
            @JsonProperty("isPasswordEncrypted")  Boolean legacyEncrypted,
            // boxed + null-coalesce: Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성 대응 (누락=false).
            @JsonProperty("keepExistingPassword") Boolean keepExistingPassword
    ) {
        boolean legacyHash = legacyEncrypted != null && legacyEncrypted && passwordHash == null;
        this.username             = username;
        this.password             = legacyHash ? null : password;
        this.passwordHash         = legacyHash ? password : passwordHash;
        this.isSudoer             = isSudoer;
        this.keepExistingPassword = keepExistingPassword != null && keepExistingPassword;
    }

    @JsonProperty("keepExistingPassword")
    public boolean isKeepExistingPassword() {
        return keepExistingPassword;
    }

    /** 값의 종류(평문 · 해시). 값이 없으면 empty. */
    @JsonIgnore
    public Optional<LinuxPassword> credential() {
        if (passwordHash != null && !passwordHash.isBlank()) return Optional.of(new LinuxPassword.Hashed(passwordHash));
        if (password != null && !password.isBlank()) return Optional.of(new LinuxPassword.Plain(password));
        return Optional.empty();
    }

    /** 저장본에 값이 실려 있는가 — 수정 저장 병합의 "유지할 것이 있는가" 판정(HF12). */
    @JsonIgnore
    public boolean hasPassword() {
        return credential().isPresent();
    }

    /** 요청에 해시 키가 실렸는가 — 저장 서비스 입구의 위조 판정(D-18). */
    @JsonIgnore
    public boolean carriesHash() {
        return passwordHash != null;
    }

    /** 비밀번호를 뺀 사본 + 기존 유지 표시(수정 폼 pre-fill). */
    public UserRequest withoutPassword() {
        return new UserRequest(username, null, null, isSudoer, null, true);
    }

    /** 저장본(같은 username)의 값을 이어받은 사본(keep 해제) — 이름 · sudo 는 요청 것을 유지한다. */
    public UserRequest retaining(UserRequest previous) {
        return new UserRequest(username, previous.password, previous.passwordHash, isSudoer, null, false);
    }

    /** 평문이면 해시한 사본, 아니면 그대로. */
    public UserRequest hashed(UnaryOperator<String> hasher) {
        return credential()
                .map(c -> new UserRequest(username, null, c.toCrypt(hasher), isSudoer, null, keepExistingPassword))
                .orElse(this);
    }
}
