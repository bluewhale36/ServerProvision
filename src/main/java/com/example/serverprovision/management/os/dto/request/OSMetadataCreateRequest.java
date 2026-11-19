package com.example.serverprovision.management.os.dto.request;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.validation.RegistrableOsName;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신규 OS 메타데이터 등록 요청. (osName, osVersion) 조합은 유일해야 한다.
 *
 * <p>S23-1: OS 는 배포판과 메이저 단위 상수에서 고르고, 버전은 그 상수의 버전 규칙({@link OSName#acceptsVersion})을
 * 따라야 한다. 미지원 상수는 화면에 나오지 않으며, 직접 보낸 요청은 {@link RegistrableOsName} 이 거절한다.</p>
 */
public record OSMetadataCreateRequest(
		@NotNull(message = "OS 이름을 선택해야 합니다.")
		@RegistrableOsName
		OSName osName,

		@NotBlank(message = "OS 버전을 입력해야 합니다.")
		@Size(max = 64, message = "OS 버전은 64자 이하로 입력해야 합니다.")
		String osVersion,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해야 합니다.")
		String description
) {

	/**
	 * 등록 화면의 빈 폼. OS 목록에서 고른 OS 가 넘어오면 그 OS 를 미리 고른다.
	 * 미지원 OS 는 등록할 수 없으므로 고르지 않은 채로 둔다.
	 */
	public static OSMetadataCreateRequest startingWith(OSName requested) {
		OSName preselected = requested != null && requested.registrable() ? requested : null;
		return new OSMetadataCreateRequest(preselected, "", "");
	}

	/** 버전이 고른 OS 의 메이저와 맞는가. Rocky Linux 9 는 "9.6" 을 받고 "10.0" 을 거절한다. */
	@AssertTrue(message = "OS 버전은 고른 OS 의 메이저 버전과 맞아야 합니다.")
	public boolean isOsVersionAccepted() {
		// 미지원 OS 나 빈 버전은 다른 검증이 먼저 말한다(오류를 겹쳐 내지 않는다).
		if (osName == null || !osName.registrable() || osVersion == null || osVersion.isBlank()) {
			return true;
		}
		return osName.acceptsVersion(osVersion);
	}
}
