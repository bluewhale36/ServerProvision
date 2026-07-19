package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 첫 phase 실행기(E1-1) — DIAGNOSE_LINUX 진입 게스트에게 Alpine netboot 체인로드 스크립트를 준다.
 * 이 빈이 registry 에 등록되는 것만으로 dispatch 매트릭스가 6행 HOLD → 7행 위임으로 바뀐다(DEC-6).
 *
 * <p>스크립트는 실행기 소유 text block — 공용 {@code IpxeScripts} 에 phase 별 스크립트를 쌓으면
 * 그것이 곧 조건분기 증식이므로 넣지 않는다(plan §4). 커널 인자 계약(agent.sh 와의 SSOT):
 * {@code provision_token}(에이전트 인증) · {@code provision_base}(콜백 주소). Alpine 공식 파라미터
 * ({@code alpine_repo}/{@code modloop}/{@code apkovl})는 iPXE 가 아니라 Alpine init 이 소비하므로
 * 전부 절대 URL 이어야 한다 — 유일한 주소 원천은 {@code pxe.server.base-url}.</p>
 *
 * <p>모든 로드 명령에 {@code || goto failed} 폴백 — 자산 404 · 네트워크 단절 시 게스트가 죽지 않고
 * 기존 대기 루프와 같은 재시도(sleep 후 /boot 재진입)로 복귀한다(UC-4 류 창을 관찰 가능한 재시도로).
 * EFI 부팅은 iPXE {@code initrd} 행과 별개로 커널 인자 {@code initrd=} 중복 명기가 필수(E1-R §1).</p>
 */
@Component
@ConditionalOnProperty("pxe.assets.root")
@RequiredArgsConstructor
public class DiagnoseLinuxExecutor implements ProvisioningPhaseExecutor {

    private final PxeAssetsProperties properties;

    @Override
    public ProvisioningPhase phase() {
        return ProvisioningPhase.DIAGNOSE_LINUX;
    }

    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery) {
        if (server.getGuestToken() == null) {
            // 등록 트랜잭션(issueTokenIfAbsent)이 항상 선행하므로 도달 불가 — 데이터 손상은 500 이 정직하다.
            throw new IllegalStateException("게스트 토큰 부재 — 등록 invariant 위반. guestServerId=" + server.getId());
        }
        String base = properties.getBaseUrl();
        String assets = base + "/api/pxe/v1/assets";
        return """
                #!ipxe
                echo [provision] chainloading diagnose linux...
                kernel %s/vmlinuz-lts ip=dhcp modules=loop,squashfs console=tty0 console=ttyS0,115200 alpine_repo=%s/repo/main modloop=%s/modloop-lts apkovl=%s/diag.apkovl.tar.gz provision_token=%s provision_base=%s initrd=initramfs-lts || goto failed
                initrd %s/initramfs-lts || goto failed
                boot || goto failed
                :failed
                echo [provision] chainload failed. retrying...
                sleep 30
                chain /api/pxe/v1/boot?%s
                """.formatted(assets, assets, assets, assets,
                server.getGuestToken().value(), base, assets, rebootQuery);
    }
}
