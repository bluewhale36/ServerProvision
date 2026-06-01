package com.example.serverprovision.management.os.entity;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.os.enums.OSName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * OS 메타데이터 — (OSName, osVersion) 조합의 유일한 논리적 단위.
 *
 * <p>MK2 — {@link LifecycleEntity} 상속으로 lifecycle 4 boolean(+audit) 을 super 가 보유한다.
 * 자체 lifecycle 메서드는 super 가 처리하지만, OSMetadata 만의 cascade 정책 (자식 활성 ISO 도 같이
 * soft-delete) 을 위해 {@link #softDelete()} 만 override 한다. {@code restore()} 는 OSMetadata 자기 자신
 * 만 살리고, 자식 ISO 는 사용자 명시 액션으로 개별 복구해야 한다 — 과거 삭제 시점이 달라 일괄 복구는
 * 의도치 않은 재노출을 부를 수 있기 때문.</p>
 */
@Entity
@Table(name = "os_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class OSMetadata extends LifecycleEntity implements Markable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "os_name", nullable = false, length = 32)
	private OSName osName;

	@Column(name = "os_version", nullable = false, length = 64)
	private String osVersion;

	@Column(name = "description", length = 1024)
	private String description;

	// 양방향 매핑 : ISO 가 FK 소유자. 조회 편의를 위해 연관 리스트를 유지한다.
	// cascade/orphanRemoval 을 쓰지 않는 이유 — ISO 생성/삭제는 Service 가 명시적으로 Repository 호출로 수행한다.
	// @Builder.Default — SuperBuilder 가 sub-class 필드의 기본값을 build 시점에 적용하도록 보장.
	@OneToMany(mappedBy = "osMetadata", fetch = FetchType.LAZY)
	@OrderBy("id ASC")
	@Builder.Default
	private List<ISO> isos = new ArrayList<>();

	// A1-1 : 설치 환경·패키지 그룹은 OSMetadata 범위에서 관리된다. 양방향 매핑은 조회 편의용이며 cascade 없음.
	@OneToMany(mappedBy = "osMetadata", fetch = FetchType.LAZY)
	@OrderBy("id ASC")
	@Builder.Default
	private List<OSEnvironment> environments = new ArrayList<>();

	@OneToMany(mappedBy = "osMetadata", fetch = FetchType.LAZY)
	@OrderBy("id ASC")
	@Builder.Default
	private List<OSPackageGroup> packageGroups = new ArrayList<>();

	// ---- LifecycleEntity 가드 메시지용 -----------------------------------

	@Override
	protected Long resourceId() {
		return this.id;
	}

	@Override
	protected LifecycleEntity parentLifecycle() {
		return null;   // R4-1 — 루트(부모 없음) → effective = own
	}

	@Override
	protected String resourceLabel() {
		return "OS 버전";
	}

	// ---- 도메인 메서드 -------------------------------------------------

	/**
	 * S5-2-3 정합화 : 자식 ISO 동반 cascade soft-delete 책임을 Service 로 응집.
	 * <p>이전엔 entity 가 자식 ISO.softDelete() 를 직접 호출했으나, 그 경로는
	 * trashLifecycleService 우회로 ISO 들을 ghost (is_deleted=true + trashed_at=null) 로 만들었다.
	 * 본 entity 는 자기 lifecycle 만 책임지고, OSMetadataService.softDelete 가 자식 trash 이동을 수행.</p>
	 */

	/**
	 * R1-2 — OSMetadata 의 수정 가능 채널은 description 만. osName / osVersion 은 불변 도메인 정책.
	 * 변경이 필요한 경우 자원을 삭제 후 다시 생성한다.
	 */
	public void update(String description) {
		this.description = description;
	}

	// ==== Markable 구현 (메타 자원) ===================================
	// OSMetadata 는 디렉토리/파일 없는 메타데이터 — 마커 발급/검증 흐름 미적용.
	// 휴지통 페이지 노출용으로 ResourceType / lifecycle 메타만 제공.

	@Override
	public Long getResourceId() {
		return this.id;
	}

	@Override
	public ResourceType getResourceType() {
		return ResourceType.OS_IMAGE;
	}

	@Override
	public Path getResourcePath() {
		return null;
	}

	@Override
	public String getManifestHash() {
		return null;
	}

	@Override
	public String getMarkerSignature() {
		return null;
	}

	@Override
	public void reissueMarker(String manifestHash, String markerSignature) {
		// 메타 자원 — no-op.
	}

	/**
	 * S5-2 — typed-name 검증 + modal 표시 기준 자원명.
	 */
	@Override
	public String displayName() {
		return osName.getDisplayName() + " " + osVersion;
	}
}
