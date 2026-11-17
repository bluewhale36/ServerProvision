package com.example.serverprovision.management.raidcard.entity;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.IllegalRaidCardStateException;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.CacheCapacityConverter;
import com.example.serverprovision.management.raidcard.vo.PciSubsystemId;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevelsConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * RAID 카드 — 세팅 정의서가 "어떤 카드를 전제하는가" 를 id 로 참조하는 관리 자원(MA7).
 *
 * <p>{@code BoardModel} 과 같은 부류의 메타 자원 — 디스크 파일이 없어 HMAC 마커를 발급하지 않고,
 * 휴지통 노출을 위한 {@link Markable} 계약만 구현한다({@code ResourceType.RAID_CARD} 는
 * {@code metadata=true} 부류의 세 번째). 부모 · 자식 자원이 없는 루트라 lifecycle cascade 도 없다.</p>
 *
 * <p>지원 RAID 레벨은 등록 시 입력값이다(MA7 D2) — 정의서 작성 화면의 1차 차단이 이 값을 즉시
 * 필요로 하고, 데이터시트에 적혀 있어 사람이 아는 값이기 때문. PCI Subsystem ID 는 선택 입력이며
 * null = '미확인'(정밀 등록 슬라이스가 실측 대조로 채운다 — D3).</p>
 */
@Entity
@Table(name = "raid_card",
		uniqueConstraints = @UniqueConstraint(name = "uk_raid_card_vendor_model_name", columnNames = {"vendor", "model_name"}))   // HF30-1 — 휴지통 · Deprecated 행도 정체를 점유
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class RaidCard extends LifecycleEntity implements Markable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "vendor", nullable = false, length = 32)
	private RaidCardVendor vendor;

	@Column(name = "model_name", nullable = false, length = 128)
	private String modelName;

	@Convert(converter = SupportedRaidLevelsConverter.class)
	@Column(name = "supported_raid_levels", nullable = false, length = 64)
	private SupportedRaidLevels supportedRaidLevels;

	/** 온보드 캐시 용량(GB), 0 = 없음(CP6 개정). {@link #hasCache()} 파생값이 RAID0 최소 디스크 판정의 입력. */
	@Convert(converter = CacheCapacityConverter.class)
	@Column(name = "cache_capacity_gb", nullable = false)
	private CacheCapacity cacheCapacity;

	/** 제어 계열(E3.5-6) — VD 파라미터 지원 판정({@code RaidChipFamily.supportsVdParameters})의 SSOT 입력. */
	@Enumerated(EnumType.STRING)
	@Column(name = "chip_family", nullable = false, length = 16)
	private RaidChipFamily chipFamily;

	@Column(name = "description", length = 1024)
	private String description;

	/** 실물이 아는 값 — null = 아직 미확인(선택 입력 또는 정밀 등록으로 채워진다). */
	@Embedded
	private PciSubsystemId pciSubsystemId;

	// ---- LifecycleEntity 가드 메시지용 -----------------------------------

	@Override
	protected Long resourceId() {
		return this.id;
	}

	@Override
	protected LifecycleEntity parentLifecycle() {
		return null;   // 루트(부모 없음) → effective = own
	}

	@Override
	protected String resourceLabel() {
		return "RAID 카드";
	}

	// ---- 도메인 메서드 -------------------------------------------------

	public void update(String modelName, SupportedRaidLevels supportedRaidLevels,
					   CacheCapacity cacheCapacity, RaidChipFamily chipFamily,
					   String description, PciSubsystemId pciSubsystemId) {
		this.modelName = modelName;
		this.supportedRaidLevels = supportedRaidLevels;
		this.cacheCapacity = cacheCapacity;
		this.chipFamily = chipFamily;
		this.description = description;
		this.pciSubsystemId = pciSubsystemId;
	}

	/**
	 * 관측값으로 확정(E3.5-5-b D4) — 미확인 카드에만 허용한다. 확정값이 있으면 관측으로 덮어쓰지 않는다(정정은 수정 폼).
	 * 화면 · 서비스가 같은 판정({@code RaidCardObservationStatus})으로 먼저 거르므로 여기는 안전망이다.
	 */
	public void confirmObservedPciSubsystemId(PciSubsystemId observed) {
		if (observed == null) {
			throw new IllegalArgumentException("확정할 관측값이 없습니다.");
		}
		if (this.pciSubsystemId != null) {
			throw new IllegalRaidCardStateException("이미 확정된 카드입니다. 정정은 수정에서 해야 합니다.");
		}
		this.pciSubsystemId = observed;
	}

	/** 캐시 보유 여부 — {@code RaidLevel.minimumDisks(cardHasCache)} 판정의 입력 (U4-1-1 계약). */
	public boolean hasCache() {
		return cacheCapacity.isPresent();
	}

	/**
	 * 세팅 정의서가 이 카드로 이 레벨을 만들 수 있는가(HF32) — 칩 계열이 만들 수 있고(R17-2) 카드 등록 정보에도 있어야 한다.
	 * R17-2 이전에 등록한 카드에는 계열이 못 만드는 레벨이 남아 있을 수 있어 등록 정보만으로는 판정하지 않는다.
	 * null = 가능, 아니면 차단 사유 — 정의서 폼의 레벨 잠금과 저장 가드({@code DiskGroupRules})가 이 메서드 하나를 부른다.
	 */
	public String definableLevelBlockReason(RaidLevel level) {
		if (!chipFamily.canCreate(level)) {
			return chipFamily.creatableLevelsHint() + ".";
		}
		return supportedRaidLevels.blockReasonFor(level, definableLevels());
	}

	/**
	 * 정의서가 이 카드로 만들 수 있는 레벨 — 등록 정보 중 칩 계열이 만들 수 있는 것(선언 순). 옛 카드가 계열이 못 만드는 레벨만
	 * 등록했다면 빈 집합일 수 있어 {@link SupportedRaidLevels}(1 개 이상 불변식)가 아니라 집합으로 낸다.
	 */
	public java.util.SortedSet<RaidLevel> definableLevels() {
		java.util.SortedSet<RaidLevel> levels = new java.util.TreeSet<>();
		supportedRaidLevels.asSet().stream().filter(chipFamily::canCreate).forEach(levels::add);
		return java.util.Collections.unmodifiableSortedSet(levels);
	}

	/** 등록 정보에 남은, 칩 계열이 만들 수 없는 레벨(R17-2 이전 등록분) — 카드 상세의 주의 안내 재료. 없으면 빈 집합. */
	public java.util.Set<RaidLevel> uncreatableRegisteredLevels() {
		java.util.Set<RaidLevel> levels = java.util.EnumSet.noneOf(RaidLevel.class);
		supportedRaidLevels.asSet().stream().filter(level -> !chipFamily.canCreate(level)).forEach(levels::add);
		return levels;
	}

	// ==== Markable 구현 (메타 자원) ===================================
	// 디렉토리/파일 없는 메타데이터 — 휴지통 노출용으로 ResourceType / lifecycle 만 제공 (BoardModel 선례).

	@Override
	public Long getResourceId() {
		return this.id;
	}

	@Override
	public ResourceType getResourceType() {
		return ResourceType.RAID_CARD;
	}

	@Override
	public java.nio.file.Path getResourcePath() {
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
	 * typed-name 검증 + modal 표시 기준 자원명 (예: {@code GIGABYTE CRA3338}).
	 */
	@Override
	public String displayName() {
		return vendor.getDisplayName() + " " + modelName;
	}
}
