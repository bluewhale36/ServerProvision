package com.example.serverprovision.execution.pxeinfra.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * dnsmasq PXE 인프라 관측 설정(R16) — {@code pxe.dnsmasq.fragment-path} 가 설정된 환경에서만 존재하는 빈이다
 * ({@code TftpAssetsProperties} 와 동형의 {@code @ConditionalOnProperty} 패턴). 미설정 환경은 이 빈이 통째로
 * 빠지고, DHCP 영역이 {@code ObjectProvider} 로 조회해 빈 부재를 {@code AreaAvailability.NOT_CONFIGURED} 로
 * 흡수한다 — 대시보드는 미설정이어도 오류 없이 "서빙 비활성" 으로 조회된다(분기 추가 0).
 *
 * <p>TFTP 설정과 달리 <b>파일 존재는 fail-fast 하지 않는다</b> — 조각 · 구성 · 임대 파일 부재는 정상 관측 상태
 * (ABSENT · 빈 임대)로 흡수하는 것이 이 영역의 설계라, 생성자는 경로 문자열이 빈 값인지만 거절한다.</p>
 */
@Getter
@Component
@ConditionalOnProperty("pxe.dnsmasq.fragment-path")
public class PxeInfraProperties {

    /** 앱이 관리하는 dnsmasq 조각 파일({@code /etc/dnsmasq.d/…} drop-in · 존재 · 크기 · 수정시각 슬롯). */
    private final Path fragmentPath;

    /** {@code dnsmasq --test -C} 대상(조각을 conf-dir 로 읽는 메인 구성). */
    private final Path confPath;

    /** dnsmasq 가 쓰는 임대 파일. */
    private final Path leasesPath;

    public PxeInfraProperties(
            @Value("${pxe.dnsmasq.fragment-path}") String fragmentPath,
            @Value("${pxe.dnsmasq.conf-path:/etc/dnsmasq.conf}") String confPath,
            @Value("${pxe.dnsmasq.leases-path:/var/lib/dnsmasq/dnsmasq.leases}") String leasesPath) {
        this.fragmentPath = normalizeRequired("pxe.dnsmasq.fragment-path", fragmentPath);
        this.confPath = normalizeRequired("pxe.dnsmasq.conf-path", confPath);
        this.leasesPath = normalizeRequired("pxe.dnsmasq.leases-path", leasesPath);
    }

    private static Path normalizeRequired(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    key + " 가 빈 값이다 — 키를 정의하려면 실제 파일 경로여야 한다.");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
