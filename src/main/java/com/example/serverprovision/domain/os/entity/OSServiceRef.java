package com.example.serverprovision.domain.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * ISO 기본 저장소 패키지에서 제공되는 systemd unit(.service) 참조 인덱스.
 *
 * <p>OSMetadata 재인덱싱 시 {@code repodata/filelists.xml.gz} 의 각 패키지 파일 목록에서
 * {@code .service} suffix 를 가진 파일의 basename 을 추출해 저장한다. 예:
 * {@code /usr/lib/systemd/system/httpd.service} → {@code httpd}.</p>
 *
 * <p>사용자가 입력한 서비스 이름의 오타 검증에 사용된다. 인덱스 미스는 "경고" 수준이며,
 * 사용자가 명시적으로 제공하는 unit 파일이나 alias 의 경우 실제 실행은 정상 동작할 수 있다.</p>
 */
@Entity
@Table(
        name = "os_service_ref",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_os_service_ref_meta_name",
                        columnNames = {"os_metadata_id", "name"}
                )
        },
        indexes = {
                @Index(name = "ix_os_service_ref_meta_name",
                        columnList = "os_metadata_id,name")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OSServiceRef extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "os_metadata_id", nullable = false)
    private OSMetadata osMetadata;

    /** systemd unit 이름. {@code .service} suffix 는 제거된 형태로 저장한다 (예: {@code httpd}). */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 이 unit 을 제공하는 RPM 패키지 이름. 추적/디버깅용. */
    @Column(name = "provided_by_pkg", length = 255)
    private String providedByPkg;
}
