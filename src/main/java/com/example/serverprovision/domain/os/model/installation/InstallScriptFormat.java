package com.example.serverprovision.domain.os.model.installation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OS 설치 자동화 스크립트 포맷.
 *
 * <p>{@link RenderedScript} 와 결합하여 PXE/HTTP 서빙 계층이 Content-Type, 파일명,
 * iPXE 커널 파라미터(예: {@code inst.ks=} vs {@code autoinstall ds=nocloud-net;s=})
 * 를 결정하는 데 사용된다.</p>
 *
 * <p>Windows 계열({@code unattend.xml})은 현재 진입 경로가 차단되어 있어
 * 해당 포맷 상수는 정의하지 않는다. 추후 Windows 지원 도입 시 그에 맞춰 추가한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum InstallScriptFormat {

    /** RHEL 계열 Anaconda Kickstart 포맷. */
    KICKSTART("text/plain; charset=utf-8", "install.ks"),

    /** Ubuntu Subiquity autoinstall (cloud-init user-data) YAML 포맷. */
    AUTOINSTALL_YAML("text/yaml; charset=utf-8", "user-data");

    /** HTTP 응답의 Content-Type 헤더 값. */
    private final String mediaType;

    /** 기본 파일명 (iPXE 응답 경로 마지막 세그먼트 등에서 사용). */
    private final String defaultFileName;
}
