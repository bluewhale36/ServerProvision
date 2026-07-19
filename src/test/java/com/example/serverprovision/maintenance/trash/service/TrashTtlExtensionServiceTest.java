package com.example.serverprovision.maintenance.trash.service;

import com.example.serverprovision.global.marker.MarkableTrashOperator;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.TrashPolicy;
import com.example.serverprovision.maintenance.trash.exception.TtlExtensionUnsupportedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * HF4-1 — 보존기간 연장 service 의 가드/위임 검증.
 *
 * <p>연장 지원 판정({@code supportsTrashTtlExtension})은 UI 버튼 disabled 와 공유하는 SPI SSOT —
 * 미지원 자원의 direct POST 는 {@link TtlExtensionUnsupportedException}(409) 안전망이 거절한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TrashTtlExtensionServiceTest {

	@Mock
	MarkableTrashOperator fileOperator;   // 파일 자원 (연장 지원) 대역

	@Mock
	MarkableTrashOperator metaOperator;   // 메타 자원 (연장 미지원) 대역

	@Mock
	TrashPolicy trashPolicy;

	TrashTtlExtensionService service;

	@BeforeEach
	void setUp() {
		given(fileOperator.supportedType()).willReturn(ResourceType.OS_ISO);
		given(metaOperator.supportedType()).willReturn(ResourceType.OS_IMAGE);
		service = new TrashTtlExtensionService(List.of(fileOperator, metaOperator), trashPolicy);
	}

	@Test
	@DisplayName("지원 자원 — scanner 에 위임 + step 일수(TrashPolicy TTL 일수) 전달")
	void extend_supported_delegatesWithStepDays() {
		given(fileOperator.supportsTrashTtlExtension()).willReturn(true);
		given(trashPolicy.getTtlDays()).willReturn(30);

		service.extend(ResourceType.OS_ISO, 5L);

		then(fileOperator).should().extendTrashTtl(5L, 30);
	}

	@Test
	@DisplayName("미지원 자원 (메타) — TtlExtensionUnsupportedException(409 계열) + 위임 미발생")
	void extend_unsupported_throwsConflict() {
		// supportsTrashTtlExtension default(false) — 별도 stub 없이 mock 기본값 그대로.
		assertThatThrownBy(() -> service.extend(ResourceType.OS_IMAGE, 2L))
				.isInstanceOf(TtlExtensionUnsupportedException.class)
				.hasMessageContaining("보존기간 연장을 지원하지 않습니다");

		then(metaOperator).should(never()).extendTrashTtl(anyLong(), anyInt());
	}

	@Test
	@DisplayName("미등록 자원 종류 — 기존 IllegalArgumentException 유지")
	void extend_unregisteredType_throwsIllegalArgument() {
		assertThatThrownBy(() -> service.extend(ResourceType.BOARD_MODEL, 1L))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
