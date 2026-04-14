package com.example.serverprovision.domain.os.model.installation;

/**
 * OS 설치 자동화 스크립트 생성에 필요한 런타임 컨텍스트 정보를 담는 불변 레코드이다.
 *
 * <p>역할: 도메인 설치 모델({@link OSInstallation} 구현체)이
 * {@link com.example.serverprovision.domain.node.entity.ServerNode} 엔티티를 직접 참조하지 않도록
 * 분리하는 경계 객체이다. 호스트명, 할당 IP, 설치 소스 URL 등 네트워크 의존적인 정보를 전달한다.</p>
 *
 * <p>유스케이스: 설치 스크립트 서빙 컨트롤러가 {@code ServerNode} 에서 값을 추출해
 * 이 레코드를 만들고 {@link OSInstallation#getInstallScript(InstallationContext)} 에 넘긴다.
 * 도메인 계층은 이 레코드만 알면 되므로 엔티티 의존성이 제거된다. Kickstart / Autoinstall /
 * Unattend 등 패밀리별 스크립트가 공통으로 사용하기 위해 이름은 포맷 중립적으로 정의되었다.</p>
 *
 * @param hostname          OS 설치 후 서버에 부여할 호스트명
 * @param assignedIp        OS 설치 후 서버에 부여할 고정 IP 주소 (현재 스크립트 생성에는 미사용, 향후 확장 예정)
 * @param installSourceUrl  PXE 서버의 OS 설치 소스 HTTP URL (예: {@code http://192.168.1.1/rocky9})
 */
public record InstallationContext(
        String hostname,
        String assignedIp,
        String installSourceUrl
) {
    public InstallationContext {
        if (installSourceUrl == null || installSourceUrl.isBlank()) {
            throw new IllegalArgumentException("installSourceUrl 은 필수 값입니다. " +
                    "application.properties 의 pxe.server.install-source-url 또는 환경변수 PXE_INSTALL_SOURCE_URL 을 확인하세요.");
        }
    }
}
