package com.example.serverprovision.maintenance.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.maintenance.os.vo.PackageGroupCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * OS 이미지에 귀속되는 패키지 그룹 레코드.
 * 동일 {@code groupCode} 가 여러 ISO 에 의해 제공될 수 있으므로 ISO 와의 관계는 N:M 이며, 소유 측은 {@link ISO} 쪽이다.
 * 여기서는 그룹 본체만 관리하고, 제공자 목록 조회는 Service 가 별도 쿼리로 수행한다.
 */
@Entity
@Table(name = "os_package_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OSPackageGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "os_image_id", nullable = false)
    private OSImage osImage;

    @Embedded
    private PackageGroupCode groupCode;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    // ---- 도메인 메서드 ----------------------------------------------

    /**
     * 재추출된 동일 코드 그룹의 메타데이터로 필드를 갱신한다.
     * 값이 실제로 달라졌을 때만 변경이 반영되도록 JPA dirty checking 에 의존한다.
     */
    public void update(String displayName, String description, boolean isDefault) {
        this.displayName = displayName;
        this.description = description;
        this.isDefault = isDefault;
    }
}
