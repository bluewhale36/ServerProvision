package com.example.serverprovision.execution.pxeinfra.render;

import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * desired 구성을 dhcpd 조각 텍스트로 렌더한다. {@link #render} 는 순수 함수(IO·예외 없음)이며, 같은 입력이면
 * 항상 바이트 단위로 같은 조각을 낸다 — 골든 문자열 검증과 드리프트 판정이 이 결정성에 기댄다.
 *
 * <p><b>결정성 규율</b> : (1) 머리말은 가변값 없는 정적 문자열, (2) 넷마스크는 {@code SubnetCidr.netmask()} 의
 * 결정적 변환, (3) 삽입 순서는 리터럴로 고정하고 Map/Set 순회를 쓰지 않으며, (4) 항상 trailing newline 으로
 * 끝나고, (5) secondaryDns·domainName 의 유무 4조합을 분기로 명시한다.</p>
 */
@Component
public class DhcpdConfigRenderer {

    /** 조각 머리말 — 사람이 직접 편집하지 말라는 고정 안내(가변값 없음, 빈 조각과도 공유하는 SSOT). */
    static final String HEADER = "# Managed by ServerProvision. Do not edit by hand.";

    /**
     * PXE 부트 로더 파일명. E1-I 자산 계약상 iPXE EFI 바이너리로 고정한다.
     * T3(실 Rocky 검증)에서 실제 배포 자산명과 대조해 보정할 잠정 상수다 — 한 곳에만 둔다.
     */
    static final String BOOT_FILENAME = "ipxe.efi";

    /** iPXE 2단 진입 스크립트(tftp) — /api/pxe/v1/boot 로 체인하는 1단 스크립트 파일명. */
    static final String IPXE_SCRIPT_FILENAME = "boot.ipxe";

    /** desired 를 dhcpd 조각으로 렌더한다(순수 함수). */
    public String render(PxeNetworkConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("subnet ").append(config.getSubnetCidr().networkAddress())
                .append(" netmask ").append(config.getSubnetCidr().netmask()).append(" {\n");
        sb.append("  range ").append(config.getRangeStart().value())
                .append(' ').append(config.getRangeEnd().value()).append(";\n");
        sb.append("  option routers ").append(config.getRouters().value()).append(";\n");

        sb.append("  option domain-name-servers ").append(config.getPrimaryDns().value());
        if (config.getSecondaryDns() != null) {
            sb.append(", ").append(config.getSecondaryDns().value());
        }
        sb.append(";\n");

        if (config.getDomainName() != null) {
            sb.append("  option domain-name \"").append(config.getDomainName()).append("\";\n");
        }

        sb.append("  default-lease-time ").append(config.getDefaultLeaseSeconds().value()).append(";\n");
        sb.append("  max-lease-time ").append(config.getMaxLeaseSeconds().value()).append(";\n");
        sb.append('\n');
        sb.append("  class \"pxeclients\" {\n");
        sb.append("    match if substring(option vendor-class-identifier, 0, 9) = \"PXEClient\";\n");
        sb.append("    next-server ").append(config.getBootServerIp().value()).append(";\n");
        // 실기 보정(2026-08-24 통합 테스트): 원본 ipxe.efi 는 재DHCP 시 같은 파일을 받으면 무한
        // 체인 루프다. iPXE(user-class)에게는 1단 스크립트(boot.ipxe, tftp)를 주어 /api/pxe/v1/boot
        // 로 넘어가게 분기한다 — 사무실 실측 구성(2026-06)과 동일 관용구.
        sb.append("    if exists user-class and option user-class = \"iPXE\" {\n");
        sb.append("      filename \"").append(IPXE_SCRIPT_FILENAME).append("\";\n");
        sb.append("    } else {\n");
        sb.append("      filename \"").append(BOOT_FILENAME).append("\";\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 서브넷 선언이 없는 유효한 빈 조각(머리말만). 최초 적용 실패의 복원본으로 쓴다 — 파일을 지우지 않고
     * "include 는 성립하되 구성은 없는" 상태로 되돌린다(D7).
     */
    public String renderEmpty() {
        return HEADER + "\n# (구성 없음 — 유효한 빈 조각)\n";
    }

    /**
     * 렌더 결과를 조각과 같은 디렉토리({@code dir}) 하위 temp 파일로 쓴 뒤 그 경로를 돌려주는 얇은 래퍼.
     * 같은 파일시스템이어야 뒤이은 {@code ATOMIC_MOVE} 가 성립하므로 조각 디렉토리에 만든다(호출자 규율).
     */
    public Path renderToTemp(PxeNetworkConfig config, Path dir) throws IOException {
        Path temp = dir.resolve("dhcpd-fragment-" + UUID.randomUUID() + ".tmp");
        Files.writeString(temp, render(config));
        return temp;
    }
}
