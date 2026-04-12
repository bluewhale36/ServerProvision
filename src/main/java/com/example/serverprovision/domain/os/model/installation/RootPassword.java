package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.global.exception.DomainValidationException;
import com.example.serverprovision.global.exception.DomainValidationException.Reason;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Pattern;

/**
 * 루트 계정 비밀번호를 나타내는 값 객체.
 *
 * <p>Kickstart {@code rootpw} 명령을 생성한다. {@code null} 이면 루트 계정이 잠긴 상태로 설치된다.
 * Rocky Linux 9 기준, 루트가 잠긴 경우에도 wheel 그룹 일반 사용자가 있으면 시스템 접근이 가능하다.
 *
 * <p>비밀번호 값은 공백/제어문자를 허용하지 않는다. 허용되면 생성된 Kickstart 스크립트에서
 * 라인 분리 또는 옵션 주입이 발생할 수 있다.
 */
@Getter
@ToString
public class RootPassword implements InstallScriptable {

    /** Kickstart 비밀번호 값으로 안전한 ASCII printable 문자만 허용 (공백·제어문자 제외). */
    private static final Pattern SAFE_PASSWORD = Pattern.compile("^[\\x21-\\x7E]+$");

    @ToString.Exclude
    private final String password;
    private final boolean isPasswordEncrypted;

    @Builder
    @JsonCreator
    RootPassword(
            @JsonProperty("password")          String password,
            // Jackson 이 isPasswordEncrypted() getter 에서 "is" 접두사를 제거해 "passwordEncrypted" 로 직렬화함
            @JsonProperty("passwordEncrypted") boolean isPasswordEncrypted) {
        if (password == null || !SAFE_PASSWORD.matcher(password).matches()) {
            throw new DomainValidationException(Reason.INVALID_ROOT_PASSWORD,
                    "root 비밀번호에 공백 또는 제어문자를 사용할 수 없습니다. " +
                    "ASCII printable 문자(공백 제외)만 허용됩니다.");
        }
        this.password = password;
        this.isPasswordEncrypted = isPasswordEncrypted;
    }

    @Override
    public String getRHELScript() {
        StringBuilder sb = new StringBuilder();
        // Kickstart rootpw 문법: rootpw [--iscrypted | --plaintext] <password>
        // 옵션 플래그는 반드시 password 앞에 위치해야 한다.
        sb.append("rootpw ");
        if (isPasswordEncrypted) sb.append("--iscrypted ");
        else sb.append("--plaintext ");
        sb.append(password).append("\n");
        return sb.toString();
    }
}
