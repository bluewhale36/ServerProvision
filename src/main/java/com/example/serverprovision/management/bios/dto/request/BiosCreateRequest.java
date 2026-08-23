package com.example.serverprovision.management.bios.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BIOS 펌웨어 등록 Request (XHR 경로에서 {@code @ModelAttribute} 로 수신).
 *
 * <p>R12-1 — 번들(폴더 · zip) 업로드 방식을 폐지하고 ISO 등록과 동일한 단일 폼으로 통합했다.
 * {@code firmwarePath} 가 {@code /} 로 끝나면 디렉토리로 간주해 업로드 파일명을 이어 붙이고,
 * 파일 경로면 그대로 쓴다. 같은 요청에 업로드 파일({@code firmwareFile})이 있으면 그 경로에 저장하고,
 * 없으면 그 경로에 이미 존재하는 파일을 자원으로 등록(claim)한다.</p>
 */
public record BiosCreateRequest(

		@NotBlank(message = "이름을 입력해주세요.")
		@Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
		String name,

		@NotBlank(message = "버전을 입력해주세요.")
		@Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
		String version,

		@NotBlank(message = "펌웨어 파일 경로를 입력해주세요.")
		@Size(max = 1024, message = "펌웨어 파일 경로는 1024자 이하로 입력해주세요.")
		String firmwarePath,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description,

		/**
		 * 상위 디렉토리가 없을 때 자동 생성할지 여부. 기본 false.
		 */
		boolean allowCreateDirectory
) {

}
