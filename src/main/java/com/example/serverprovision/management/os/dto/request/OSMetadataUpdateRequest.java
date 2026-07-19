package com.example.serverprovision.management.os.dto.request;

import jakarta.validation.constraints.Size;

/**
 * OS 메타데이터 수정 요청. OSName / osVersion 은 변경 불가 (R1-2 — 동일 정책).
 * 변경이 필요한 경우 자원을 삭제 후 다시 생성한다. 본 요청으로는 description 만 갱신.
 */
public record OSMetadataUpdateRequest(
		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description
) {

}
