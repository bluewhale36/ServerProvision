package com.example.serverprovision.management.os.entity;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.management.os.enums.OSName;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * OS 이미지 — (OSName, osVersion) 조합의 유일한 논리적 단위.
 *
 * <p>MK2 — {@link LifecycleEntity} 상속으로 lifecycle 4 boolean(+audit) 을 super 가 보유한다.
 * 자체 lifecycle 메서드는 super 가 처리하지만, OSImage 만의 cascade 정책 (자식 활성 ISO 도 같이
 * soft-delete) 을 위해 {@link #softDelete()} 만 override 한다. {@code restore()} 는 OSImage 자기 자신
 * 만 살리고, 자식 ISO 는 사용자 명시 액션으로 개별 복구해야 한다 — 과거 삭제 시점이 달라 일괄 복구는
 * 의도치 않은 재노출을 부를 수 있기 때문.</p>
 */
@Entity
@Table(name = "os_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class OSImage extends LifecycleEntity {

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

    // 양방향 매핑 : ISO 가 FK 소유자. 조회 편의를 위해 연관 리스트를 유지한다.
    // cascade/orphanRemoval 을 쓰지 않는 이유 — ISO 생성/삭제는 Service 가 명시적으로 Repository 호출로 수행한다.
    // @Builder.Default — SuperBuilder 가 sub-class 필드의 기본값을 build 시점에 적용하도록 보장.
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

    // ---- LifecycleEntity 가드 메시지용 -----------------------------------

    @Override
    protected Long resourceId() {
        return this.id;
    }

    @Override
    protected String resourceLabel() {
        return "OS 이미지";
    }

    // ---- 도메인 메서드 -------------------------------------------------

    /**
     * OS 이미지를 soft 삭제하면서, 현재 활성 상태인 ISO 들도 함께 soft 삭제한다.
     * <p>super 의 가드 (이미 삭제됨 등) 를 먼저 적용하고, 추가로 자식 ISO cascade 를 수행한다.
     * 이미 삭제된 ISO 는 건드리지 않는다 (이전 삭제 시점 보존).</p>
     */
    @Override
    public void softDelete() {
        super.softDelete();
        // 부모-자식 시점 보존 : 이미 삭제된 ISO 는 건너뛰고, 활성 ISO 만 동반 삭제.
        this.isos.stream()
                .filter(iso -> !iso.isDeleted())
                .forEach(ISO::softDelete);
    }

    public void update(String osVersion, String description) {
        this.osVersion = osVersion;
        this.description = description;
    }
}
