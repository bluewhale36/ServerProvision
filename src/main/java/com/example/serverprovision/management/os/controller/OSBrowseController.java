package com.example.serverprovision.management.os.controller;

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
 * ISO 등록 폼의 경로 입력 보조 — 서버 디렉토리 탐색 단일 endpoint.
 *
 * <p>의존성: {@link DirectoryBrowseService} 단독. <b>R2-3</b> — browse 예외 4종을 advice 로 승급(try/catch 제거).
 * 호출처가 명시적 {@code Accept: application/json} 을 보내므로 {@code ApiExceptionHandler} JSON 핸들러가 매칭한다
 * (존재없음 404 / 파일경로 409 / InvalidBrowsePath 400 / IO 500). 동작 보존 — 이전 try/catch 가 우려한 {@code *}{@code /*}
 * 회귀는 실제 호출처가 명시 Accept 를 보내 발생하지 않으며, 이 invariant 는 AdviceExceptionMappingTest 가 고정한다.</p>
 */
@Controller
@RequestMapping("/management/os")
@RequiredArgsConstructor
public class OSBrowseController {

	private final DirectoryBrowseService directoryBrowseService;

	/**
	 * ISO 등록 폼의 "ISO 경로" 필드를 마우스로 채우게 하는 보조 API.
	 * {@code includeFiles=true} 시 파일까지 보여 "이미 디스크에 있는 .iso 를 직접 선택" 케이스를 지원한다(경로만 등록 흐름).
	 */
	@GetMapping(path = "/browse")
	@ResponseBody
	public ResponseEntity<?> browse(
			@RequestParam(name = "path", required = false) String pathParam,
			@RequestParam(name = "includeFiles", defaultValue = "false") boolean includeFiles
	) {
		return ResponseEntity.ok(directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, includeFiles)));
	}
}
