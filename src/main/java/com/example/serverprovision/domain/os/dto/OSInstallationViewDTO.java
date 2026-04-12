package com.example.serverprovision.domain.os.dto;

import java.util.List;

// 세팅 주문서 생성 폼 — OS 설치 단계용 뷰 전용 DTO
// 계층 구조: OS 타입(OSName) → 버전(os_metadata 행) → 환경 → 패키지 그룹
public record OSInstallationViewDTO(
        String osNameKey,       // OSName.name() — JS 비교용 키
        String osDisplayName,   // OSName.displayName — 화면 표시용
        List<OSVersionViewItem> versions
) {
    // os_metadata 한 행 (특정 OS 버전)
    public record OSVersionViewItem(
            Long id,            // os_metadata.id — 백엔드로 전송할 osMetadataId
            String version,     // os_metadata.os_version
            List<EnvironmentViewItem> environments
    ) {
        // OS 환경(Environment) 항목
        public record EnvironmentViewItem(
                Long id,
                String displayName,
                boolean isDefault,
                List<PackageGroupViewItem> packageGroups
        ) {
            // 패키지 그룹 항목
            public record PackageGroupViewItem(
                    Long id,
                    String displayName,
                    boolean isDefault
            ) {}
        }
    }
}
