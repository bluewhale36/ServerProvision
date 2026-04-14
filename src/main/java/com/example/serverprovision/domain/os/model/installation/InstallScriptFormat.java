package com.example.serverprovision.domain.os.model.installation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OS 설치 자동화 스크립트 포맷.
 *
 * <p>{@link RenderedScript} 와 결합하여 PXE/HTTP 서빙 계층이 Content-Type, 파일명,
 * iPXE 커널 파라미터(예: {@code inst.ks=} vs {@code autoinstall ds=nocloud-net;s=})
 * 를 결정하는 데 사용된다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum InstallScriptFormat {

    /** RHEL 계열 Anaconda Kickstart 포맷. */
    KICKSTART("text/plain; charset=utf-8", "install.ks"),

    /** Ubuntu Subiquity autoinstall (cloud-init user-data) YAML 포맷. */
    AUTOINSTALL_YAML("text/yaml; charset=utf-8", "user-data"),

    /** Windows 무인 설치용 unattend.xml (현재 미구현, placeholder). */
    UNATTEND_XML("application/xml; charset=utf-8", "unattend.xml");

    /** HTTP 응답의 Content-Type 헤더 값. */
    private final String mediaType;

    /** 기본 파일명 (iPXE 응답 경로 마지막 세그먼트 등에서 사용). */
    private final String defaultFileName;
}
