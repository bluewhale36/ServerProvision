package com.example.serverprovision.domain.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * ISO 기본 저장소에서 제공되는 패키지 참조 인덱스.
 *
 * <p>OSMetadata 등록/재인덱싱 시 {@code repodata/primary.xml.gz} 에서 추출된 패키지 이름을 저장한다.
 * 세팅 주문서 생성 시 사용자가 입력한 {@code additionalPackages} 이름이 이 테이블에 존재하는지로
 * 간단한 오타 검증을 제공한다. EPEL·사내 저장소 패키지는 이 인덱스에 포함되지 않으므로
 * 검증 실패 시에도 "경고" 수준으로만 사용한다 (hard error 금지).</p>
 */
@Entity
@Table(
        name = "os_package_ref",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_os_package_ref_meta_name",
                        columnNames = {"os_metadata_id", "name"}
                )
        },
        indexes = {
                @Index(name = "ix_os_package_ref_meta_name",
                        columnList = "os_metadata_id,name")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OSPackageRef extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "os_metadata_id", nullable = false)
    private OSMetadata osMetadata;

    /** RPM 패키지 이름 (예: {@code vim-enhanced}, {@code httpd}). arch 접미사는 제외. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 패키지가 속한 저장소 레이블 (예: {@code BaseOS}, {@code AppStream}). 추적/디버깅용. */
    @Column(name = "repo", length = 100)
    private String repo;
}
