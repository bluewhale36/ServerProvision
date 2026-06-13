package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * MA5 Subprogram 등록 폼의 경로 입력 보조 — 서버 디렉토리 탐색 단일 endpoint.
 *
 * <p>R6-1 에서 fat {@code SubprogramController} 의 browse 를 분리. 의존성 중복이 전혀 없어
 * ({@code DirectoryBrowseService} 단독) 깔끔하게 분리된다. 본문은 원본을 그대로 (as-is) 이동했다 —
 * advice 승급 (cross-cutting) 은 본 슬라이스 범위 밖 (후속 이월).</p>
 */
@Controller
@RequestMapping("/management/subprogram")
@RequiredArgsConstructor
public class SubprogramBrowseController {

	private final DirectoryBrowseService directoryBrowseService;

	@GetMapping("/browse")
	@ResponseBody
	public Object browse(@RequestParam(name = "path", required = false) String pathParam) {
		return directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, true));
	}
}
