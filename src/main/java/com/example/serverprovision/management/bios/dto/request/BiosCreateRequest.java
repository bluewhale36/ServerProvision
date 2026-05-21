package com.example.serverprovision.management.bios.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BIOS 번들 등록 Request (XHR 업로드 경로에서 {@code @ModelAttribute} 로 수신).
 * <p>v3 부터 파일 경로 단일 필드는 사라지고 "대상 디렉토리 경로" 로 대체된다.
 * 실제 바이너리 전송은 같은 요청의 multipart file[] 또는 단일 zip 파일로 이뤄진다.</p>
 *
 * <p>{@code entrypointRelativePath} 는 자동 탐지 규칙(f.nsh / flash.nsh / 단일 *.nsh) 이
 * 실패할 때만 관리자가 기입한다. 비어 있으면 서버가 자동 탐지.</p>
 */
public record BiosCreateRequest(

		@NotBlank(message = "이름을 입력해주세요.")
		@Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
		String name,

		@NotBlank(message = "버전을 입력해주세요.")
		@Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
		String version,

		@NotBlank(message = "대상 디렉토리 경로를 입력해주세요.")
		@Size(max = 1024, message = "대상 디렉토리 경로는 1024자 이하로 입력해주세요.")
		String targetDirectory,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description,

		/**
		 * 상위 디렉토리가 없을 때 자동 생성할지 여부. 기본 false.
		 */
		boolean allowCreateDirectory,

		/**
		 * 진입점 상대경로 override. 빈 값이면 서버 자동 탐지. 예: {@code "f.nsh"}, {@code "subdir/update.nsh"}.
		 * 번들 수행 시 실행할 파일 자체만 지정하며, 실행 시 파라미터 등은 향후 Execution 도메인이 관장한다.
		 */
		@Size(max = 512, message = "진입점 경로는 512자 이하로 입력해주세요.")
		String entrypointRelativePath
) {

}
