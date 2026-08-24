package com.example.serverprovision.execution.sandbox;

import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosMarkerWriter;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bmc.service.BmcMarkerWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

/**
 * CP5 샌드박스 전용 — 손으로 만든 펌웨어 트리에 <b>유효한 마커를 발급</b>한다.
 *
 * <p>검증 환경을 세울 때 자원 파일을 직접 두는데, 마커는 HMAC 서명이라 손으로 만들 수 없다.
 * 서명 경로를 재현하는 대신 <b>운영 코드가 쓰는 그 writer 를 그대로 부른다</b> — 재현하면 서명 방식이
 * 바뀔 때 조용히 어긋나고, 그 어긋남이 검증 결과를 오염시킨다.</p>
 *
 * <p>{@code provision.sandbox.marker-bootstrap} 이 설정된 환경에서만 빈이 만들어진다 —
 * 운영에는 이 빈 자체가 존재하지 않는다.</p>
 *
 * <p><b>왜 test 소스가 아니라 여기 있는가</b> — 검증은 실행 가능한 jar 를 띄워서 하고, test 소스는
 * 그 jar 에 들어가지 않는다. 마커 발급은 애플리케이션 컨텍스트 안에서 운영 코드의 writer 를 그대로
 * 불러야 의미가 있으므로(서명 방식을 재현하면 그 방식이 바뀔 때 조용히 어긋난다) 이 자리가 맞다.
 * 대신 설정으로 잠가 운영에서는 존재조차 하지 않게 했다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty("provision.sandbox.marker-bootstrap")
@RequiredArgsConstructor
public class Cp5MarkerBootstrap implements ApplicationRunner {

    private final BiosRepository biosRepository;
    private final BmcRepository bmcRepository;
    private final BiosMarkerWriter biosMarkerWriter;
    private final BmcMarkerWriter bmcMarkerWriter;
    private final BundleManifestService bundleManifestService;

    /** 쉼표로 구분한 대상 — {@code bios:13,bmc:4} 형태. */
    @Value("${provision.sandbox.marker-bootstrap}")
    private String targets;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String token : targets.split(",")) {
            String[] parts = token.trim().split(":");
            if (parts.length != 2) {
                continue;
            }
            long id = Long.parseLong(parts[1]);
            try {
                if ("bios".equalsIgnoreCase(parts[0])) {
                    biosRepository.findById(id).ifPresent(bios -> {
                        Path root = Path.of(bios.getTreeRootPath());
                        var manifest = bundleManifestService.compute(root);
                        biosMarkerWriter.writeSignedMarker(bios, root, bios.getBoardModel().getId(),
                                bios.getVersion(), bios.getEntrypointRelativePath(), manifest.manifestHash());
                        log.info("[sandbox] BIOS {} 마커 발급 : {}", id, root);
                    });
                } else if ("bmc".equalsIgnoreCase(parts[0])) {
                    bmcRepository.findById(id).ifPresent(bmc -> {
                        Path root = Path.of(bmc.getTreeRootPath());
                        var manifest = bundleManifestService.compute(root);
                        bmcMarkerWriter.writeSignedMarker(bmc, root, bmc.getBoardModel().getId(),
                                bmc.getVersion(), bmc.getEntrypointRelativePath(), manifest.manifestHash());
                        log.info("[sandbox] BMC {} 마커 발급 : {}", id, root);
                    });
                }
            } catch (Exception e) {
                log.error("[sandbox] {} 마커 발급 실패", token, e);
            }
        }
    }
}
