package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.vo.GuestToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-1 CP4 — 체인로드 스크립트 조립 계약. 이 문자열이 곧 게스트(iPXE · Alpine init · agent.sh)와의
 * 계약이다: 커널 인자 이름(provision_token/provision_base)과 자산 URL 구조가 바뀌면 게스트가 깨진다.
 */
class DiagnoseLinuxExecutorTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";
    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 19, 2, 0);

    @TempDir Path assetsRoot;

    private DiagnoseLinuxExecutor executor;

    @BeforeEach
    void setUp() {
        // base-url 뒤 슬래시는 properties 가 정규화 — 스크립트에 이중 슬래시가 없어야 한다.
        executor = new DiagnoseLinuxExecutor(
                new PxeAssetsProperties(assetsRoot.toString(), "http://10.0.2.2:7777/"));
    }

    private GuestServer server(GuestToken token) {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID())
                .guestToken(token).build();
    }

    private ProvisioningProgress progress() {
        return ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.DIAGNOSE_LINUX).lastTransitionAt(T).startedAt(T).build();
    }

    @Test
    @DisplayName("체인로드 스크립트 — 자산 절대 URL · 커널 인자 계약 · EFI initrd= · 실패 폴백 전부 포함")
    void bootScript_containsFullContract() {
        String script = executor.bootScript(server(new GuestToken(TOKEN)), progress(), "systemUUID=abc");

        assertThat(script)
                .startsWith("#!ipxe")
                .contains("kernel http://10.0.2.2:7777/api/pxe/v1/assets/vmlinuz-lts")
                .contains("alpine_repo=http://10.0.2.2:7777/api/pxe/v1/assets/repo/main")
                .contains("modloop=http://10.0.2.2:7777/api/pxe/v1/assets/modloop-lts")
                .contains("apkovl=http://10.0.2.2:7777/api/pxe/v1/assets/diag.apkovl.tar.gz")
                .contains("provision_token=" + TOKEN)
                .contains("provision_base=http://10.0.2.2:7777")
                .contains("initrd=initramfs-lts")                       // EFI 필수 중복 명기(E1-R §1)
                .contains("initrd http://10.0.2.2:7777/api/pxe/v1/assets/initramfs-lts")
                .contains(":failed")                                    // 로드 실패 폴백 라벨
                .contains("chain /api/pxe/v1/boot?systemUUID=abc")      // 재진입은 원본 쿼리 그대로
                .doesNotContain("7777//");                              // base-url 정규화 검증
    }

    @Test
    @DisplayName("phase 판별자 = DIAGNOSE_LINUX (registry 위임 키)")
    void phase_isDiagnoseLinux() {
        assertThat(executor.phase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);
    }

    @Test
    @DisplayName("토큰 부재 — 등록 invariant 위반은 500 이 정직하다 (도달 불가 가드)")
    void missingToken_throwsIllegalState() {
        assertThatThrownBy(() -> executor.bootScript(server(null), progress(), "q"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("게스트 토큰 부재");
    }
}
