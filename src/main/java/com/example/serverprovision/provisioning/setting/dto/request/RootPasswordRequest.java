package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.vo.LinuxPassword;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * root 계정 비밀번호 설정 (리눅스 스코프 — root 는 리눅스 계정 모델의 표현).
 *
 * <p>E4-1-b-2 D-18 — 화면은 평문({@code password})만 보내고, 서버가 저장 때 해시해 {@code passwordHash} 로만 남긴다.
 * 요청과 저장본이 같은 역직렬화를 타므로 두 키를 모두 읽되, 요청에 해시 키가 실리면 저장 서비스 입구가 거절한다.
 * 옛 저장본의 {@code isPasswordEncrypted=true} 는 해시로 읽는다(쓰기에는 더 이상 싣지 않는다).</p>
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RootPasswordRequest {

    /** 입력 평문. 수정 폼 pre-fill 에는 절대 포함되지 않는다. */
    // Kickstart 스크립트 주입 방지 — ASCII printable 비공백만 (legacy INVALID_ROOT_PASSWORD 이관).
    @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "root 비밀번호에는 공백이나 제어문자를 쓸 수 없습니다.")
    private final String password;

    /** 저장된 SHA-512 crypt 해시. */
    private final String passwordHash;

    /** 수정 시 기존 비밀번호를 유지할지 여부 — true 면 {@code password} 는 무시된다. */
    private final boolean keepExistingPassword;

    @JsonCreator
    public RootPasswordRequest(
            @JsonProperty("password")             String password,
            @JsonProperty("passwordHash")         String passwordHash,
            // 옛 저장본 해석 전용 — true 면 password 칸의 값이 이미 해시였다.
            @JsonProperty("isPasswordEncrypted")  Boolean legacyEncrypted,
            // boxed + null-coalesce: Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성 대응 (누락=false).
            @JsonProperty("keepExistingPassword") Boolean keepExistingPassword
    ) {
        boolean legacyHash = legacyEncrypted != null && legacyEncrypted && passwordHash == null;
        this.password             = legacyHash ? null : password;
        this.passwordHash         = legacyHash ? password : passwordHash;
        this.keepExistingPassword = keepExistingPassword != null && keepExistingPassword;
    }

    public static RootPasswordRequest keepExisting() {
        return new RootPasswordRequest(null, null, null, true);
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

    /** 저장본의 값을 이어받은 사본(keep 해제). */
    public RootPasswordRequest retaining(RootPasswordRequest previous) {
        return new RootPasswordRequest(previous.password, previous.passwordHash, null, false);
    }

    /** 평문이면 해시한 사본, 아니면 그대로. */
    public RootPasswordRequest hashed(UnaryOperator<String> hasher) {
        return credential()
                .map(c -> new RootPasswordRequest(null, c.toCrypt(hasher), null, keepExistingPassword))
                .orElse(this);
    }
}
