package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

/**
 * dhcpd.leases 파일을 읽어 스냅샷으로 파싱하는 경계 — 영역(dhcpd 대시보드)과 컨트롤러(임대 목록 페이지)가
 * 공용한다(중복 회수). 미구성이거나 파일 부재/IO 실패면 빈 스냅샷(예외 아님)으로 흡수한다.
 */
@Component
public class DhcpLeaseReader {

    private final ObjectProvider<PxeInfraProperties> propertiesProvider;
    private final DhcpLeaseParser parser;

    public DhcpLeaseReader(ObjectProvider<PxeInfraProperties> propertiesProvider, DhcpLeaseParser parser) {
        this.propertiesProvider = propertiesProvider;
        this.parser = parser;
    }

    /** 미구성이거나 파일 부재/IO 실패면 빈 snapshot(예외 아님). */
    public LeaseSnapshot read() {
        PxeInfraProperties p = propertiesProvider.getIfAvailable();
        if (p == null) {
            return LeaseSnapshot.empty();
        }
        try {
            return parser.parse(Files.readString(p.getLeasesPath()));
        } catch (IOException e) {
            return LeaseSnapshot.empty();
        }
    }
}
