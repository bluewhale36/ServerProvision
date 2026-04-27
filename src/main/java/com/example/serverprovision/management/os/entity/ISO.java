package com.example.serverprovision.management.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * OS 이미지에 귀속되는 ISO 파일 레코드. 실제 ISO 바이너리는 {@code isoPath} 가 가리키는 파일시스템 경로에 둔다.
 * 한 OS 버전이 복수 ISO 를 가질 수 있어 (DVD/Boot/Minimal 등) 별도 엔티티로 분리한다.
 */
@Entity
@Table(name = "iso")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ISO extends BaseTimeEntity implements Markable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "os_image_id", nullable = false)
    private OSImage osImage;

    @Column(name = "iso_path", nullable = false, length = 1024)
    private String isoPath;

    /**
     * 업로드된 ISO 파일의 SHA-256 체크섬 (hex, 64자).
     * 경로만 지정해 등록된 레코드는 NULL 일 수 있다 — 해시 계산 시점을 갖지 못했기 때문이다.
     * 같은 내용의 ISO 를 서로 다른 이름·경로로 올릴 경우 이 필드를 비교해 중복 등록을 차단한다.
     */
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /**
     * MK1 마커 시스템 — sidecar `<isoPath>.provision.json` 에 기록된 manifest hash.
     * 등록 시점 1회 SHA-256(file bytes) 계산. {@code checksum} 과 같은 값이 들어갈 수 있으나
     * 의미는 다르다 — checksum 은 중복 등록 차단용, manifestHash 는 마커 무결성 검증용.
     * 마커 미발급 ISO (MK1 이전 등록) 는 NULL.
     */
    @Column(name = "manifest_hash", length = 64)
    private String manifestHash;

    /**
     * MK1 마커 시스템 — HMAC-SHA256 서명 (sidecar 마커의 signature 필드와 동일).
     * 2-phase save 로 entity 선저장(NULL) 후 signature 계산하여 갱신. 마커 미발급 ISO 는 NULL.
     */
    @Column(name = "marker_signature", length = 64)
    private String markerSignature;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_integrity_status", nullable = false, length = 32)
    @Builder.Default
    private com.example.serverprovision.management.bios.vo.IntegrityStatus lastIntegrityStatus =
            com.example.serverprovision.management.bios.vo.IntegrityStatus.NOT_VERIFIED;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    /**
     * 추출 파이프라인이 정상 완료된 시각. NULL 이면 "완료된 적 없음" 을 의미한다.
     * 중단(마운트 실패, comps.xml 파싱 실패, DB 저장 중 예외 등) 시에는 이 값이 세팅되지 않으므로,
     * providedEnvironments 일부가 남아 있더라도 재추출을 허용한다 — "제공 관계 유무" 대신
     * "완료 플래그" 를 판정 기준으로 삼는 이유.
     */
    @Column(name = "extracted_at")
    private Instant extractedAt;

    // ---- 제공 관계 (A1-1 추출 결과) ----------------------------------
    // ISO 한 건이 어떤 환경·그룹을 "제공"하는지를 N:M 으로 기록한다.
    // ISO 가 조인 소유 측 — 추출 실행 주체가 ISO 단위라서, 관계 재구성 시 "이 ISO 의 이전 제공 내역" 을 한 번에 끌어와 비교하는 편이 자연스럽다.
    // 조인 테이블의 양쪽 FK 는 부모가 지워질 때 함께 사라져야 한다 — DB DDL 에서도 CASCADE 로 고정.
    @ManyToMany(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinTable(
            name = "iso_environment",
            joinColumns = @JoinColumn(name = "iso_id"),
            inverseJoinColumns = @JoinColumn(name = "os_environment_id")
    )
    @Builder.Default
    private List<OSEnvironment> providedEnvironments = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinTable(
            name = "iso_package_group",
            joinColumns = @JoinColumn(name = "iso_id"),
            inverseJoinColumns = @JoinColumn(name = "os_package_group_id")
    )
    @Builder.Default
    private List<OSPackageGroup> providedPackageGroups = new ArrayList<>();

    // ---- 도메인 메서드 -------------------------------------------------

    public void toggleEnabled() {
        this.isEnabled = !this.isEnabled;
    }

    public void softDelete() {
        this.isDeleted = true;
    }

    public void restore() {
        this.isDeleted = false;
    }

    public void update(String isoPath, String description) {
        this.isoPath = isoPath;
        this.description = description;
    }

    /**
     * 이 ISO 가 "제공한다" 고 기록된 환경·그룹 관계를 한 번에 치환한다.
     * 기존 리스트와 동일하면 caller 가 호출 자체를 생략하는 것이 자원 낭비 방지 정책(A1-1) 에 부합한다.
     */
    public void replaceProvisions(List<OSEnvironment> environments, List<OSPackageGroup> packageGroups) {
        this.providedEnvironments.clear();
        this.providedEnvironments.addAll(environments);
        this.providedPackageGroups.clear();
        this.providedPackageGroups.addAll(packageGroups);
    }

    /**
     * 추출 파이프라인이 정상 완료된 시점을 기록한다.
     * CompsMergeService 가 모든 merge 작업을 성공적으로 끝낸 직후에 호출되어야 한다.
     * 중단·실패 시에는 호출되지 않으므로 extractedAt 은 null 유지 → 재추출 허용.
     */
    public void markExtracted() {
        this.extractedAt = Instant.now();
    }

    /**
     * 재추출을 허용할지 결정하는 공식 기준.
     * providedEnvironments 유무가 아닌 completion 플래그로 판정해, 일부만 저장된 후 중단된
     * 상태에서도 재추출이 가능하도록 한다.
     */
    public boolean isExtractionComplete() {
        return extractedAt != null;
    }

    /** PATH_DRIFT 자동 적용 시 IsoMarkableScanner 가 호출 — DB 의 isoPath 만 갱신. */
    public void updateIsoPath(String isoPath) {
        this.isoPath = isoPath;
    }

    // ---- Markable 구현 -------------------------------------------------

    @Override
    public Long getResourceId() {
        return id;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.OS_ISO;
    }

    @Override
    public Path getResourcePath() {
        return Path.of(isoPath);
    }

    @Override
    public void reissueMarker(String manifestHash, String markerSignature) {
        this.manifestHash = manifestHash;
        this.markerSignature = markerSignature;
    }

    public void recordIntegritySnapshot(com.example.serverprovision.management.bios.vo.IntegrityStatus integrityStatus,
                                        Instant verifiedAt) {
        this.lastIntegrityStatus = integrityStatus;
        this.lastVerifiedAt = verifiedAt;
    }
}
