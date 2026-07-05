package com.example.serverprovision.management.common.filesystem.controller;

import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 관리 영역 공용 서버 디렉토리 탐색 endpoint — <b>R8-2</b> 에서 도메인별 4 BrowseController
 * (OS/BIOS/BMC/Subprogram)를 흡수한 단일 진입점.
 *
 * <p>browse 는 도메인 개념이 아니라 파일시스템 유틸리티다 — 구 4 컨트롤러는 도메인 로직 0 의 동형 위임이었고
 * 갈리는 것은 {@code includeFiles} 하드코딩뿐이었다. 이를 요청 param(기본 false)으로 이동해 표시 정책
 * 결정권을 호출처(frontend)로 옮겼다. 경로 보안검증(허용루트/traversal)은 {@link DirectoryBrowseService} 담당.</p>
 *
 * <p>browse 예외 4종의 status 매핑은 R2-3(=R8-1)이 advice 로 승급 완료 — 호출처가 명시적
 * {@code Accept: application/json} 을 보내므로 {@code ApiExceptionHandler} JSON 핸들러가 매칭한다
 * (InvalidBrowsePath 400 / BrowseTargetNotFound 404 / BrowseTargetNotDirectory 409 / Io 500).</p>
 */
@Controller
@RequestMapping("/management/browse")
@RequiredArgsConstructor
public class DirectoryBrowseController {

	private final DirectoryBrowseService directoryBrowseService;

	/**
	 * 등록/편집 폼의 경로 입력 보조. {@code includeFiles=true} 시 파일까지 반환 —
	 * ISO 직접 선택(iso-new/edit)과 Subprogram entrypoint 상대경로 선택(subprogram-edit)이 사용한다.
	 */
	@GetMapping
	@ResponseBody
	public ResponseEntity<?> browse(
			@RequestParam(name = "path", required = false) String pathParam,
			@RequestParam(name = "includeFiles", defaultValue = "false") boolean includeFiles
	) {
		return ResponseEntity.ok(directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, includeFiles)));
	}
}
