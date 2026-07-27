package com.example.serverprovision.execution.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TFTP 부팅 인프라 자산 서빙 설정 — {@code pxe.tftp.root}(예: {@code /var/lib/tftpboot}) 가 설정된 환경에서만
 * 존재하는 빈이다({@link PxeAssetsProperties} 와 동형의 {@code @ConditionalOnProperty} 패턴). 미설정 환경은
 * 이 빈이 통째로 빠지고, {@code TftpAssetArea} 가 {@code ObjectProvider} 로 조회해 빈 부재를
 * {@code AreaAvailability.NOT_CONFIGURED} 로 흡수한다 — 대시보드는 미설정이어도 오류 없이 "서빙 비활성"
 * 으로 조회된다(분기 추가 0).
 *
 * <p>진단 자산 서빙과 달리 게스트 콜백 base URL 개념이 없다 — TFTP 자산은 tftpd 가 직접 서빙하고 URL 을
 * 조립하지 않으므로 {@code pxe.server.base-url} 를 요구하지 않는다. 생성자에서 root 정합만 fail-fast 검증한다.</p>
 */
@Getter
@Component
@ConditionalOnProperty("pxe.tftp.root")
public class TftpAssetsProperties {

    /** TFTP 서빙 디렉토리(ipxe.efi 등). */
    private final Path root;

    public TftpAssetsProperties(@Value("${pxe.tftp.root}") String root) {
        if (root == null || root.isBlank()) {
            throw new IllegalStateException(
                    "pxe.tftp.root 가 빈 값이다 — 키를 정의하려면 실제 디렉토리 경로여야 한다 (PXE_TFTP_ROOT).");
        }
        Path candidate = Path.of(root).toAbsolutePath().normalize();
        if (!Files.isDirectory(candidate)) {
            throw new IllegalStateException("pxe.tftp.root 디렉토리가 존재하지 않는다 : " + candidate
                    + " — TFTP 서빙 루트를 먼저 준비한다.");
        }
        this.root = candidate;
    }
}
