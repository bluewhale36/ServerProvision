package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.global.lifecycle.dto.ResourceListQuery;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardCreateRequest;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardUpdateRequest;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.exception.DuplicateRaidCardException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.PciSubsystemId;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RAID 카드 메타 CRUD · 조회 (MA7).
 *
 * <ul>
 *   <li>findById / findAllGrouped — 조회. 응답은 지원 RAID 레벨을 동봉한다(D6 — U4-1-1 의
 *       정의서 작성 폼이 목록 조회만으로 disabled 판정을 만들 수 있어야 한다).</li>
 *   <li>create / update — 메타 쓰기. 같은 (제조사, 모델명) 이 어느 상태로든 있으면 거절하고 상태별 다음 행동을 안내한다
 *       (HF30-1 — 메인보드 HF26 과 같은 규칙. 종전 중복 확인 창은 Deprecated 카드 옆에 같은 이름의 카드를 하나 더 만들었다).</li>
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

	private final RaidCardRepository raidCardRepository;

	// ==== 조회 ========================================================

	public RaidCardResponse findById(Long id) {
		return RaidCardResponse.of(RaidCardGuards.requireActiveCard(raidCardRepository, id));
	}

	/**
	 * 목록 묶음(S8-3 — 조회 조건 반영). 조건은 카드(C2 항목)에 걸고 묶기 전에 거른다.
	 * 검색 대상 = 제조사 표시명 · 모델명 · PCI Subsystem ID(표시형).
	 */
	public List<RaidCardVendorGroupResponse> findAllGrouped(ResourceListQuery query) {
		List<RaidCard> cards = (query.includeDeleted()
				? raidCardRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: raidCardRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc()).stream()
				.filter(c -> query.matches(c, c.getVendor().getDisplayName(), c.getModelName(),
						c.getPciSubsystemId() == null ? null : c.getPciSubsystemId().toDisplay()))
				.toList();

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

	/**
	 * 등록 — 같은 (제조사, 모델명) 이 활성 · Deprecated · 휴지통 어느 상태로든 있으면 거절하고 상태별 다음 행동을 안내한다(HF30-1).
	 * DB UNIQUE({@code uk_raid_card_vendor_model_name})가 같은 규칙의 안전망이다.
	 */
	@Transactional
	public Long create(RaidCardCreateRequest request) {
		findExisting(request.vendor(), request.modelName()).ifPresent(existing -> {
			throw DuplicateRaidCardException.againstRegistration(existing);
		});
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
		// HF30-1 — 휴지통 · Deprecated 행도 정체를 점유한다(등록과 같은 규칙). 종전처럼 살아 있는 카드만 보면 휴지통 카드의 이름으로
		// 바꿀 때 DB UNIQUE 에 걸려 500 이 된다. 정상 흐름에서는 수정 폼이 renameConflictReason 으로 먼저 칸 오류를 그리므로,
		// 여기는 레이스 · direct POST 의 안전망이다(메인보드 HF26 CP5 D-2 와 같은 구조).
		Optional<DuplicateRaidCardException> conflict = renameConflict(card, request.modelName());
		if (conflict.isPresent()) {
			throw conflict.get();
		}
		card.update(
				request.modelName(),
				SupportedRaidLevels.of(request.supportedRaidLevels()),
				CacheCapacity.ofGigabytes(request.cacheCapacityGb()),
				request.chipFamily(),
				request.description(),
				parsePci(request.pciSubsystemId())
		);
		try {
			// flush 로 UNIQUE 위반을 이 자리에서 드러낸다 — 커밋 시점까지 미루면 번역할 곳 없이 500 으로 샌다(HF30-1 CP5 B12).
			raidCardRepository.flush();
		} catch (DataIntegrityViolationException e) {
			// 두 카드를 동시에 같은 새 이름으로 바꾼 경합 — 위 검사를 함께 통과한 뒤 DB UNIQUE 가 뒤늦은 쪽을 거절했다.
			// 경합 상대는 방금 그 이름이 된 활성 카드라 활성 문구가 맞다.
			log.info("[raidCard] 동시 이름 수정 경합 — DB 유일성 인덱스가 거절. id={}, modelName={}", id, request.modelName());
			throw new DuplicateRaidCardException(card.getVendor(), request.modelName());
		}
	}

	/**
	 * 이름 수정 폼의 사전 판정 — {@link #update} 의 서버 가드와 같은 판정을 칸 오류 문구로 돌려준다. 충돌이 없으면 {@code null}.
	 */
	public String renameConflictReason(Long id, String newModelName) {
		RaidCard card = RaidCardGuards.requireActiveCard(raidCardRepository, id);
		return renameConflict(card, newModelName).map(DuplicateRaidCardException::getMessage).orElse(null);
	}

	// ==== 내부 헬퍼 ====================================================

	/** 이름을 바꿀 때만 같은 (제조사, 모델명) 을 찾는다 — 같은 이름 그대로면 충돌이 아니다. */
	private Optional<DuplicateRaidCardException> renameConflict(RaidCard card, String newModelName) {
		if (card.getModelName().equals(newModelName)) {
			return Optional.empty();
		}
		return findExisting(card.getVendor(), newModelName).map(DuplicateRaidCardException::againstRename);
	}

	/**
	 * 같은 (제조사, 모델명) 의 기존 행 — UNIQUE 이전의 옛 데이터에 같은 키가 여럿 남아 있으면 가장 살아 있는 상태
	 * (활성 → Deprecated → 휴지통)를 기준으로 안내한다.
	 */
	private Optional<RaidCard> findExisting(RaidCardVendor vendor, String modelName) {
		return raidCardRepository.findAllByVendorAndModelName(vendor, modelName).stream()
				.min(Comparator.comparing(c -> LifecycleStage.of(c.isDeprecated(), c.isDeleted())));
	}

	/**
	 * 신규 카드 영속.
	 *
	 * <p><b>CP5 발견 결함 수정</b> — 앞선 {@code existsBy...} 검사와 INSERT 사이에는 점검-사용 시점
	 * 사이의 창(TOCTOU)이 있다. 등록 버튼을 빠르게 두 번 누르면 두 요청이 모두 검사를 통과한 뒤 뒤늦은
	 * 쪽이 DB 유니크 인덱스(HF30-1 부터 {@code uk_raid_card_vendor_model_name})에 걸리는데, 그때 발생하는
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
			throw new DuplicateRaidCardException(vendor, modelName);   // 경합 상대는 방금 생긴 활성 카드 — 활성 문구가 맞다
		}
	}

	/** 선택 입력 문자열 → VO. 비어 있으면 null(미확인). 형식은 요청 @Pattern 이 1차 거른다. */
	private static PciSubsystemId parsePci(String raw) {
		return (raw == null || raw.isBlank()) ? null : PciSubsystemId.parse(raw);
	}
}
