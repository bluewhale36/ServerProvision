package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.global.exception.DomainValidationException;
import com.example.serverprovision.global.exception.DomainValidationException.Reason;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Pattern;

/**
 * 일반 사용자 계정을 나타내는 값 객체.
 *
 * <p>Kickstart {@code user} 명령을 생성한다. 루트 계정은 {@link RootPassword} 에서 별도로 처리하므로
 * 이 클래스는 오직 일반(비-root) 사용자만 표현한다.
 *
 * <p>사용자명은 POSIX 패턴으로, 비밀번호는 ASCII printable 비공백 문자로 검증한다.
 * 허용 범위를 벗어나면 생성된 Kickstart 스크립트에 옵션 주입이 발생할 수 있다.
 */
@Getter
@ToString
public class User implements InstallScriptable {

    /** POSIX 사용자명 패턴: 소문자·숫자·밑줄·하이픈, 소문자 또는 밑줄로 시작, 최대 32자. */
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-z_][a-z0-9_-]{0,31}$");
    /** Kickstart 비밀번호 값으로 안전한 ASCII printable 문자만 허용 (공백·제어문자 제외). */
    private static final Pattern SAFE_PASSWORD  = Pattern.compile("^[\\x21-\\x7E]+$");

    @NotNull(message = "사용자 이름은 필수 값입니다.")
    @NotBlank(message = "사용자 이름은 필수 입력값입니다.")
    private final String username;

    @NotNull(message = "비밀번호는 필수 값입니다.")
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @ToString.Exclude
    private final String password;

    private final boolean isSudoer;
    private final boolean isPasswordEncrypted;

    @Builder
    @JsonCreator
    User(
            @JsonProperty("username")            String username,
            @JsonProperty("password")            String password,
            // Jackson 이 isSudoer() getter 에서 "is" 접두사를 제거해 "sudoer" 로 직렬화함
            @JsonProperty("sudoer")              Boolean isSudoer,
            // Jackson 이 isPasswordEncrypted() getter 에서 "is" 접두사를 제거해 "passwordEncrypted" 로 직렬화함
            @JsonProperty("passwordEncrypted")   boolean isPasswordEncrypted) {
        if (username == null || !VALID_USERNAME.matcher(username).matches()) {
            throw new DomainValidationException(Reason.INVALID_USER_CREDENTIALS,
                    "사용자명이 올바르지 않습니다: '" + username +
                    "' (소문자·숫자·밑줄·하이픈, 최대 32자, 소문자 또는 밑줄로 시작)");
        }
        if (password == null || !SAFE_PASSWORD.matcher(password).matches()) {
            throw new DomainValidationException(Reason.INVALID_USER_CREDENTIALS,
                    "비밀번호에 공백 또는 제어문자를 사용할 수 없습니다. " +
                    "ASCII printable 문자(공백 제외)만 허용됩니다.");
        }
        this.username            = username;
        this.password            = password;
        this.isSudoer            = isSudoer != null && isSudoer;
        this.isPasswordEncrypted = isPasswordEncrypted;
    }

    @Override
    public String getRHELScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("user --name=").append(username);
        sb.append(" --password=").append(password);
        if (isPasswordEncrypted) sb.append(" --iscrypted");
        else sb.append(" --plaintext");
        if (isSudoer) sb.append(" --groups=wheel");
        sb.append("\n");
        return sb.toString();
    }
}
