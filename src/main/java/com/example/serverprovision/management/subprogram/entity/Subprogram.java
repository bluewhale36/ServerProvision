package com.example.serverprovision.management.subprogram.entity;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

	/** 적용 OS(R15-1 D-1) — null 이면 OS 무관. 마커 attribute 에는 넣지 않는다(기존 서명 보존 · 설명과 같은 메타 성격). */
	@Enumerated(EnumType.STRING)
	@Column(name = "os_name", length = 32)
	private OSName osName;

	/** 버전별 변형(R15-1 D-2) — 진입점의 유일한 자리. 수정 폼이 표 전체를 제출하므로 교체로 다룬다. */
	@OneToMany(mappedBy = "subprogram", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	@Builder.Default
	private List<SubprogramVariant> variants = new ArrayList<>();

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

	public void update(String name, String version, String description, OSName osName) {
		this.name = name;
		this.version = version;
		this.description = description;
		this.osName = osName;
	}

	/**
	 * 변형 표 동기화 — 폼이 전체를 제출한다(D-5). 같은 버전 키의 행은 제자리에서 갱신하고, 없는 키는 더하고, 빠진 키는 뺀다.
	 * clear + addAll 로 갈아 끼우면 Hibernate 가 INSERT 를 orphan DELETE 보다 먼저 flush 해 UNIQUE(subprogram_id, os_version)
	 * 에 걸린다(CP5 F-1) — 그래서 키 단위로 맞추며, 유지되는 행의 id 도 그대로다.
	 */
	public void syncVariants(List<SubprogramVariant> desired) {
		java.util.Map<String, SubprogramVariant> existing = new java.util.HashMap<>();
		for (SubprogramVariant v : variants) {
			existing.put(v.versionKey(), v);
		}
		List<SubprogramVariant> next = new ArrayList<>();
		for (SubprogramVariant d : desired) {
			SubprogramVariant kept = existing.remove(d.versionKey());
			if (kept != null) {
				kept.updateFrom(d);
				next.add(kept);
			} else {
				next.add(d);
			}
		}
		variants.removeAll(existing.values());   // orphanRemoval → DELETE
		variants.removeAll(next);
		variants.addAll(next);                   // 제출 순서로 재배열(sortOrder 는 각 행이 이미 품고 있다)
	}

	public String osLabel() {
		return osName == null ? "OS 무관" : osName.getDisplayName();
	}

	public void updateTreeRootPath(String treeRootPath) {
		this.treeRootPath = treeRootPath;
	}

	@Override
	public void reissueMarker(String manifestHash, String markerSignature) {
		this.manifestHash = manifestHash;
		this.markerSignature = markerSignature;
	}

	/**
	 * S5-2 — typed-name 검증 + modal 표시 기준. Subprogram 은 name 자체가 식별자.
	 */
	@Override
	public String displayName() {
		return name;
	}

	/**
	 * S5-2-3-1 — 휴지통 위계 시각화용 부모 노출. null (공용 자원) 이면 부모 없음.
	 */
	@Override
	public java.util.Optional<com.example.serverprovision.global.marker.Markable> getParentMarkable() {
		return java.util.Optional.ofNullable(boardModel);
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
	protected LifecycleEntity parentLifecycle() {
		return this.boardModel;   // R4-1 — board-scoped 는 부모 상속, 공용(boardModel=null)은 effective=own
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
