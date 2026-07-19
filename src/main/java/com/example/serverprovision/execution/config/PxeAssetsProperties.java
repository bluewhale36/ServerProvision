package com.example.serverprovision.execution.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 진단 리눅스 자산 서빙 설정(E1-1, plan Q2) — {@code pxe.assets.root} 가 설정된 환경에서만 존재하는
 * 빈이다. 미설정 환경은 이 빈과 함께 {@code DiagnoseLinuxExecutor} · 자산 핸들러가 통째로 빠져
 * dispatch 6행 HOLD 라는 기존 명시 동작이 유지된다(분기 추가 0).
 *
 * <p>생성자에서 정합을 fail-fast 검증한다(SecurityPropertiesValidator 선례): 자산 서빙을 켰는데
 * 게스트 콜백 주소({@code pxe.server.base-url})가 없는 반쪽 설정은 게스트가 자산 URL 을 조립할 수
 * 없는 상태이므로 조용히 운행하지 않는다.</p>
 */
@Getter
@Component
@ConditionalOnProperty("pxe.assets.root")
public class PxeAssetsProperties {

    /** 자산 디렉토리(vmlinuz-lts · initramfs-lts · modloop-lts · diag.apkovl.tar.gz · agent.sh · repo/). */
    private final Path root;

    /** 게스트가 자산 · 에이전트 API 에 접근할 이 서버의 절대 base URL (뒤 슬래시 제거 정규화). */
    private final String baseUrl;

    public PxeAssetsProperties(@Value("${pxe.assets.root}") String root,
                               @Value("${pxe.server.base-url:}") String baseUrl) {
        if (root == null || root.isBlank()) {
            throw new IllegalStateException(
                    "pxe.assets.root 가 빈 값이다 — 키를 정의하려면 실제 디렉토리 경로여야 한다 (PXE_ASSETS_ROOT).");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "pxe.assets.root 가 설정되면 pxe.server.base-url 도 필수다 — 게스트의 커널 인자"
                            + "(modloop/apkovl/provision_base)는 절대 URL 만 소비한다 (PXE_SERVER_BASE_URL).");
        }
        Path candidate = Path.of(root).toAbsolutePath().normalize();
        if (!Files.isDirectory(candidate)) {
            throw new IllegalStateException("pxe.assets.root 디렉토리가 존재하지 않는다 : " + candidate
                    + " — scripts/diag-image/build-assets.sh 로 먼저 조립한다.");
        }
        this.root = candidate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
