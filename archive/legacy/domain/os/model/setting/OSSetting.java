package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.OSTemplate;
import com.example.serverprovision.domain.os.model.enums.OSFamily;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * OS 설치 후 후처리(Post-install) 설정의 도메인 다형성 루트.
 *
 * <p>{@link com.example.serverprovision.domain.os.model.installation.OSInstallation} 과 동일한
 * {@code osType} 판별자 네임스페이스를 공유한다 (예: {@code ROCKY_LINUX_9} 값은 양쪽에서 동일 OS 를 지칭).
 * OS 계열/메이저 버전별로 서로 다른 Post-install 스크립트 포맷(RHEL Kickstart {@code %post},
 * Ubuntu autoinstall {@code late-commands}, Windows unattend 등) 을 생성한다.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "osType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RockyLinux8Setting.class,  name = "ROCKY_LINUX_8"),
        @JsonSubTypes.Type(value = RockyLinux9Setting.class,  name = "ROCKY_LINUX_9"),
        @JsonSubTypes.Type(value = RockyLinux10Setting.class, name = "ROCKY_LINUX_10"),
        @JsonSubTypes.Type(value = CentOS7Setting.class,      name = "CENTOS_7")
})
public abstract class OSSetting extends OSTemplate {

    protected OSSetting(OSName compatibleOS, List<String> compatibleOSVersion) {
        super(compatibleOS, compatibleOSVersion);
    }

    /** 뷰/Resolver 가 설치·설정 계열을 통일된 기준으로 분기하기 위한 OS 패밀리. */
    public final OSFamily getOsFamily() {
        return getOsName().getFamily();
    }

    /**
     * 후처리 스크립트를 생성한다. RHEL 계열은 Kickstart {@code %post} 블록,
     * Debian 계열은 autoinstall {@code late-commands} YAML 조각 등 OS-specific 포맷으로 반환한다.
     */
    public abstract String getPostInstallScript();
}
