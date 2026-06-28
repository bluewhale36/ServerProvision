package com.example.serverprovision.management.bmc.controller;

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
 * MA4 BMC 펌웨어 등록 폼의 경로 입력 보조 — 서버 디렉토리 탐색 단일 endpoint.
 *
 * <p>R5-1 분할 — 단일 {@code BmcController} 에서 browse 책임 분리. <b>R2-3</b> — browse 예외 4종을 advice 로
 * 승급(try/catch 제거). 호출처가 명시적 {@code Accept: application/json} 을 보내 ApiExceptionHandler 가 JSON 응답.
 * 동작 보존.</p>
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcBrowseController {

	private final DirectoryBrowseService directoryBrowseService;

	@GetMapping(path = "/browse")
	@ResponseBody
	public ResponseEntity<?> browse(@RequestParam(name = "path", required = false) String pathParam) {
		return ResponseEntity.ok(directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, true)));
	}
}
