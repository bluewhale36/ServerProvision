package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUpdateRequest;
import com.example.serverprovision.management.subprogram.dto.response.BoardWithSubprogramListResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramVariantResponse;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramVariantRequest;
import com.example.serverprovision.management.subprogram.entity.SubprogramVariant;
import com.example.serverprovision.management.subprogram.exception.InvalidSubprogramVariantException;
import java.util.ArrayList;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MA5 Subprogram 도메인 read + update 코어. Driver / Utility 통합.
 *
 * <p>R6-3 — fat service 5분할. lifecycle 은 {@link SubprogramLifecycleService}, 등록은
 * {@link SubprogramRegistrationService}, marker 발급은 {@link SubprogramMarkerWriter}, 무결성은
 * {@link SubprogramIntegrityService}, 공유 조회 가드는 {@link SubprogramGuards} 로 분리했다. 본 service 에는
 * 조회(findAllGrouped / findByScope / findSubprogram) + 편집(update) + 뷰 변환(toResponse) 만 잔류한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubprogramService {

	private final SubprogramRepository subprogramRepository;
	private final BoardModelRepository boardModelRepository;
	private final EntrypointPolicyService entrypointPolicyService;
	private final com.example.serverprovision.management.os.repository.OSMetadataRepository osMetadataRepository;

	/* ─────────────────────────── 조회 ─────────────────────────── */

	public List<BoardWithSubprogramListResponse> findAllGrouped(SubprogramKind kind, boolean includeDeleted) {
		// 보드별 활성 보드 + 공용 노드(첫 행 고정).
		List<BoardModel> boards = includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

		List<Subprogram> all = includeDeleted
				? subprogramRepository.findAllByKind(kind)
				: subprogramRepository.findAllByKindAndIsDeletedFalse(kind);

		// boardId(또는 null) → 자원 목록.
		Map<Long, List<Subprogram>> byBoardId = new HashMap<>();
		List<Subprogram> commonItems = new ArrayList<>();
		for (Subprogram sp : all) {
			if (sp.isCommonScope()) {
				commonItems.add(sp);
			} else {
				byBoardId.computeIfAbsent(sp.getBoardId(), k -> new ArrayList<>()).add(sp);
			}
		}

		List<BoardWithSubprogramListResponse> rows = new ArrayList<>();
		rows.add(BoardWithSubprogramListResponse.common(toResponses(commonItems)));

		for (BoardModel board : boards) {
			List<Subprogram> items = byBoardId.getOrDefault(board.getId(), List.of());
			rows.add(new BoardWithSubprogramListResponse(
					board.getId(),
					board.getVendor(),
					board.getVendor().getDisplayName(),
					board.getModelName(),
					board.isDeleted(),
					toResponses(items)
			));
		}
		return rows;
	}

	public BoardWithSubprogramListResponse findByScope(SubprogramKind kind, BoardScope scope, boolean includeDeleted) {
		List<Subprogram> items;
		if (scope.isCommon()) {
			items = subprogramRepository.findByKindAndCommonScope(kind);
		} else {
			// 보드 활성 검증
			BoardModel board = boardModelRepository.findById(scope.boardId())
					.orElseThrow(() -> new BoardModelNotFoundException(scope.boardId()));
			items = subprogramRepository.findByKindAndBoardId(kind, scope.boardId());

			List<Subprogram> filtered = items.stream()
					.filter(s -> includeDeleted || !s.isDeleted())
					.toList();
			return new BoardWithSubprogramListResponse(
					board.getId(),
					board.getVendor(),
					board.getVendor().getDisplayName(),
					board.getModelName(),
					board.isDeleted(),
					toResponses(filtered)
			);
		}
		List<Subprogram> filtered = items.stream()
				.filter(s -> includeDeleted || !s.isDeleted())
				.toList();
		return BoardWithSubprogramListResponse.common(toResponses(filtered));
	}

	public SubprogramResponse findSubprogram(Long subprogramId) {
		return toResponse(SubprogramGuards.requireLive(subprogramRepository, subprogramId));
	}

	/* ─────────────────────────── 편집 ─────────────────────────── */

	@Transactional
	public void update(Long subprogramId, SubprogramUpdateRequest request) {
		Subprogram sp = SubprogramGuards.requireLive(subprogramRepository, subprogramId);
		// version 변경 시 (kind, scope, name, version) 중복 재검사
		if (!sp.getVersion().equals(request.getVersion()) || !sp.getName().equals(request.getName())) {
			Optional<Subprogram> conflict = sp.isCommonScope()
					? subprogramRepository.findActiveByCommonKey(sp.getKind(), request.getName(), request.getVersion())
					: subprogramRepository.findActiveByBoardKey(sp.getKind(), sp.getBoardId(), request.getName(), request.getVersion());
			if (conflict.isPresent() && !conflict.get().getId().equals(subprogramId)) {
				BoardScope scope = sp.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(sp.getBoardId());
				throw new DuplicateSubprogramVersionException(sp.getKind(), scope, request.getName(), request.getVersion());
			}
		}
		// R15-1 — 변형 표 동기화. 구조 · 경로 규칙은 폼이 먼저 걸렀고(checkVariants) 여기서는 안전망(direct POST).
		List<SubprogramVariantRequest> rows = request.variantsOrEmpty();
		Inspection inspection = inspect(sp, rows);
		if (!inspection.findings().isEmpty()) {
			SubprogramVariantRules.Finding first = inspection.findings().getFirst();
			throw new InvalidSubprogramVariantException(first.message(), first.field());
		}
		List<SubprogramVariant> next = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			SubprogramVariantRequest row = rows.get(i);
			next.add(new SubprogramVariant(sp, row.getOsVersion(), inspection.normalized().get(i), row.getArguments(), row.isRebootRequired(), i));
		}
		sp.update(request.getName(), request.getVersion(), request.getDescription(), request.getOsName());
		sp.syncVariants(next);
	}

	/**
	 * 변형 표 검사(R15-1) — 구조 규칙(SubprogramVariantRules)에 경로 보안 정책(EntrypointPolicyService)을 더한 전체 판정.
	 * 폼(BindingResult 행 필드 오류)과 update 안전망이 같은 목록을 본다. 경로 위반은 SSR 폼에서 500 으로 새던 경로(CP5 F-2)라
	 * 정책 예외를 행 필드 오류로 옮긴다 — 정책의 판정은 그대로, 표기만 옮긴다.
	 */
	@Transactional(readOnly = true)
	public List<SubprogramVariantRules.Finding> checkVariants(Long subprogramId, List<SubprogramVariantRequest> rows) {
		return inspect(SubprogramGuards.requireLive(subprogramRepository, subprogramId), rows).findings();
	}

	/** 검사 결과 — 위반 목록과 행별 정규화 진입점(위반 행은 null). 정책 호출은 행당 한 번이다. */
	private record Inspection(List<SubprogramVariantRules.Finding> findings, List<String> normalized) {
	}

	private Inspection inspect(Subprogram sp, List<SubprogramVariantRequest> rows) {
		List<SubprogramVariantRules.Finding> findings = new ArrayList<>(SubprogramVariantRules.check(rows));
		List<String> normalized = new ArrayList<>();
		Path treeRoot = Path.of(sp.getTreeRootPath());
		for (int i = 0; i < rows.size(); i++) {
			String entrypoint = rows.get(i) == null ? null : rows.get(i).getEntrypointRelativePath();
			if (entrypoint == null || entrypoint.isBlank()) {
				normalized.add(null);   // 누락은 구조 규칙이 이미 잡았다
				continue;
			}
			try {
				normalized.add(entrypointPolicyService.validateAndNormalize(treeRoot, entrypoint));
			} catch (com.example.serverprovision.global.security.exception.EntrypointInvalidException e) {
				normalized.add(null);
				findings.add(new SubprogramVariantRules.Finding(i, SubprogramVariantRules.Violation.ENTRYPOINT_PATH, e.getMessage()));
			}
		}
		return new Inspection(findings, normalized);
	}

	/** 수정 폼의 OS 버전 제안(R15-1 D-6) — 등록된 OS 메타의 버전. 입력은 자유 문자열이라 제안일 뿐이다. */
	@Transactional(readOnly = true)
	public List<String> osVersionSuggestions(com.example.serverprovision.management.os.enums.OSName osName) {
		if (osName == null) {
			return List.of();
		}
		return osMetadataRepository.findAllByIsDeletedFalseOrderByOsNameAscCreatedAtDesc().stream()
				.filter(os -> os.getOsName() == osName)
				.map(os -> os.getOsVersion())
				.filter(v -> v != null && !v.isBlank())
				.distinct()
				.sorted(Comparator.reverseOrder())
				.toList();
	}

	/* ─────────────────────────── 뷰 변환 ─────────────────────────── */

	private List<SubprogramResponse> toResponses(List<Subprogram> entities) {
		return entities.stream()
				.sorted(Comparator.comparing(Subprogram::getName).thenComparing(Comparator.comparing(Subprogram::getVersion).reversed()))
				.map(SubprogramService::toResponse)
				.toList();
	}

	private static SubprogramResponse toResponse(Subprogram entity) {
		BoardModel parent = entity.getBoardModel();   // 공용 자원이면 null → capability 전부 false
		return new SubprogramResponse(
				entity.getId(),
				entity.getKind(),
				entity.getKind().getDisplayName(),
				entity.getBoardId(),
				entity.getName(),
				entity.getVersion(),
				entity.getTreeRootPath(),
				entity.getOsName(),
				entity.osLabel(),
				entity.getVariants().stream().map(SubprogramVariantResponse::of).toList(),
				entity.getManifestHash(),
				entity.getFileCount(),
				entity.getTotalBytes(),
				entity.getDescription(),
				entity.getLastIntegrityStatus() != null ? entity.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				entity.isEnabled(),
				entity.isDeleted(),
				entity.isDeprecated(),
				entity.currentStage(),
				// R2-2-1 — 부모(BoardModel) lifecycle capability. 공용 자원(parent=null)은 전부 false. SSOT = BoardModel.blocksChild*().
				parent != null && parent.blocksChildEnable(),
				parent != null && parent.blocksChildUndeprecate(),
				parent != null && parent.blocksChildRestore()
		);
	}
}
