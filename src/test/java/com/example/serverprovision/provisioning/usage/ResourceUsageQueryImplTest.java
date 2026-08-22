package com.example.serverprovision.provisioning.usage;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcessSnapshot;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspector;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * MK4-2 — 자원 사용 깊이 판정. 네 수준이 각각 어떤 상태에서 나오는지를 고정한다.
 *
 * <p>참조 추출은 {@link ProcessReferenceInspector} 뒤에 있으므로 여기서는 그 경계를 가짜로 두고
 * <b>깊이를 어떻게 정하는가</b>만 본다 — 대체된 할당을 세지 않는 것, 소비됐어도 프로비저닝이 끝났으면
 * 진행 중으로 치지 않는 것, 한 자원이 여러 곳에 걸리면 가장 깊은 쪽을 대표로 삼는 것이다.</p>
 */
class ResourceUsageQueryImplTest {

	private static final ResourceKey ISO = new ResourceKey(ResourceType.OS_ISO, 1L);
	private static final ResourceKey BIOS = new ResourceKey(ResourceType.BIOS_BUNDLE, 2L);

	private SettingDefinitionRepository definitionRepository;
	private SettingAssignmentSnapshotRepository assignmentRepository;
	private ProvisioningProgressRepository progressRepository;
	private ResourceUsageQueryImpl query;

	/** 어떤 단계든 지정된 자원을 참조한다고 답하는 가짜 검사기. */
	private static ProcessReferenceInspector inspectorReturning(List<ResourceKey> keys) {
		return new ProcessReferenceInspector() {
			@Override
			public SettingProcessType target() {
				return SettingProcessType.OS_INSTALLATION;
			}

			@Override
			public void validateReferences(AbstractProcessRequest process, ProcessValidationContext context) {
			}

			@Override
			public List<String> describeDeprecatedReferences(AbstractProcessRequest process) {
				return List.of();
			}

			@Override
			public List<ResourceKey> referencedResources(AbstractProcessRequest process) {
				return keys;
			}
		};
	}

	private void wire(List<ResourceKey> referenced) {
		query = new ResourceUsageQueryImpl(definitionRepository, assignmentRepository,
				progressRepository, List.of(inspectorReturning(referenced)));
	}

	private static SettingDefinition definitionWithProcess() {
		SettingDefinition definition = Mockito.mock(SettingDefinition.class);
		SettingProcess process = Mockito.mock(SettingProcess.class);
		ProcessPayload payload = Mockito.mock(ProcessPayload.class);
		when(process.getProcessType()).thenReturn(SettingProcessType.OS_INSTALLATION);
		when(process.getPayload()).thenReturn(payload);
		when(payload.request()).thenReturn(null);
		when(definition.getProcesses()).thenReturn(List.of(process));
		return definition;
	}

	private static SettingAssignmentSnapshot assignment(UUID guestId, LocalDateTime consumedAt) {
		SettingAssignmentSnapshot assignment = Mockito.mock(SettingAssignmentSnapshot.class);
		GuestServer guest = Mockito.mock(GuestServer.class);
		AssignedProcessSnapshot process = Mockito.mock(AssignedProcessSnapshot.class);
		ProcessPayload payload = Mockito.mock(ProcessPayload.class);
		when(guest.getId()).thenReturn(guestId);
		when(assignment.getGuestServer()).thenReturn(guest);
		when(assignment.getConsumedAt()).thenReturn(consumedAt);
		when(process.getProcessType()).thenReturn(SettingProcessType.OS_INSTALLATION);
		when(process.getPayload()).thenReturn(payload);
		when(payload.request()).thenReturn(null);
		when(assignment.getProcesses()).thenReturn(List.of(process));
		return assignment;
	}

	private static ProvisioningProgress progress(UUID guestId, boolean completed, boolean failed) {
		ProvisioningProgress progress = Mockito.mock(ProvisioningProgress.class);
		GuestServer guest = Mockito.mock(GuestServer.class);
		when(guest.getId()).thenReturn(guestId);
		when(progress.getGuestServer()).thenReturn(guest);
		when(progress.isCompleted()).thenReturn(completed);
		when(progress.isFailed()).thenReturn(failed);
		return progress;
	}

	@BeforeEach
	void setUp() {
		definitionRepository = Mockito.mock(SettingDefinitionRepository.class);
		assignmentRepository = Mockito.mock(SettingAssignmentSnapshotRepository.class);
		progressRepository = Mockito.mock(ProvisioningProgressRepository.class);
		when(definitionRepository.findAllByIsDeletedFalseOrderByIdAsc()).thenReturn(List.of());
		when(assignmentRepository.findBySupersededAtIsNull()).thenReturn(List.of());
		when(progressRepository.findAllByGuestServer_IdIn(anyList())).thenReturn(List.of());
	}

	@Test
	@DisplayName("미사용 — 어느 정의서도 지목하지 않으면 NONE 이고, 요청한 키는 모두 담겨 온다")
	void noneWhenNobodyReferences() {
		wire(List.of());

		Map<ResourceKey, ResourceUsageLevel> levels = query.levelsOf(List.of(ISO, BIOS));

		assertThat(levels).containsOnlyKeys(ISO, BIOS);
		assertThat(levels.get(ISO)).isEqualTo(ResourceUsageLevel.NONE);
		assertThat(levels.get(BIOS)).isEqualTo(ResourceUsageLevel.NONE);
	}

	@Test
	@DisplayName("정의서 지정 — 살아 있는 정의서가 지목하면 DEFINED")
	void definedWhenDefinitionReferences() {
		SettingDefinition definition = definitionWithProcess();
		when(definitionRepository.findAllByIsDeletedFalseOrderByIdAsc()).thenReturn(List.of(definition));
		wire(List.of(ISO));

		assertThat(query.levelsOf(List.of(ISO)).get(ISO)).isEqualTo(ResourceUsageLevel.DEFINED);
	}

	@Test
	@DisplayName("서버 할당 — 활성 할당이 지목했고 아직 소비 전이면 ASSIGNED")
	void assignedWhenActiveAssignmentNotConsumed() {
		SettingAssignmentSnapshot active = assignment(UUID.randomUUID(), null);
		when(assignmentRepository.findBySupersededAtIsNull()).thenReturn(List.of(active));
		wire(List.of(ISO));

		assertThat(query.levelsOf(List.of(ISO)).get(ISO)).isEqualTo(ResourceUsageLevel.ASSIGNED);
	}

	@Test
	@DisplayName("진행 중 — 소비됐고 프로비저닝이 끝나지 않았으면 RUNNING")
	void runningWhenConsumedAndStillInFlight() {
		UUID guest = UUID.randomUUID();
		SettingAssignmentSnapshot active = assignment(guest, LocalDateTime.now());
		ProvisioningProgress inFlight = progress(guest, false, false);
		when(assignmentRepository.findBySupersededAtIsNull()).thenReturn(List.of(active));
		when(progressRepository.findAllByGuestServer_IdIn(anyList())).thenReturn(List.of(inFlight));
		wire(List.of(ISO));

		assertThat(query.levelsOf(List.of(ISO)).get(ISO)).isEqualTo(ResourceUsageLevel.RUNNING);
	}

	@Test
	@DisplayName("끝난 프로비저닝은 진행 중이 아니다 — 완료 · 실패는 ASSIGNED 로 남는다")
	void finishedProvisioningIsNotRunning() {
		UUID completedGuest = UUID.randomUUID();
		SettingAssignmentSnapshot active = assignment(completedGuest, LocalDateTime.now());
		ProvisioningProgress done = progress(completedGuest, true, false);
		when(assignmentRepository.findBySupersededAtIsNull()).thenReturn(List.of(active));
		when(progressRepository.findAllByGuestServer_IdIn(anyList())).thenReturn(List.of(done));
		wire(List.of(ISO));

		assertThat(query.levelsOf(List.of(ISO)).get(ISO)).isEqualTo(ResourceUsageLevel.ASSIGNED);
	}

	@Test
	@DisplayName("대체된 할당은 세지 않는다 — 조회 자체가 활성 스냅샷만 가져온다")
	void supersededAssignmentIsNotCounted() {
		// findBySupersededAtIsNull 이 빈 목록을 돌려주는 상태 = 활성 할당 없음.
		SettingDefinition definition = definitionWithProcess();
		when(definitionRepository.findAllByIsDeletedFalseOrderByIdAsc()).thenReturn(List.of(definition));
		wire(List.of(ISO));

		// 정의서 지정까지만 올라가고 서버 할당으로는 올라가지 않는다.
		assertThat(query.levelsOf(List.of(ISO)).get(ISO)).isEqualTo(ResourceUsageLevel.DEFINED);
	}

	@Test
	@DisplayName("여러 곳에 걸리면 가장 깊은 사용이 대표값이 된다")
	void deepestWins() {
		UUID guest = UUID.randomUUID();
		SettingDefinition definition = definitionWithProcess();
		SettingAssignmentSnapshot active = assignment(guest, LocalDateTime.now());
		ProvisioningProgress inFlight = progress(guest, false, false);
		when(definitionRepository.findAllByIsDeletedFalseOrderByIdAsc()).thenReturn(List.of(definition));
		when(assignmentRepository.findBySupersededAtIsNull()).thenReturn(List.of(active));
		when(progressRepository.findAllByGuestServer_IdIn(anyList())).thenReturn(List.of(inFlight));
		wire(List.of(ISO));

		assertThat(query.levelsOf(List.of(ISO)).get(ISO)).isEqualTo(ResourceUsageLevel.RUNNING);
	}

	@Test
	@DisplayName("빈 요청은 빈 결과 — 불필요한 순회를 하지 않는다")
	void emptyRequestShortCircuits() {
		wire(List.of(ISO));

		assertThat(query.levelsOf(List.of())).isEmpty();
		Mockito.verify(definitionRepository, Mockito.never()).findAllByIsDeletedFalseOrderByIdAsc();
	}

	@Test
	@DisplayName("관심 밖 자원은 결과에 담기지 않는다")
	void unrelatedResourcesAreIgnored() {
		SettingDefinition definition = definitionWithProcess();
		when(definitionRepository.findAllByIsDeletedFalseOrderByIdAsc()).thenReturn(List.of(definition));
		wire(List.of(BIOS));

		Map<ResourceKey, ResourceUsageLevel> levels = query.levelsOf(List.of(ISO));

		assertThat(levels).containsOnlyKeys(ISO);
		assertThat(levels.get(ISO)).isEqualTo(ResourceUsageLevel.NONE);
	}
}
