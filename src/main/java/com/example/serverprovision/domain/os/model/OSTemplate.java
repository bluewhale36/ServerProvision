package com.example.serverprovision.domain.os.model;

import com.example.serverprovision.domain.os.model.enums.OSName;
import jakarta.validation.constraints.NotNull;

import java.util.List;


public abstract class OSTemplate {

    @NotNull(message = "호환되는 OS 이름은 필수 값입니다.")
    private final OSName COMPATIBLE_OS;

    /**
     * 호환되는 OS 버전 목록입니다. 비어있을 경우 모든 버전과 호환된다고 간주합니다.
     */
    @NotNull(message = "호환되는 OS 버전 목록은 필수 값입니다.")
    private final List<String> COMPATIBLE_OS_VERSIONS;

    protected OSTemplate(OSName compatibleOS, List<String> compatibleOSVersion) {
        this.COMPATIBLE_OS = compatibleOS;
        this.COMPATIBLE_OS_VERSIONS = compatibleOSVersion;
    }

    public final boolean isCompatible(OSName osName, String osVersion) {
        return this.COMPATIBLE_OS == osName && (COMPATIBLE_OS_VERSIONS.isEmpty() || COMPATIBLE_OS_VERSIONS.contains(osVersion));
    }

    /**
     * 이 설치 모델이 호환되는 대표 {@link OSName} 을 반환한다.
     *
     * <p>Thymeleaf / 뷰 디스패치에서 구체 클래스 {@code SimpleName} 대신 OSName 을 기준으로
     * 분기할 수 있도록 enum 자체를 노출한다.</p>
     */
    public final OSName getOsName() {
        return this.COMPATIBLE_OS;
    }

    protected final boolean isVersionCompatible(String osVersion) {
        return COMPATIBLE_OS_VERSIONS.isEmpty() || COMPATIBLE_OS_VERSIONS.contains(osVersion);
    }
}
