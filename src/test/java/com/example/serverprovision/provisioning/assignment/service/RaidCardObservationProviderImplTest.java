package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.raid.DetectedRaidCard;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.vo.RaidCardObservation;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * E3.5-5-b D1 — 관측 파생: 활성 스냅샷이 대상 카드를 지정한 게스트만 · 저장 인벤토리의 감지 카드만 · 실패는 관용.
 */
@ExtendWith(MockitoExtension.class)
class RaidCardObservationProviderImplTest {

	@Mock SettingAssignmentSnapshotRepository assignmentRepository;
	@Mock GuestServerDetailRepository detailRepository;
	private final ObjectMapper mapper = new ObjectMapper();
	private RaidCardObservationProviderImpl provider;

	@BeforeEach
	void setUp() {
		provider = new RaidCardObservationProviderImpl(assignmentRepository, detailRepository, mapper);
	}

	private static GuestServer guest(UUID id, String name, String serial) {
		GuestServer guest = mock(GuestServer.class);
		lenient().when(guest.getId()).thenReturn(id);
		lenient().when(guest.getName()).thenReturn(name);
		lenient().when(guest.getSerialNumber()).thenReturn(serial);
		lenient().when(guest.systemUUIDSuffix()).thenReturn("8d709bc972b1");
		return guest;
	}

	/** raidCardId null 은 "RAID 단계는 있으나 카드 미지정", hasRaid=false 는 "단계 없음". */
	private static SettingAssignmentSnapshot snapshot(GuestServer guest, boolean hasRaid, Long raidCardId) {
		SettingAssignmentSnapshot snapshot = mock(SettingAssignmentSnapshot.class);
		lenient().when(snapshot.getGuestServer()).thenReturn(guest);
		given(snapshot.processRequestOf(RaidConfigurationRequest.class)).willReturn(hasRaid
				? Optional.of(new RaidConfigurationRequest(raidCardId, List.of(), VolumePriorityRuleRequest.defaults(), null))
				: Optional.empty());
		return snapshot;
	}

	private static GuestServerDetail detail(GuestServer guest, String raidInventoryJson) {
		GuestServerDetail detail = mock(GuestServerDetail.class);
		given(detail.getGuestServer()).willReturn(guest);
		given(detail.getRaidInventoryJson()).willReturn(raidInventoryJson);
		return detail;
	}

	private String inventoryJson(String subsystem) {
		return mapper.writeValueAsString(new RaidInventory(
				subsystem == null ? null : new DetectedRaidCard(RaidChipFamily.MEGARAID, subsystem, "9361-8i", "4.6"),
				List.of(), List.of()));
	}

	@Test
	@DisplayName("빈 카드 집합 — 리포지토리를 묻지 않고 빈 맵")
	void emptyIds() {
		assertThat(provider.observationsByCard(Set.of())).isEmpty();
		verifyNoInteractions(assignmentRepository, detailRepository);
	}

	@Test
	@DisplayName("대상 카드를 지정한 활성 스냅샷의 게스트만 — 다른 카드 · RAID 없음 · 카드 미지정은 제외")
	void onlyDesignatedGuests() {
		GuestServer a = guest(UUID.randomUUID(), "srv-a", null);
		GuestServer b = guest(UUID.randomUUID(), "srv-b", null);
		GuestServer c = guest(UUID.randomUUID(), "srv-c", null);
		GuestServer d = guest(UUID.randomUUID(), "srv-d", null);
		// 스텁 안에서 스텁하는 헬퍼를 부르면 Mockito 가 "unfinished stubbing" 으로 막는다 — 먼저 만들고 나서 스텁한다
		List<SettingAssignmentSnapshot> snapshots = List.of(
				snapshot(a, true, 7L), snapshot(b, true, 8L), snapshot(c, false, null), snapshot(d, true, null));
		List<GuestServerDetail> details = List.of(detail(a, inventoryJson("1000:9361")));
		given(assignmentRepository.findBySupersededAtIsNull()).willReturn(snapshots);
		given(detailRepository.findAllByServerIdInWithBoardModel(List.of(a.getId()))).willReturn(details);

		Map<Long, List<RaidCardObservation>> result = provider.observationsByCard(Set.of(7L));

		assertThat(result).containsOnlyKeys(7L);
		assertThat(result.get(7L)).singleElement().satisfies(obs -> {
			assertThat(obs.guestServerId()).isEqualTo(a.getId());
			assertThat(obs.guestLabel()).isEqualTo("srv-a");
			assertThat(obs.pciSubsystemId()).isEqualTo("1000:9361");
		});
	}

	@Test
	@DisplayName("지정 게스트가 없으면 detail 을 묻지 않는다")
	void noDesignatedGuest() {
		List<SettingAssignmentSnapshot> snapshots = List.of(snapshot(guest(UUID.randomUUID(), "x", null), true, 8L));
		given(assignmentRepository.findBySupersededAtIsNull()).willReturn(snapshots);
		assertThat(provider.observationsByCard(Set.of(7L))).isEmpty();
		verify(detailRepository, never()).findAllByServerIdInWithBoardModel(anyList());
	}

	@Test
	@DisplayName("관용 — 인벤토리 없음 · 카드 미감지 · JSON 손상 게스트는 관측에서 빠지고 정상 게스트만 남는다")
	void tolerantSkips() {
		GuestServer none = guest(UUID.randomUUID(), "none", null);
		GuestServer noCard = guest(UUID.randomUUID(), "nocard", null);
		GuestServer broken = guest(UUID.randomUUID(), "broken", null);
		GuestServer ok = guest(UUID.randomUUID(), "ok", null);
		List<SettingAssignmentSnapshot> snapshots = List.of(
				snapshot(none, true, 7L), snapshot(noCard, true, 7L), snapshot(broken, true, 7L), snapshot(ok, true, 7L));
		List<GuestServerDetail> details = List.of(
				detail(none, null), detail(noCard, inventoryJson(null)), detail(broken, "{not json"), detail(ok, inventoryJson("1000:00ce")));
		given(assignmentRepository.findBySupersededAtIsNull()).willReturn(snapshots);
		given(detailRepository.findAllByServerIdInWithBoardModel(anyList())).willReturn(details);

		Map<Long, List<RaidCardObservation>> result = provider.observationsByCard(Set.of(7L));

		assertThat(result.get(7L)).extracting(RaidCardObservation::guestLabel).containsExactly("ok");
	}

	@Test
	@DisplayName("라벨 폴백 — 이름 → 시리얼 → systemUUID 끝 세그먼트(CP5 F-1: id 앞자리는 시각부라 겹친다)")
	void labelFallback() {
		UUID id = UUID.randomUUID();
		assertThat(RaidCardObservationProviderImpl.labelOf(guest(id, "srv-01", "SN1"))).isEqualTo("srv-01");
		assertThat(RaidCardObservationProviderImpl.labelOf(guest(id, " ", "SN1"))).isEqualTo("SN1");
		assertThat(RaidCardObservationProviderImpl.labelOf(guest(id, null, null))).isEqualTo("8d709bc972b1");
	}
}
