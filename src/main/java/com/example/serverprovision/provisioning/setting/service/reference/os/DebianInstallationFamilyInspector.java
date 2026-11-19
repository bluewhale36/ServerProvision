package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.provisioning.setting.dto.request.LinuxInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.exception.InvalidUserAccessException;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Debian 계열 설치 참조 검사 — 계열 고유 자원 참조는 없고(hostname/패키지 문자열은 참조 아님),
 * 리눅스 공통 파티션 규칙만 검증한다. hostname·패키지 형식은 Layer A(@Pattern, U2-4)가 담당 —
 * 이후 autoinstall 고유 규칙이 생기면 이 빈에서 자란다.
 */
@Component
public class DebianInstallationFamilyInspector implements OSInstallationFamilyInspector {

    @Override
    public OSFamily family() {
        return OSFamily.DEBIAN_BASED;
    }

    /** Ubuntu 설치 자동화는 아직 없다(E4-1-b-2 비목표). */
    @Override
    public String installBlockReason(OSMetadata os) {
        return UBUNTU_BLOCK_REASON;
    }

    static final String UBUNTU_BLOCK_REASON = "Ubuntu 는 아직 설치할 수 없습니다.";

    @Override
    public void validateReferences(OSInstallationRequest request) {
        LinuxInstallationRequest linux = (LinuxInstallationRequest) request;
        LinuxPartitionRules.validate(linux.getPartitions());
        // Ubuntu autoinstall 은 root 잠금 기본 — identity 사용자 1+ 필수(사용자 확정 2026-07-05).
        if (linux.getUsers() == null || linux.getUsers().isEmpty()) {
            throw InvalidUserAccessException.ubuntuUserRequired();
        }
    }

    @Override
    public List<String> describeDeprecatedReferences(OSInstallationRequest request) {
        return List.of();
    }
}
