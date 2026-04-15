package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSFamily;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.OSTemplate;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * 모든 OS 설치 도메인 모델의 추상 루트.
 *
 * <p>{@code @JsonTypeInfo(property = "osType")} 로 다형성 역직렬화를 수행한다.
 * 판별자 값은 OS 패밀리 + 메이저 버전을 조합한 상수 문자열 (예: {@code ROCKY_LINUX_9}) 을
 * 사용하여 마이너 버전 차이만큼의 동일성은 JSON 에 노출하지 않는다.</p>
 *
 * <p>레거시 호환: 이전 버전에서는 {@code "ROCKY_LINUX"} 판별자를 사용했으며,
 * {@link com.example.serverprovision.application.setting.converter.SettingProcessConverter}
 * 가 읽기 경로에서 레거시 값을 {@code ROCKY_LINUX_9} 로 승격시킨다.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "osType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RockyLinux8Installation.class,  name = "ROCKY_LINUX_8"),
        @JsonSubTypes.Type(value = RockyLinux9Installation.class,  name = "ROCKY_LINUX_9"),
        @JsonSubTypes.Type(value = RockyLinux10Installation.class, name = "ROCKY_LINUX_10"),
        @JsonSubTypes.Type(value = CentOS7Installation.class,      name = "CENTOS_7"),
        @JsonSubTypes.Type(value = UbuntuInstallation.class,       name = "UBUNTU_22_04")
})
public abstract class OSInstallation extends OSTemplate {

    protected OSInstallation(OSName compatibleOS, List<String> compatibleOSVersion) {
        super(compatibleOS, compatibleOSVersion);
    }

    /**
     * 주어진 런타임 컨텍스트를 바탕으로 완성된 설치 자동화 스크립트를 반환한다.
     *
     * <p>반환된 {@link RenderedScript} 는 실제 스크립트 문자열과 포맷(Kickstart / Autoinstall /
     * Unattend) 정보를 함께 담고 있어, 서빙 계층이 Content-Type·파일명·iPXE 커널 파라미터를
     * 결정할 수 있다.</p>
     *
     * @param ctx 호스트명·IP·설치 소스 URL 등 네트워크 의존 정보를 담은 컨텍스트
     * @return 완성된 스크립트 + 포맷 정보
     */
    public abstract RenderedScript getInstallScript(InstallationContext ctx);

    /**
     * 이 설치 모델이 속한 OS 패밀리를 반환한다.
     *
     * <p>뷰 계층의 fragment 디스패치 (RHEL vs Debian)·요청 DTO 다형성 판별자 등
     * OSFamily 기준 분기에서 참조한다. {@link #getOsName()} 에서 위임된다.</p>
     */
    public final OSFamily getOsFamily() {
        return getOsName().getFamily();
    }

    /**
     * 이 설치 모델이 {@code rootpw --allow-ssh} (root SSH 로그인 허용) 옵션을
     * 지원하는지 반환한다.
     *
     * <p>Rocky Linux 10 부터 {@code rootpw} 가 기본적으로 SSH 루트 로그인을 차단하며
     * {@code --allow-ssh} 로 명시적으로 허용해야 한다. 해당 옵션을 가진 구체 모델만
     * {@code true} 를 반환하도록 오버라이드하고, 기본값은 {@code false} 이다.</p>
     *
     * <p>뷰 계층에서 구체 클래스 {@code SimpleName} 에 의존하지 않고 이 플래그로
     * "root SSH 허용" 필드 표시 여부를 결정할 수 있다.</p>
     */
    public boolean supportsAllowSsh() {
        return false;
    }
}
