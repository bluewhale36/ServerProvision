package com.example.serverprovision.management.os.dto.request;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.enums.RhelRelease;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신규 OS 메타데이터 등록 요청. OS 메타데이터는 이름과 버전만 가진다 —
 * 실제 파일 경로(ISO, kickstart 템플릿 등) 는 별도 하위 리소스에서 다룬다.
 * (osName, osVersion) 조합은 활성 레코드 안에서 유일해야 한다.
 *
 * <p>E4-1-b-2 D-21 — Rocky Linux 는 지원 메이저({@link RhelRelease})를 함께 고르고, 버전은 그 메이저의 세부 버전이어야
 * 한다. 메이저는 저장하지 않는다(버전 문자열에서 다시 계산되며 여기서 일치를 보장한다). 다른 OS 는 메이저를 받지 않는다.</p>
 */
public record OSMetadataCreateRequest(
		@NotNull(message = "OS 이름을 선택해야 합니다.")
		OSName osName,

		RhelRelease rhelRelease,

		@NotBlank(message = "OS 버전을 입력해야 합니다.")
		@Size(max = 64, message = "OS 버전은 64자 이하로 입력해야 합니다.")
		String osVersion,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해야 합니다.")
		String description
) {

	/** Rocky Linux 면 지원 메이저가 있어야 하고, 다른 OS 면 없어야 한다. */
	@AssertTrue(message = "지원 메이저 버전을 골라야 합니다.")
	public boolean isRhelReleaseValid() {
		if (osName == null) {
			return true;
		}
		boolean needsRelease = !RhelRelease.supportedMajors(osName).isEmpty();
		return needsRelease ? rhelRelease != null && rhelRelease.osName() == osName : rhelRelease == null;
	}

	/** 버전이 고른 메이저의 세부 버전인가(Rocky Linux 9 + "9.6" 통과, "10.0" 거절). */
	@AssertTrue(message = "OS 버전은 고른 메이저 버전의 세부 버전이어야 합니다.")
	public boolean isOsVersionCovered() {
		// 메이저가 이 OS 의 것이 아니면 isRhelReleaseValid 가 먼저 말한다(오류를 겹쳐 내지 않는다).
		if (rhelRelease == null || rhelRelease.osName() != osName || osVersion == null || osVersion.isBlank()) {
			return true;
		}
		return rhelRelease.covers(osVersion);
	}
}
