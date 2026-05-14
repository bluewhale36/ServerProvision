package com.example.serverprovision.management.subprogram.entity;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Driver / Utility 통합 엔티티 (MA5).
 * <p>{@link #boardModel} 이 {@code null} 이면 공용 자원 (board 무관). 두 kind 가 동일 라이프사이클을 공유하므로
 * 단일 테이블 + {@code kind} 필드로 분기한다.</p>
 * <p>레이아웃은 IN_TREE 마커 (BIOS/BMC 와 동일).</p>
 *
 * <p>MK2 — {@link LifecycleEntity} 상속으로 lifecycle 4 boolean ({@code is_enabled} / {@code is_deprecated}
 * / {@code is_deleted}) + audit + 가드 메서드를 super 에 위임한다. 자체 toggle/softDelete/restore/deprecate
 * /undeprecate 구현 제거.</p>
 */
@Entity
@Table(name = "subprogram")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Subprogram extends LifecycleEntity implements Markable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private SubprogramKind kind;

    /**
     * {@code null} 이면 공용 자원.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "board_model_id")
    private BoardModel boardModel;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "version", nullable = false, length = 64)
    private String version;

    @Column(name = "tree_root_path", nullable = false, length = 1024)
    private String treeRootPath;

    /**
     * 등록 시점에는 {@code null}. 사용자가 편집 화면에서 명시 입력 (MA5-D5).
     */
    @Column(name = "entrypoint_relative_path", length = 512)
    private String entrypointRelativePath;

    @Column(name = "manifest_hash", nullable = false, length = 64)
    private String manifestHash;

    @Column(name = "marker_signature", length = 64)
    private String markerSignature;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_integrity_status", nullable = false, length = 32)
    @Builder.Default
    private IntegrityStatus lastIntegrityStatus = IntegrityStatus.NOT_VERIFIED;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    public void update(String name, String version, String description, String entrypointRelativePath) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.entrypointRelativePath = blankToNull(entrypointRelativePath);
    }

    public void updateTreeRootPath(String treeRootPath) {
        this.treeRootPath = treeRootPath;
    }

    @Override
    public void reissueMarker(String manifestHash, String markerSignature) {
        this.manifestHash = manifestHash;
        this.markerSignature = markerSignature;
    }

    /** S5-2 — typed-name 검증 + modal 표시 기준. Subprogram 은 name 자체가 식별자. */
    @Override
    public String displayName() {
        return name;
    }

    public void recordIntegritySnapshot(IntegrityStatus integrityStatus, Instant verifiedAt) {
        this.lastIntegrityStatus = integrityStatus == null ? IntegrityStatus.NOT_VERIFIED : integrityStatus;
        this.lastVerifiedAt = verifiedAt;
    }

    public boolean isCommonScope() {
        return boardModel == null;
    }

    public Long getBoardId() {
        return boardModel == null ? null : boardModel.getId();
    }

    @Override
    public Long getResourceId() {
        return id;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.SUBPROGRAM;
    }

    @Override
    public Path getResourcePath() {
        return Path.of(treeRootPath);
    }

    /* ─────────────── LifecycleEntity hooks ─────────────── */

    @Override
    protected Long resourceId() {
        return id;
    }

    @Override
    protected String resourceLabel() {
        // Driver / Utility 어휘를 가드 메시지에 그대로 노출.
        return kind == null ? "Subprogram" : kind.getDisplayName();
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
