package com.example.serverprovision.management.bmc.entity;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.board.entity.BoardModel;
import jakarta.persistence.*;
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
 * 메인보드 모델에 귀속되는 BMC 펌웨어 번들.
 * 실제 파일들은 {@code treeRootPath} 하위에 전개되며, {@code entrypointRelativePath} 가
 * 실행 대상 파일을 가리킨다.
 *
 * <p>MK2 — {@link LifecycleEntity} 상속으로 lifecycle 4 boolean ({@code is_enabled} /
 * {@code is_deprecated} / {@code is_deleted}) 과 {@code deprecated_at} / {@code created_at} /
 * {@code updated_at} 및 lifecycle 메서드 (가드 포함) 일체를 super 가 보유. 본 sub-class 는
 * 도메인 메서드 + Markable 구현 + 자체 컬럼만 가진다.</p>
 *
 * <p><b>legacy schema 호환</b> — {@code file_path} / {@code board_model_id} 컬럼이 NOT NULL 로 잔존하므로
 * {@link #legacyFilePath} 와 {@link #boardModelIdMirror} 필드로 미러링한다. 신규 코드 경로에서는
 * {@link #getTreeRootPath()} 와 {@link #getBoardModel()} 만 의미를 가진다.</p>
 */
@Entity
@Table(name = "board_bmc")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class BoardBMC extends LifecycleEntity implements Markable {

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

	// ---- 도메인 메서드 (lifecycle 메서드는 LifecycleEntity 가 처리) -------

	@Override
	protected Long resourceId() {
		return this.id;
	}

	@Override
	protected LifecycleEntity parentLifecycle() {
		return this.boardModel;   // R4-1 — 부모 메인보드 effective 를 상속
	}

	@Override
	protected String resourceLabel() {
		return "BMC";
	}

	public void update(String name, String version, String description) {
		this.name = name;
		this.version = version;
		this.description = description;
	}

	/**
	 * PATH_DRIFT 자동 적용 시 호출 — DB 의 treeRootPath 와 legacy mirror 를 함께 갱신.
	 */
	public void updateTreeRootPath(String treeRootPath) {
		this.treeRootPath = treeRootPath;
		this.legacyFilePath = treeRootPath;
	}

	@Override
	public void reissueMarker(String manifestHash, String markerSignature) {
		this.manifestHash = manifestHash;
		this.markerSignature = markerSignature;
	}

	/**
	 * S5-2 — typed-name 검증 + modal 표시 기준. BMC 는 name 자체가 식별자.
	 */
	@Override
	public String displayName() {
		return name;
	}

	/**
	 * S5-2-3-1 — 휴지통 위계 시각화용 부모 노출.
	 */
	@Override
	public java.util.Optional<com.example.serverprovision.global.marker.Markable> getParentMarkable() {
		return java.util.Optional.ofNullable(boardModel);
	}

	public void recordIntegritySnapshot(
			com.example.serverprovision.management.bios.vo.IntegrityStatus integrityStatus,
			Instant verifiedAt
	) {
		this.lastIntegrityStatus = integrityStatus;
		this.lastVerifiedAt = verifiedAt;
	}

	// ---- Markable 구현 -------------------------------------------------

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
