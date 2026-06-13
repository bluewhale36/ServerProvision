package com.example.serverprovision.management.bios.controller;

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
 * BIOS 폼 입력 보조 — 서버 경로 탐색 진입점.
 *
 * <p>R4-1 — fat {@code BiosController} 6분할 결과. browse() 의 4-catch 는 본 슬라이스에서는
 * 동작 보존을 위해 그대로(as-is) 이전한다. {@code @RestControllerAdvice} 승급(BIOS+OS 통합)은
 * 후속 슬라이스로 이월.</p>
 */
@Controller
@RequestMapping("/management/bios")
@RequiredArgsConstructor
public class BiosBrowseController {

	private final DirectoryBrowseService directoryBrowseService;

	/**
	 * 관리자가 "대상 디렉토리" 필드를 손수 타자하지 않고 서버 경로를 브라우저로 내비게이션해 고를 수 있게 한다.
	 * 파일이 아닌 <b>디렉토리만</b> 목록으로 돌려주며, 하위 디렉토리가 없어도 빈 배열로 정상 응답.
	 * <ul>
	 *   <li>{@code path} 미지정 or 공란 → 루트 ({@code /}) 기준</li>
	 *   <li>존재하지 않거나 권한 없음 → 404 + ApiErrorResponse</li>
	 *   <li>파일을 경로로 넘기면 → 409 + ApiErrorResponse</li>
	 * </ul>
	 * 본 엔드포인트는 관리자 도구 성격이라 화이트리스트 같은 경로 제한을 두지 않는다.
	 * A4 BMC · A5 Driver 에서도 같은 기능이 필요하게 되면 global 로 승급한다.
	 */
	@GetMapping(path = "/browse")
	@ResponseBody
	public ResponseEntity<?> browse(@RequestParam(name = "path", required = false) String pathParam) {
		try {
			return ResponseEntity.ok(directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, false)));
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
