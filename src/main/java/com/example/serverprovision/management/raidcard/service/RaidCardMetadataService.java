package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardCreateRequest;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardUpdateRequest;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.DuplicateRaidCardException;
import com.example.serverprovision.management.raidcard.exception.IllegalRaidCardStateException;
import com.example.serverprovision.management.raidcard.exception.RaidCardNudgeRequiredException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.PciSubsystemId;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAID 카드 메타 CRUD · 조회 + nudge confirm 의 실제 자원 생성 · 교체 (MA7).
 *
 * <ul>
 *   <li>findById / findAllGrouped — 조회. 응답은 지원 RAID 레벨을 동봉한다(D6 — U4-1-1 의
 *       정의서 작성 폼이 목록 조회만으로 disabled 판정을 만들 수 있어야 한다).</li>
 *   <li>create / update — 메타 쓰기. create 는 충돌 시 nudge 세션 발급.</li>
 *   <li>completePendingCardFromNudge / purgeCardForNudge — nudge confirm 의 실제 생성 · 교체.
 *       {@code RaidCardNudgeService} 가 세션 orchestration 후 본 서비스로 위임(단방향 의존).</li>
 * </ul>
 *
 * <p>lifecycle 상태 전이는 {@code RaidCardLifecycleService}. 본 서비스는 다른 raidcard service 에
 * 의존하지 않는다(leaf). 자식 자원이 없어 BoardModel 의 개수 집계도 없다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaidCardMetadataService {

	// nudge payload attributes 의 키 어휘 — completePendingCardFromNudge 와 buildNudgePayload 가 공유.
	private static final String ATTR_VENDOR = "vendor";
	private static final String ATTR_MODEL_NAME = "modelName";
	private static final String ATTR_LEVELS = "supportedRaidLevels";
	private static final String ATTR_CACHE_GB = "cacheCapacityGb";
	private static final String ATTR_CHIP_FAMILY = "chipFamily";
	private static final String ATTR_PCI = "pciSubsystemId";
	private static final String ATTR_DESCRIPTION = "description";

	private final RaidCardRepository raidCardRepository;
	private final NudgeRegistry nudgeRegistry;

	// ==== 조회 ========================================================

	public RaidCardResponse findById(Long id) {
		return RaidCardResponse.of(RaidCardGuards.requireActiveCard(raidCardRepository, id));
	}

	public List<RaidCardVendorGroupResponse> findAllGrouped(boolean includeDeleted) {
		List<RaidCard> cards = includeDeleted
				? raidCardRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: raidCardRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

		Map<RaidCardVendor, List<RaidCard>> byVendor = cards.stream().collect(
				Collectors.groupingBy(RaidCard::getVendor, LinkedHashMap::new, Collectors.toList())
		);

		return byVendor.entrySet().stream()
				.map(entry -> RaidCardVendorGroupResponse.of(
						entry.getKey(),
						entry.getValue().stream().map(RaidCardResponse::of).toList()
				))
				.toList();
	}

	// ==== 쓰기 연산 ====================================================

	@Transactional
	public Long create(RaidCardCreateRequest request) {
		// 1) "살아 있는"(비삭제 · 비 Deprecated) 동일키 충돌 — fail-fast (D7 유일성 어휘).
		if (raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				request.vendor(), request.modelName())) {
			throw new DuplicateRaidCardException(request.vendor(), request.modelName());
		}

		// 2) soft-deleted / deprecated 후보 → nudge 세션 발급 (MK2 선례).
		List<RaidCard> candidates = collectMetaNudgeCandidates(request.vendor(), request.modelName());
		if (!candidates.isEmpty()) {
			throw new RaidCardNudgeRequiredException(buildNudgePayload(request, candidates));
		}

		return persistNewCard(
				request.vendor(), request.modelName(),
				SupportedRaidLevels.of(request.supportedRaidLevels()),
				CacheCapacity.ofGigabytes(request.cacheCapacityGb()),
				request.chipFamily(),
				parsePci(request.pciSubsystemId()), request.description());
	}

	@Transactional
	public void update(Long id, RaidCardUpdateRequest request) {
		RaidCard card = RaidCardGuards.requireActiveCard(raidCardRepository, id);
		// modelName 이 바뀔 때만 살아 있는 동일키 중복 재검증 (BoardModel 선례 + D7 어휘).
		if (!card.getModelName().equals(request.modelName())
				&& raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
						card.getVendor(), request.modelName())) {
			throw new DuplicateRaidCardException(card.getVendor(), request.modelName());
		}
		card.update(
				request.modelName(),
				SupportedRaidLevels.of(request.supportedRaidLevels()),
				CacheCapacity.ofGigabytes(request.cacheCapacityGb()),
				request.chipFamily(),
				request.description(),
				parsePci(request.pciSubsystemId())
		);
	}

	// ==== nudge confirm 위임 수신 ======================================

	/**
	 * PROCEED — 충돌 후보 보존, 신규 자원만 살아 있는 카드로 등록.
	 * {@code RaidCardNudgeService.proceed / replace} 가 세션 orchestration 후 위임.
	 */
	@Transactional
	public Long completePendingCardFromNudge(NudgeSession session) {
		if (!(session.payload() instanceof IntentMetaNudgePayload payload)) {
			throw new IllegalRaidCardStateException(
					"RAID 카드 nudge 세션은 IntentMetaNudgePayload 만 허용합니다. nudgeId=" + session.nudgeId());
		}
		Map<String, String> attrs = payload.attributes();
		RaidCardVendor vendor = RaidCardVendor.valueOf(attrs.get(ATTR_VENDOR));
		String modelName = attrs.get(ATTR_MODEL_NAME);
		// race — 다른 트랜잭션이 같은 메타로 살아 있는 자원을 만든 경우 (DB 유니크 인덱스가 최종 판정).
		if (raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(vendor, modelName)) {
			throw new DuplicateRaidCardException(vendor, modelName);
		}
		SupportedRaidLevels levels = SupportedRaidLevels.of(
				Arrays.stream(attrs.get(ATTR_LEVELS).split(",")).map(RaidLevel::valueOf).toList());
		String pci = attrs.get(ATTR_PCI);
		return persistNewCard(vendor, modelName, levels,
				CacheCapacity.ofGigabytes(Integer.parseInt(attrs.get(ATTR_CACHE_GB))),
				RaidChipFamily.valueOf(attrs.get(ATTR_CHIP_FAMILY)),
				(pci == null || pci.isBlank()) ? null : PciSubsystemId.parse(pci),
				attrs.get(ATTR_DESCRIPTION));
	}

	@Transactional
	public void purgeCardForNudge(RaidCard target) {
		if (!target.isDeleted() && !target.isDeprecated()) {
			throw new IllegalRaidCardStateException(
					"살아 있는 자원은 nudge replace 대상이 될 수 없습니다. id=" + target.getId());
		}
		raidCardRepository.delete(target);
		log.info(
				"[raidCard] purge for nudge replace : id={}, vendor={}, modelName={}",
				target.getId(), target.getVendor(), target.getModelName()
		);
	}

	// ==== 내부 헬퍼 ====================================================

	private List<RaidCard> collectMetaNudgeCandidates(RaidCardVendor vendor, String modelName) {
		List<RaidCard> softDeleted = raidCardRepository.findAllByVendorAndModelNameAndIsDeletedTrue(vendor, modelName);
		List<RaidCard> deprecated = raidCardRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(vendor, modelName);
		List<RaidCard> merged = new ArrayList<>(softDeleted.size() + deprecated.size());
		merged.addAll(softDeleted);
		merged.addAll(deprecated);
		return merged;
	}

	private NudgeRequiredResponse buildNudgePayload(RaidCardCreateRequest request, List<RaidCard> candidates) {
		NudgeSession session = nudgeRegistry.register(
				NudgeResourceType.RAID_CARD,
				null,
				candidates.stream().map(RaidCard::getId).toList(),
				new IntentMetaNudgePayload(
						Map.of(
								ATTR_VENDOR, request.vendor().name(),
								ATTR_MODEL_NAME, request.modelName(),
								ATTR_LEVELS, request.supportedRaidLevels().stream()
										.map(Enum::name).collect(Collectors.joining(",")),
								ATTR_CACHE_GB, String.valueOf(request.cacheCapacityGb()),
								ATTR_CHIP_FAMILY, request.chipFamily().name(),
								ATTR_PCI, request.pciSubsystemId() != null ? request.pciSubsystemId() : "",
								ATTR_DESCRIPTION, request.description() != null ? request.description() : ""
						)
				)
		);
		// CP5 발견 — 공용 modal 이 (name, version) 을 "name version" 순으로 붙여 렌더하므로, 자원명 기준인
		// displayName()("GIGABYTE CRA3338")과 같은 어순이 되도록 vendor 를 name 자리에 둔다. 역순으로 넘기면
		// 모달만 "CRA3338 GIGABYTE" 가 되어, 영구삭제 자원명 입력 · 삭제 확인 · 휴지통 표기와 어긋난다.
		List<NudgeConflictEntry> entries = candidates.stream()
				.map(c -> new NudgeConflictEntry(
						c.getId(),
						LifecycleStage.of(c.isDeprecated(), c.isDeleted()),
						null,
						c.getVendor().getDisplayName(),
						c.getModelName(),
						Instant.now()
				))
				.toList();
		log.info(
				"[raidCard] nudge required : vendor={}, modelName={}, candidates={}",
				request.vendor(), request.modelName(), candidates.size()
		);
		return NudgeRequiredResponse.of(session.nudgeId(), entries, session.expiresAt());
	}

	/**
	 * 신규 카드 영속. 등록(create)과 nudge 확정(completePendingCardFromNudge) 두 경로가 공유한다.
	 *
	 * <p><b>CP5 발견 결함 수정</b> — 앞선 {@code existsBy...} 검사와 INSERT 사이에는 점검-사용 시점
	 * 사이의 창(TOCTOU)이 있다. 등록 버튼을 빠르게 두 번 누르면 두 요청이 모두 검사를 통과한 뒤 뒤늦은
	 * 쪽이 DB 유니크 인덱스({@code uk_raid_card_active_identity})에 걸리는데, 그때 발생하는
	 * {@link DataIntegrityViolationException} 은 도메인 예외가 아니라 advice 의 계층 탐색에 걸리지 않아
	 * 처리되지 않은 500 으로 샜다(실측 : 동시 요청 2건 중 1건이 {@code http.request.unhandled}).
	 * 전역 폼 가로채기가 승자의 리다이렉트를 렌더하므로 사용자 화면에는 성공으로 보이던, CLAUDE.md 가
	 * 경계하는 silent-500 이다.</p>
	 *
	 * <p>그래서 {@code saveAndFlush} 로 INSERT 시점을 트랜잭션 커밋이 아닌 이 자리로 당겨 위반을 잡고,
	 * 순차 등록이 내는 것과 <b>같은</b> {@link DuplicateRaidCardException}(409 + modelName 필드 오류)으로
	 * 번역한다 — 사용자에게는 동시 · 순차 어느 쪽이든 같은 안내로 수렴한다. 서비스 검사는 친절한 안내를
	 * 위한 것이고 실제 동시성을 막는 것은 DB 불변식이라는 D7 의 두 층 설계가 여기서 완성된다.</p>
	 */
	private Long persistNewCard(RaidCardVendor vendor, String modelName, SupportedRaidLevels levels,
								CacheCapacity cacheCapacity, RaidChipFamily chipFamily,
								PciSubsystemId pciSubsystemId, String description) {
		try {
			RaidCard saved = raidCardRepository.saveAndFlush(RaidCard.builder()
																	 .vendor(vendor)
																	 .modelName(modelName)
																	 .supportedRaidLevels(levels)
																	 .cacheCapacity(cacheCapacity)
																	 .chipFamily(chipFamily)
																	 .pciSubsystemId(pciSubsystemId)
																	 .description(description)
																	 .build());
			return saved.getId();
		} catch (DataIntegrityViolationException e) {
			log.info(
					"[raidCard] 동시 등록 경합 — DB 유일성 인덱스가 거절. vendor={}, modelName={}",
					vendor, modelName
			);
			throw new DuplicateRaidCardException(vendor, modelName);
		}
	}

	/** 선택 입력 문자열 → VO. 비어 있으면 null(미확인). 형식은 요청 @Pattern 이 1차 거른다. */
	private static PciSubsystemId parsePci(String raw) {
		return (raw == null || raw.isBlank()) ? null : PciSubsystemId.parse(raw);
	}
}
