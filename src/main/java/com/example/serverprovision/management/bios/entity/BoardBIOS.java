package com.example.serverprovision.management.bios.entity;

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
 * 메인보드 모델에 귀속되는 BIOS "번들". 실제 파일들은 {@code treeRootPath} 가 가리키는 서버 디렉토리 하위에 전개된다.
 * <p>BIOS 업데이트는 단일 바이너리가 아니라 여러 파일이 한 세트를 이룬다 (RBU 이미지 · AFU 유틸리티 · .nsh 스크립트 · 서명 등).
 * 따라서 한 레코드 = 한 번들 = 한 디렉토리 트리이며, {@code entrypointRelativePath} 로 실행 파일 위치를 특정한다.</p>
 *
 * <h3>무결성 계층</h3>
 * <ul>
 *   <li>{@code manifestHash} : 트리 내 모든 파일(.provision.json 제외)의 (상대경로 || per-file SHA-256) 정렬 concat → SHA-256.</li>
 *   <li>{@code markerSignature} : 위 해시를 포함한 marker 컨텐츠를 서버 측 HMAC 키로 서명한 값.</li>
 *   <li>{@code .provision.json} 파일 자체는 트리 루트에 저장되며, 이 두 값을 담고 있다 — 엔티티와 중복이지만 파일시스템만 보고도 무결성 확인이 가능하게 하기 위함.</li>
 * </ul>
 *
 * <p>{@code (boardModel, version)} 범위의 유일성은 DB unique 가 아닌 Service 레벨 검증으로 강제 —
 * 휴지통 보기(soft delete) 에서 같은 version 이 살아있는 경우를 허용하기 위한 선택. 동일 (board, version) 재업로드는
 * 활성 레코드 있으면 409 거절 · soft-deleted 레코드만 있으면 해당 트리·marker 물리 삭제 후 신규 등록 진행.</p>
 *
 * <p>{@code entrypointRelativePath} 는 <b>실행 파일 자체만</b> 담는다 (예: {@code "f.nsh"}).
 * 실행 시 파라미터 등의 실행 로직은 향후 Execution 도메인(E 단계) 이 관장한다.</p>
 */
@Entity
@Table(name = "board_bios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoardBIOS extends BaseTimeEntity implements Markable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "board_model_id", nullable = false)
    private BoardModel boardModel;

    /** 업체/제품 계열명 등 관리자가 식별에 쓰는 레이블. */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** BIOS 버전 문자열 (예: "2.03", "F13"). BoardModel 범위 유일성은 Service 가 검증. */
    @Column(name = "version", nullable = false, length = 64)
    private String version;

    /** 번들 트리 루트 절대경로. 이 경로 <em>하위</em> 에 전개된 파일들이 실제 BIOS 번들 내용. */
    @Column(name = "tree_root_path", nullable = false, length = 1024)
    private String treeRootPath;

    /**
     * 진입점의 {@code treeRootPath} 기준 상대경로. 예: {@code "f.nsh"}, {@code "subdir/update.nsh"}.
     * 실행 파일 자체만 보관하며, 파라미터 · 호출 방식 등 실행 로직은 향후 Execution 도메인에서 관장.
     */
    @Column(name = "entrypoint_relative_path", nullable = false, length = 512)
    private String entrypointRelativePath;

    /** 트리 내용 전체의 SHA-256 매니페스트 해시 (hex 64자). marker 와 대조해 무결성 확인. */
    @Column(name = "manifest_hash", nullable = false, length = 64)
    private String manifestHash;

    /**
     * marker 컨텐츠의 HMAC-SHA256 서명 (hex 64자). 외부 이식/변조 탐지.
     * <p>등록 과정상 2-phase save 필요(저장 후 id 가 정해져야 signature 에 포함 가능)하여 nullable.
     * Service 레벨에서 "활성 레코드는 반드시 signature 가 채워짐" 을 불변식으로 보장한다.</p>
     */
    @Column(name = "marker_signature", length = 64)
    private String markerSignature;

    /** 마지막 무결성 검증 스냅샷. 목록 렌더 시 해시를 재계산하지 않고 최근 결과를 보여주기 위함. */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_integrity_status", nullable = false, length = 32)
    @Builder.Default
    private com.example.serverprovision.management.bios.vo.IntegrityStatus lastIntegrityStatus =
            com.example.serverprovision.management.bios.vo.IntegrityStatus.NOT_VERIFIED;

    /** 마지막 검증 수행 시각. 미검증 자원은 null. */
    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    /** 트리 내 파일 개수 (marker 파일 제외). 목록 뷰 표시용. */
    @Column(name = "file_count", nullable = false)
    private int fileCount;

    /** 트리 내 파일 총 바이트 (marker 파일 제외). 목록 뷰 표시용. */
    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

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

    /**
     * 메타데이터 수정. 트리·진입점·해시·서명은 여기서 변경하지 않는다 —
     * 번들 교체는 soft delete 후 새 번들 업로드 흐름을 통해서만 가능.
     */
    public void update(String name, String version, String description) {
        this.name = name;
        this.version = version;
        this.description = description;
    }

    /**
     * marker 재발급 흐름에서 manifestHash · signature 만 갱신. Entity 경계 내부에서만 허용.
     * 파일 내용이 변조된 상태에서 호출되면 그 상태가 "원본" 으로 굳어지므로 반드시 관리자 명시적 액션에서만 호출.
     */
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

    /** PATH_DRIFT 자동 적용 시 BoardBiosMarkableScanner 가 호출 — DB 의 treeRootPath 만 갱신. */
    public void updateTreeRootPath(String treeRootPath) {
        this.treeRootPath = treeRootPath;
    }

    // ---- Markable 구현 -------------------------------------------------

    @Override
    public Long getResourceId() {
        return id;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.BIOS_BUNDLE;
    }

    @Override
    public Path getResourcePath() {
        return Path.of(treeRootPath);
    }
}
