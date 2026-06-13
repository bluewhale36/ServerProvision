package com.example.serverprovision.management.bmc.controller;

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
 * MA4 BMC 펌웨어 등록 폼의 경로 입력 보조 — 서버 디렉토리 탐색 단일 endpoint.
 *
 * <p>R5-1 분할 — 단일 {@code BmcController} 에서 browse 책임을 분리.
 * 의존성: {@link DirectoryBrowseService} 단독.</p>
 *
 * <p>본문 try/catch 4 분기는 R5-1 시점엔 컨트롤러 로컬로 유지(@RestControllerAdvice 승급은
 * 후속 슬라이스 이월) — 동작 보존 우선.</p>
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcBrowseController {

	private final DirectoryBrowseService directoryBrowseService;

	@GetMapping(path = "/browse")
	@ResponseBody
	public ResponseEntity<?> browse(@RequestParam(name = "path", required = false) String pathParam) {
		try {
			return ResponseEntity.ok(directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, true)));
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
