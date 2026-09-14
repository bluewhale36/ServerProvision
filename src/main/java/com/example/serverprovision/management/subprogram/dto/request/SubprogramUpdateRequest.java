package com.example.serverprovision.management.subprogram.dto.request;

import com.example.serverprovision.management.os.enums.OSName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Subprogram 메타 + OS + 변형 표 수정 Request(R15-1). 진입점은 자원 단위 필드가 아니라 변형 행에 있다.
 * {@code variants} 는 표 전체(교체 의미) — 비면 변형 없음(트리 전체를 현행대로 다룸). 인덱스 바인딩이 행을
 * 자동으로 늘려야 하므로 setter 클래스다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubprogramUpdateRequest {

	@NotBlank(message = "이름을 입력해주세요.")
	@Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
	private String name;

	@NotBlank(message = "버전을 입력해주세요.")
	@Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
	private String version;

	@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
	private String description;

	private OSName osName;

	@Valid
	private List<SubprogramVariantRequest> variants = new ArrayList<>();

	public List<SubprogramVariantRequest> variantsOrEmpty() {
		return variants == null ? List.of() : variants;
	}
}
