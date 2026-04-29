package com.example.serverprovision.domain.os.dto;

import java.util.List;

// 세팅 주문서 생성 폼 — OS 설치 단계용 뷰 전용 DTO
// 계층 구조: OS 타입(OSName) → 버전(os_metadata 행) → 환경 → 패키지 그룹
//
// osFamilyKey/osFamilyDisplayName/selectable 은 Phase 6~10 UI 패밀리 디스패치 지원용:
//   - osFamilyKey        : JS 의 data-os-family 속성으로 매칭되는 OSFamily enum 이름
//   - osFamilyDisplayName: 드롭다운 표기시 "(준비 중)" 접미 등에 사용 (현재는 미사용, 미래 확장용)
//   - selectable         : false 면 <option disabled> 로 렌더 (Windows 계열 placeholder)
public record OSInstallationViewDTO(
        String osNameKey,            // OSName.name() — JS 비교용 키
        String osDisplayName,        // OSName.displayName — 화면 표시용
        String osFamilyKey,          // OSFamily.name() — JS 디스패처가 읽는 패밀리 판별자
        String osFamilyDisplayName,  // OSFamily.displayName — 향후 UI 보조 라벨용
        boolean selectable,          // 드롭다운 선택 가능 여부 (Windows 계열 false)
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
