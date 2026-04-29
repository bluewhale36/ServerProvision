package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Rocky Linux 10.x 설치 모델.
 *
 * <p>호환 버전: 10.0. Kickstart 버전 헤더는 {@code #version=RHEL10}.
 * 10.x 변경점: {@code rootpw} 명령이 기본적으로 SSH 루트 로그인을 허용하지 않으며,
 * 명시적으로 {@code --allow-ssh} 플래그를 주어야 한다.
 * 이 변화를 {@link #allowSshRoot} 필드로 제어하고 {@link #renderRootPwLine()} 에서 반영한다.</p>
 */
@Getter
public class RockyLinux10Installation extends RHELBasedInstallation {

    private static final List<String> COMPATIBLE_VERSIONS = List.of("10.0");

    /**
     * 설치 후 root 계정의 SSH 로그인 허용 여부.
     *
     * <p>Rocky 10 (RHEL 10) Kickstart 에서 {@code rootpw} 는 기본적으로 SSH 루트 로그인을
     * 허용하지 않는다. {@code true} 로 설정하면 {@code rootpw --allow-ssh} 옵션이 추가되어
     * {@code PermitRootLogin yes} 효과를 낸다. {@code rootPassword} 가 {@code null} 이면
     * 이 값은 무의미하다 (root 잠금 상태).</p>
     */
    private final boolean allowSshRoot;

    @Builder
    @JsonCreator
    protected RockyLinux10Installation(
            @JsonProperty("partitions")     List<Partition> partitions,
            @JsonProperty("users")          List<User> users,
            @JsonProperty("rootPassword")   RootPassword rootPassword,
            @JsonProperty("installVersion") String installVersion,
            @JsonProperty("environment")    Environment environment,
            @JsonProperty("timezone")       Timezone timezone,
            @JsonProperty("KDumpEnabled")   boolean isKDumpEnabled,
            // Jackson 이 isAllowSshRoot() getter 에서 "is" 접두사를 제거해 "allowSshRoot" 로 직렬화함
            @JsonProperty("allowSshRoot")   boolean allowSshRoot
    ) {
        super(
                OSName.ROCKY_LINUX, COMPATIBLE_VERSIONS,
                partitions, users, rootPassword,
                installVersion, environment, timezone, isKDumpEnabled
        );
        this.allowSshRoot = allowSshRoot;
    }

    @Override
    protected String getKickstartVersionMarker() {
        return "#version=RHEL10";
    }

    /**
     * Rocky 10 은 {@code rootpw --allow-ssh} 를 지원하는 유일한 구체 모델이다.
     * 뷰 계층이 "root SSH 허용" 필드 표시 여부를 결정할 때 참조한다.
     */
    @Override
    public boolean supportsAllowSsh() {
        return true;
    }

    @Override
    protected String renderRootPwLine() {
        // rootPassword 가 null 이면 기본 구현과 동일하게 빈 문자열 반환 (root 잠금)
        if (rootPassword == null) return "";
        // 기본 구현이 만드는 "rootpw [--iscrypted|--plaintext] <pw>\n" 앞에 --allow-ssh 플래그를 주입.
        // Kickstart 파서는 옵션 순서에 관대하므로 "rootpw --allow-ssh --iscrypted <pw>" 로 생성해도 유효하다.
        String base = rootPassword.getRHELScript();
        if (allowSshRoot) {
            return base.replaceFirst("^rootpw ", "rootpw --allow-ssh ");
        }
        return base;
    }
}
