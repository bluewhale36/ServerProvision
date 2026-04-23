package com.example.serverprovision.maintenance.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.maintenance.os.vo.EnvironmentCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;

/**
 * OS 이미지에 귀속되는 설치 환경.
 * comps.xml {@code <environment>} 한 건에 대응하며, 해당 환경이 포함하는 패키지 그룹 목록은 N:M 조인으로 묶인다.
 * 제공 ISO 관계(N:M)는 {@link ISO} 가 소유 측을 들고 있으므로 여기서는 양방향 매핑을 두지 않는다.
 * 조회 편의가 필요하면 Service 가 별도 쿼리를 수행한다.
 */
@Entity
@Table(name = "os_environment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OSEnvironment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "os_image_id", nullable = false)
    private OSImage osImage;

    @Embedded
    private EnvironmentCode environmentCode;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    // 환경 ↔ 패키지 그룹 N:M. 소유 측은 OSEnvironment (추출 결과가 환경 단위로 묶이기 때문).
    // 부모 환경이 지워지면 조인 레코드가 함께 사라지도록 CASCADE 설정.
    @ManyToMany(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinTable(
            name = "environment_package_group",
            joinColumns = @JoinColumn(name = "os_environment_id"),
            inverseJoinColumns = @JoinColumn(name = "os_package_group_id")
    )
    @Builder.Default
    private List<OSPackageGroup> groups = new ArrayList<>();

    // ---- 도메인 메서드 ----------------------------------------------

    public void update(String displayName, String description, boolean isDefault) {
        this.displayName = displayName;
        this.description = description;
        this.isDefault = isDefault;
    }

    /**
     * 환경이 참조하는 그룹 목록을 치환한다. Set 비교로 실제 변경이 있을 때만 호출하도록 caller 가 통제한다.
     */
    public void replaceGroups(List<OSPackageGroup> newGroups) {
        this.groups.clear();
        this.groups.addAll(newGroups);
    }
}
