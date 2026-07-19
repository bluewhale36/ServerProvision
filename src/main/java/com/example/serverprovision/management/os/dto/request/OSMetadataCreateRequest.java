package com.example.serverprovision.management.os.dto.request;

import com.example.serverprovision.management.os.enums.OSName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신규 OS 메타데이터 등록 요청. OS 메타데이터는 이름과 버전만 가진다 —
 * 실제 파일 경로(ISO, kickstart 템플릿 등) 는 별도 하위 리소스에서 다룬다.
 * (osName, osVersion) 조합은 활성 레코드 안에서 유일해야 한다.
 */
public record OSMetadataCreateRequest(
		@NotNull(message = "OS 이름을 선택하세요.")
		OSName osName,

		@NotBlank(message = "OS 버전을 입력하세요.")
		@Size(max = 64, message = "OS 버전은 64자 이하로 입력해주세요.")
		String osVersion,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description
) {

}
