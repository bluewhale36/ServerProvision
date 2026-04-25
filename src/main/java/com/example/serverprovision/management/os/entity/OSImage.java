package com.example.serverprovision.management.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.management.os.enums.OSName;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * OS 이미지 — (OSName, osVersion) 조합의 유일한 논리적 단위.
 * 하나의 OS 버전에 여러 {@link ISO} 파일이 매달릴 수 있다 (1:N).
 * 삭제는 soft 삭제 ({@code isDeleted}) 이며, OSImage 를 삭제하면 자식 ISO 중 활성 상태인 것들도 같이 soft 삭제된다.
 */
@Entity
@Table(name = "os_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OSImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "os_name", nullable = false, length = 32)
    private OSName osName;

    @Column(name = "os_version", nullable = false, length = 64)
    private String osVersion;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    // 양방향 매핑 : ISO 가 FK 소유자. 조회 편의를 위해 연관 리스트를 유지한다.
    // cascade/orphanRemoval 을 쓰지 않는 이유 — ISO 생성/삭제는 Service 가 명시적으로 Repository 호출로 수행한다.
    @OneToMany(mappedBy = "osImage", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ISO> isos = new ArrayList<>();

    // A1-1 : 설치 환경·패키지 그룹은 OSImage 범위에서 관리된다. 양방향 매핑은 조회 편의용이며 cascade 없음.
    @OneToMany(mappedBy = "osImage", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<OSEnvironment> environments = new ArrayList<>();

    @OneToMany(mappedBy = "osImage", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<OSPackageGroup> packageGroups = new ArrayList<>();

    // ---- 도메인 메서드 -------------------------------------------------

    public void toggleEnabled() {
        this.isEnabled = !this.isEnabled;
    }

    /**
     * OS 이미지를 soft 삭제하면서, 현재 활성 상태인 ISO 들도 함께 soft 삭제한다.
     * 이미 삭제된 ISO 는 건드리지 않는다 (이전 삭제 시점 보존).
     */
    public void softDelete() {
        this.isDeleted = true;
        this.isos.stream()
                .filter(iso -> !iso.isDeleted())
                .forEach(ISO::softDelete);
    }

    /**
     * OS 이미지만 복구한다. 자식 ISO 는 개별적으로 복구해야 한다 —
     * 과거 삭제 시점이 달라 일괄 복구는 의도치 않은 재노출을 부를 수 있기 때문.
     */
    public void restore() {
        this.isDeleted = false;
    }

    public void update(String osVersion, String description) {
        this.osVersion = osVersion;
        this.description = description;
    }
}
