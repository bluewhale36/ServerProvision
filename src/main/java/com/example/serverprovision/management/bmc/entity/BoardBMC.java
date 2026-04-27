package com.example.serverprovision.management.bmc.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.board.entity.BoardModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

/**
 * 메인보드 모델에 귀속되는 BMC 펌웨어 번들.
 * 실제 파일들은 {@code treeRootPath} 하위에 전개되며, {@code entrypointRelativePath} 가
 * 실행 대상 파일을 가리킨다.
 */
@Entity
@Table(name = "board_bmc")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoardBMC extends BaseTimeEntity implements Markable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "compatible_model_id", nullable = false)
    private BoardModel boardModel;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "version", nullable = false, length = 64)
    private String version;

    @Column(name = "firmware_path", nullable = false, length = 1024)
    private String treeRootPath;

    /**
     * legacy schema compatibility.
     * 기존 board_bmc.file_path 가 아직 NOT NULL 이라 현재 트리 루트 경로를 함께 미러링한다.
     */
    @Column(name = "file_path", nullable = false, length = 255)
    private String legacyFilePath;

    /**
     * legacy/new mixed schema compatibility.
     * 실테이블에 compatible_model_id 와 board_model_id 가 동시에 NOT NULL 이라 둘 다 채운다.
     */
    @Column(name = "board_model_id", nullable = false)
    private Long boardModelIdMirror;

    @Column(name = "entrypoint_relative_path", nullable = false, length = 512)
    private String entrypointRelativePath;

    @Column(name = "manifest_hash", nullable = false, length = 64)
    private String manifestHash;

    @Column(name = "marker_signature", length = 64)
    private String markerSignature;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_integrity_status", nullable = false, length = 32)
    @Builder.Default
    private com.example.serverprovision.management.bios.vo.IntegrityStatus lastIntegrityStatus =
            com.example.serverprovision.management.bios.vo.IntegrityStatus.NOT_VERIFIED;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    public void toggleEnabled() {
        this.isEnabled = !this.isEnabled;
    }

    public void softDelete() {
        this.isDeleted = true;
    }

    public void restore() {
        this.isDeleted = false;
    }

    public void update(String name, String version, String description) {
        this.name = name;
        this.version = version;
        this.description = description;
    }

    public void updateTreeRootPath(String treeRootPath) {
        this.treeRootPath = treeRootPath;
        this.legacyFilePath = treeRootPath;
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

    @Override
    public Long getResourceId() {
        return id;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.BMC_FIRMWARE;
    }

    @Override
    public Path getResourcePath() {
        return Path.of(treeRootPath);
    }
}
