package com.example.serverprovision.execution.pxeinfra.render;

import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * desired 구성을 dnsmasq 조각({@code /etc/dnsmasq.d} drop-in) 텍스트로 렌더한다(R16). {@link #render} 는 순수 함수(IO · 예외
 * 없음)이며 같은 입력이면 항상 바이트 단위로 같은 조각을 낸다 — 골든 문자열 검증과 드리프트 판정이 이 결정성에 기댄다.
 *
 * <p><b>결정성 규율</b>(종전 dhcpd 렌더러와 같다) : (1) 머리말은 가변값 없는 정적 문자열 (2) 줄 순서는 리터럴로 고정하고
 * Map/Set 순회를 쓰지 않는다 (3) 항상 trailing newline (4) 선택 줄(보조 DNS · 도메인)의 유무는 분기로 명시.
 * 모드별 줄은 {@link com.example.serverprovision.execution.pxeinfra.enums.DhcpMode} 상수가 든다.</p>
 *
 * <p>공통 줄의 뜻 — {@code port=0} 은 dnsmasq 의 DNS 를 끈다(우리는 DHCP 만 · 조각에 있어도 전역). {@code bind-interfaces} +
 * {@code listen-address} 는 부트 서버 IP 가 붙은 NIC 에만 응답하게 한다(다중 NIC · D-6). iPXE 2단 분기는 user-class
 * {@code iPXE} 태그와 {@code tag-if} 로 ROM 과 iPXE 를 갈라 각자 다른 파일을 준다(D-4 — 같은 파일을 주면 무한 체인).</p>
 */
@Component
public class DnsmasqConfigRenderer {

    /** 조각 머리말 — 사람이 직접 편집하지 말라는 고정 안내(가변값 없음, 빈 조각과도 공유하는 SSOT). */
    static final String HEADER = "# Managed by ServerProvision. Do not edit by hand.";

    /** PXE 부트 로더 파일명. E1-I 자산 계약상 iPXE EFI 바이너리로 고정한다(자산 = {@code TftpAsset.IPXE_EFI}). */
    static final String BOOT_FILENAME = "ipxe.efi";

    /** iPXE 2단 진입 스크립트(tftp) — /api/pxe/v1/boot 로 체인하는 1단 스크립트 파일명. */
    static final String IPXE_SCRIPT_FILENAME = "boot.ipxe";

    /** desired 를 dnsmasq 조각으로 렌더한다(순수 함수). */
    public String render(PxeNetworkConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("port=0\n");
        sb.append("bind-interfaces\n");
        sb.append("listen-address=").append(config.getBootServerIp().value()).append('\n');
        sb.append("log-dhcp\n");
        config.getDhcpMode().appendAddressing(sb, config);
        sb.append("dhcp-match=set:efi,option:client-arch,7\n");
        sb.append("dhcp-match=set:efi,option:client-arch,9\n");
        sb.append("dhcp-userclass=set:ipxe,iPXE\n");
        sb.append("tag-if=set:rom,tag:efi,tag:!ipxe\n");
        config.getDhcpMode().appendRomBoot(sb, config, BOOT_FILENAME);
        sb.append("dhcp-boot=tag:ipxe,").append(IPXE_SCRIPT_FILENAME).append(",,")
                .append(config.getBootServerIp().value()).append('\n');
        return sb.toString();
    }

    /**
     * 구성이 없는 유효한 빈 조각(머리말만). 최초 적용 실패의 복원본으로 쓴다 — 파일을 지우는 대신 "읽히되 구성은 없는"
     * 상태로 되돌린다(D-8 · dhcpd 시절 D7 의 관용 유지). dnsmasq 의 conf-dir 는 파일 부재도 허용하지만 복원 경로를 하나로 둔다.
     */
    public String renderEmpty() {
        return HEADER + "\n# (구성 없음 — 유효한 빈 조각)\n";
    }

    /**
     * 렌더 결과를 조각과 같은 디렉토리({@code dir}) 하위 temp 파일로 쓴 뒤 그 경로를 돌려주는 얇은 래퍼.
     * 같은 파일시스템이어야 뒤이은 {@code ATOMIC_MOVE} 가 성립하므로 조각 디렉토리에 만든다(호출자 규율).
     */
    public Path renderToTemp(PxeNetworkConfig config, Path dir) throws IOException {
        Path temp = dir.resolve("dnsmasq-fragment-" + UUID.randomUUID() + ".tmp");
        Files.writeString(temp, render(config));
        return temp;
    }
}
