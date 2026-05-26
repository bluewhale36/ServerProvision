package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * ISO 등록 폼의 경로 입력 보조 — 서버 디렉토리 탐색 단일 endpoint.
 *
 * <p>다른 controller 와 의존성 중복이 전혀 없어 (DirectoryBrowseService 단독) 깔끔하게 분리.
 * BIOS / BMC / Subprogram 도메인의 동일한 browse endpoint 와 코드가 거의 같아 향후 generic browse
 * controller 로 흡수할 가능성 있음 (보고서 §13-D8 와 유사한 generic 화 후보).</p>
 *
 * <p>본문 try/catch 4 분기는 framework 의 advice 가 HTML 뷰로 응답하는 회귀를 막기 위한
 * 컨트롤러 로컬 처리 — 후속 슬라이스 (보고서 §13-D9) 에서 produces=APPLICATION_JSON 표준화 후
 * 제거 대상.</p>
 */
@Controller
@RequestMapping("/management/os")
@RequiredArgsConstructor
public class OSBrowseController {

	private final DirectoryBrowseService directoryBrowseService;

	/**
	 * ISO 등록 폼의 "ISO 경로" 필드를 마우스로 채우게 하는 보조 API.
	 * BIOS {@code /management/bios/browse} 와 같은 형식이지만 {@code includeFiles=true} 시 파일까지 보여
	 * "이미 디스크에 있는 .iso 를 직접 선택" 케이스를 지원한다 (경로만 등록 흐름).
	 * <ul>
	 *   <li>{@code path} 미지정 → 루트 ({@code /})</li>
	 *   <li>{@code includeFiles=true} → 디렉토리 + 파일 모두. {@code .iso} 외 파일도 포함되지만 클라이언트가 강조 표시</li>
	 *   <li>존재하지 않음 → 404, 파일 경로 → 409, 권한/IO 오류 → 500</li>
	 * </ul>
	 */
	@GetMapping(path = "/browse")
	@ResponseBody
	public ResponseEntity<?> browse(
			@RequestParam(name = "path", required = false) String pathParam,
			@RequestParam(name = "includeFiles", defaultValue = "false") boolean includeFiles
	) {
		// XHR 엔드포인트 — Accept 헤더가 `*` `/*` 인 경우 Web advice 의 `handleDomain` 이 먼저 매칭되어
		// 500 HTML 로 응답하는 회귀를 막기 위해 컨트롤러 로컬 try/catch 를 유지한다. 보안 예외 (S3) 는
		// 별도 계층 (SecurityException) 이라 본 catch 에 흡수되지 않고 ApiExceptionHandler 로 통과한다.
		try {
			return ResponseEntity.ok(directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, includeFiles)));
		} catch (InvalidBrowsePathException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new ApiErrorResponse(e.getMessage()));
		} catch (BrowseTargetNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new ApiErrorResponse(e.getMessage()));
		} catch (BrowseTargetNotDirectoryException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiErrorResponse(e.getMessage()));
		} catch (DirectoryBrowseIoException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new ApiErrorResponse(e.getMessage()));
		}
	}
}
