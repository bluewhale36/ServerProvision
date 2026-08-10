package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.service.reference.os.OsMetadataReferenceChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MK4-2 — 각 단계가 "파일 실체가 있는 자원" 중 무엇을 지목하는지.
 *
 * <p>여기서 고정하는 계약은 둘이다. 첫째, 파일이 없는 논리 자원(메인보드 모델 · OS 버전 ·
 * BIOS 세팅 템플릿)은 담지 않는다 — 대조할 드리프트가 없기 때문이다. 둘째, 버전을 고정하지 않은
 * 선택(LATEST)은 참조로 세지 않는다 — 어느 파일이 쓰일지 실행 시점에 정해지므로 지금 특정 자원을
 * 지정하고 있다고 말할 수 없다.</p>
 */
class ReferencedResourcesTest {

	private static BasicUpdateRequest firmware(FirmwareSelectionRequest bios, FirmwareSelectionRequest bmc) {
		BasicUpdateRequest request = Mockito.mock(BasicUpdateRequest.class);
		Mockito.when(request.getBios()).thenReturn(bios);
		Mockito.when(request.getBmc()).thenReturn(bmc);
		return request;
	}

	private static FirmwareSelectionRequest specified(Long id) {
		return new FirmwareSelectionRequest(FirmwareSelectionMode.SPECIFIED, id);
	}

	private static FirmwareSelectionRequest latest() {
		return new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null);
	}

	private static BasicUpdateReferenceInspector basicUpdateInspector() {
		return new BasicUpdateReferenceInspector(
				Mockito.mock(BoardModelRepository.class),
				Mockito.mock(BiosRepository.class),
				Mockito.mock(BmcRepository.class));
	}

	@Test
	@DisplayName("펌웨어 업데이트 — 버전을 고정한 BIOS · BMC 파일을 지목한다")
	void basicUpdateReferencesPinnedFirmware() {
		List<ResourceKey> keys = basicUpdateInspector()
				.referencedResources(firmware(specified(11L), specified(22L)));

		assertThat(keys).containsExactlyInAnyOrder(
				new ResourceKey(ResourceType.BIOS_BUNDLE, 11L),
				new ResourceKey(ResourceType.BMC_FIRMWARE, 22L));
	}

	@Test
	@DisplayName("펌웨어 업데이트 — 최신 선택(LATEST)은 참조로 세지 않는다")
	void latestSelectionIsNotAReference() {
		assertThat(basicUpdateInspector().referencedResources(firmware(latest(), latest()))).isEmpty();
	}

	@Test
	@DisplayName("펌웨어 업데이트 — 한쪽만 고정하면 그쪽만 담는다")
	void onlyPinnedSideIsCounted() {
		List<ResourceKey> keys = basicUpdateInspector()
				.referencedResources(firmware(specified(11L), latest()));

		assertThat(keys).containsExactly(new ResourceKey(ResourceType.BIOS_BUNDLE, 11L));
	}

	@Test
	@DisplayName("BIOS 세팅 — 파일 실체가 있는 자원을 참조하지 않는다")
	void basicSettingReferencesNoFileBackedResource() {
		BasicSettingReferenceInspector inspector = new BasicSettingReferenceInspector(
				Mockito.mock(com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository.class),
				Mockito.mock(BoardModelRepository.class));

		assertThat(inspector.referencedResources(null)).isEmpty();
	}

	@Test
	@DisplayName("OS 설정 — 파일 실체가 있는 자원을 참조하지 않는다")
	void osSettingReferencesNoFileBackedResource() {
		var inspector = new com.example.serverprovision.provisioning.setting.service.reference.os
				.OSSettingReferenceInspector(
				Mockito.mock(OsMetadataReferenceChecker.class), List.of());

		assertThat(inspector.referencedResources(null)).isEmpty();
	}

	@Test
	@DisplayName("모든 검사기가 널 대신 빈 목록을 돌려준다 — 호출부가 널을 다루지 않는다")
	void neverReturnsNull() {
		assertThat(basicUpdateInspector().referencedResources(firmware(latest(), latest()))).isNotNull();
	}
}
