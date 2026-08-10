package com.example.serverprovision.provisioning.usage;

import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcess;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MK4-2 — 자원 사용 깊이 조회의 구현.
 *
 * <p>자원 참조는 정의서와 할당 스냅샷 모두 {@code payload_json} 안에 있고 역방향 인덱스가 없다.
 * 그래서 <b>질의 시점에 한 번 순회해</b> "쓰이는 자원 → 깊이" 표를 만들고 그 표를 조회한다.
 * BIOS 세팅 템플릿처럼 파생 조인 테이블을 두는 선례가 있지만, 그것은 외래 키로 삭제를 막아야 해서
 * 만든 것이고 여기는 읽기 전용 판정이다. 정의서는 사람이 손으로 쓰는 규모라 순회 비용이 스키마 ·
 * 백필 · 쓰기 배선을 늘리는 비용보다 작다.</p>
 *
 * <p>규모가 커져 순회가 부담이 되면 파생 참조 테이블로 옮긴다. 순회가
 * {@link ProcessReferenceInspector} 뒤에 있으므로 호출부를 바꾸지 않고 교체할 수 있다.</p>
 */
@Service
@RequiredArgsConstructor
public class ResourceUsageQueryImpl implements ResourceUsageQuery {

	private final SettingDefinitionRepository definitionRepository;
	private final SettingAssignmentRepository assignmentRepository;
	private final ProvisioningProgressRepository progressRepository;
	private final List<ProcessReferenceInspector> inspectors;

	@Override
	@Transactional(readOnly = true)
	public Map<ResourceKey, ResourceUsageLevel> levelsOf(Collection<ResourceKey> keys) {
		if (keys == null || keys.isEmpty()) {
			return Map.of();
		}
		Set<ResourceKey> wanted = new HashSet<>(keys);
		Map<ResourceKey, ResourceUsageLevel> found = new HashMap<>();

		collectFromDefinitions(wanted, found);
		collectFromAssignments(wanted, found);

		// 요청한 키는 빠짐없이 담아 돌려준다 — 호출부가 없는 키를 널로 다루지 않게 한다.
		Map<ResourceKey, ResourceUsageLevel> result = new HashMap<>();
		for (ResourceKey key : wanted) {
			result.put(key, found.getOrDefault(key, ResourceUsageLevel.NONE));
		}
		return result;
	}

	/** 살아 있는 정의서가 지목한 자원 — 최소 {@code DEFINED}. */
	private void collectFromDefinitions(Set<ResourceKey> wanted, Map<ResourceKey, ResourceUsageLevel> found) {
		Map<Object, ProcessReferenceInspector> byType = inspectorsByType();
		for (SettingDefinition definition : definitionRepository.findAllByIsDeletedFalseOrderByIdAsc()) {
			for (SettingProcess process : definition.getProcesses()) {
				ProcessReferenceInspector inspector = byType.get(process.getProcessType());
				if (inspector == null) continue;
				for (ResourceKey key : inspector.referencedResources(process.getPayload().request())) {
					raise(wanted, found, key, ResourceUsageLevel.DEFINED);
				}
			}
		}
	}

	/**
	 * 서버에 걸려 있는 할당이 지목한 자원 — 소비 전이면 {@code ASSIGNED}, 소비됐고 프로비저닝이
	 * 아직 끝나지 않았으면 {@code RUNNING}.
	 *
	 * <p>대체된 할당({@code supersededAt} 존재)은 세지 않는다. 그 스냅샷은 더 이상 그 서버가 따르는
	 * 계약이 아니다.</p>
	 */
	private void collectFromAssignments(Set<ResourceKey> wanted, Map<ResourceKey, ResourceUsageLevel> found) {
		List<SettingAssignment> active = assignmentRepository.findBySupersededAtIsNull();
		if (active.isEmpty()) return;

		Set<UUID> inFlight = inFlightGuestServerIds(active);
		Map<Object, ProcessReferenceInspector> byType = inspectorsByType();

		for (SettingAssignment assignment : active) {
			boolean running = assignment.getConsumedAt() != null
					&& inFlight.contains(assignment.getGuestServer().getId());
			ResourceUsageLevel level = running ? ResourceUsageLevel.RUNNING : ResourceUsageLevel.ASSIGNED;
			for (AssignedProcess process : assignment.getProcesses()) {
				ProcessReferenceInspector inspector = byType.get(process.getProcessType());
				if (inspector == null) continue;
				for (ResourceKey key : inspector.referencedResources(process.getPayload().request())) {
					raise(wanted, found, key, level);
				}
			}
		}
	}

	/**
	 * 소비된 할당들 중 프로비저닝이 아직 진행 중인 서버. 완료됐거나 실패한 것은 제외한다 —
	 * 끝난 프로비저닝은 그 자원을 지금 쓰고 있지 않다.
	 */
	private Set<UUID> inFlightGuestServerIds(List<SettingAssignment> active) {
		List<UUID> consumed = active.stream()
				.filter(assignment -> assignment.getConsumedAt() != null)
				.map(assignment -> assignment.getGuestServer().getId())
				.distinct()
				.toList();
		if (consumed.isEmpty()) return Set.of();
		return progressRepository.findAllByGuestServer_IdIn(consumed).stream()
				.filter(progress -> !progress.isCompleted() && !progress.isFailed())
				.map(progress -> progress.getGuestServer().getId())
				.collect(Collectors.toSet());
	}

	/** 관심 있는 자원만, 더 깊은 사용으로만 올린다. */
	private static void raise(Set<ResourceKey> wanted, Map<ResourceKey, ResourceUsageLevel> found,
			ResourceKey key, ResourceUsageLevel level) {
		if (key == null || key.resourceId() == null || !wanted.contains(key)) return;
		found.merge(key, level, ResourceUsageLevel::deeper);
	}

	private Map<Object, ProcessReferenceInspector> inspectorsByType() {
		return inspectors.stream().collect(Collectors.toUnmodifiableMap(
				ProcessReferenceInspector::target, Function.identity()));
	}

	/** 진단 · 테스트용 — 등록된 검사기 수. */
	List<ProcessReferenceInspector> registeredInspectors() {
		return new ArrayList<>(inspectors);
	}
}
