package com.example.serverprovision.management.subprogram.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 변형 한 행(R15-1) — 수정 폼의 표 행 · 인덱스 바인딩({@code variants[i].*}). 폼 바인딩이 빈 행을 자동 생성해야 하므로
 * record 가 아니라 setter 클래스다. 구조 규칙(진입점 필수 · 확장자 · 버전 중복)은 {@code SubprogramVariantRules} 가,
 * 경로 보안은 EntrypointPolicyService 가 본다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubprogramVariantRequest {

	@Size(max = 64, message = "OS 버전은 64자 이하로 입력하십시오.")
	private String osVersion;

	@Size(max = 512, message = "진입점 경로는 512자 이하로 입력하십시오.")
	private String entrypointRelativePath;

	@Size(max = 512, message = "인자는 512자 이하로 입력하십시오.")
	private String arguments;

	private boolean rebootRequired;
}
