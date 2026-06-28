package com.example.serverprovision.management.bios.controller;

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
 * BIOS 폼 입력 보조 — 서버 경로 탐색 진입점.
 *
 * <p>R4-1 — fat {@code BiosController} 6분할 결과. <b>R2-3</b> — browse 예외 4종(InvalidBrowsePath /
 * BrowseTargetNotFound / BrowseTargetNotDirectory / DirectoryBrowseIo)을 advice 로 승급(컨트롤러 try/catch 제거).
 * 호출처(path-browser.js / bundle-upload-bootstrap.js)가 명시적 {@code Accept: application/json} 을 보내므로
 * {@code ApiExceptionHandler} 의 JSON 핸들러가 매칭한다(NotFound→404 / NotDirectory→409 /
 * InvalidBrowsePath→@ResponseStatus(400) / Io→handleDomain 500). 동작 보존.</p>
 */
@Controller
@RequestMapping("/management/bios")
@RequiredArgsConstructor
public class BiosBrowseController {

	private final DirectoryBrowseService directoryBrowseService;

	/**
	 * 관리자가 "대상 디렉토리" 필드를 손수 타자하지 않고 서버 경로를 브라우저로 내비게이션해 고를 수 있게 한다.
	 * 파일이 아닌 <b>디렉토리만</b> 목록으로 돌려주며, 하위 디렉토리가 없어도 빈 배열로 정상 응답.
	 * 본 엔드포인트는 관리자 도구 성격이라 화이트리스트 같은 경로 제한을 두지 않는다.
	 */
	@GetMapping(path = "/browse")
	@ResponseBody
	public ResponseEntity<?> browse(@RequestParam(name = "path", required = false) String pathParam) {
		return ResponseEntity.ok(directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, false)));
	}
}
