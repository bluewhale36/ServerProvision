package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.exception.DomainException;
import com.example.serverprovision.global.exception.NotFoundException;
import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.dto.request.BiosUpdateRequest;
import com.example.serverprovision.management.bios.dto.request.BiosUploadIntentRequest;
import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.management.bios.dto.response.BiosResponse;
import com.example.serverprovision.management.bios.dto.response.BiosUploadIntentResponse;
import com.example.serverprovision.management.bios.dto.response.BiosUploadResponse;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bios.service.BiosUploadIntentService;
import com.example.serverprovision.management.bios.service.BiosVerificationLauncher;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.service.BoardModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * A3 v3. BIOS 번들 관리 MVC 컨트롤러.
 *
 * <ul>
 *   <li>BIOS 는 번들(디렉토리 트리) 단위로 관리된다. 단일 파일 등록 개념은 사라졌다.</li>
 *   <li>업로드 경로는 {@code /upload-intent} → {@code /upload} 의 2단 핸드셰이크로 고정. MVC fallback POST 없음.</li>
 *   <li>Intent / Upload / Verify 엔드포인트는 JSON 응답. {@code ApiErrorResponse} 로 예외를 래핑해
 *       GlobalExceptionHandler 의 HTML 에러 뷰 경로를 회피 → 클라이언트가 실제 사유를 파싱 가능.</li>
 *   <li>Verify 는 무결성 상태만 조회 (수정 안 함) 이지만 POST 로 고정 — 상태 계산 비용이 크고 감사 로그에 남길 필요가 있어 단순 GET 으로 캐싱되지 않도록 함.</li>
 * </ul>
 */
@Controller
@RequestMapping("/management/bios")
@RequiredArgsConstructor
public class BiosController {

    private final BiosService biosService;
    private final BiosUploadIntentService biosUploadIntentService;
    private final BoardModelService boardModelService;
    private final BiosVerificationLauncher biosVerificationLauncher;
    private final DirectoryBrowseService directoryBrowseService;

    // ==== 목록 ========================================================

    @GetMapping
    public String list(@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
                       @RequestParam(name = "selectId", required = false) Long selectId,
                       @RequestParam(name = "selectBoardId", required = false) Long selectBoardId,
                       @RequestParam(name = "selectedBoardId", required = false) Long selectedBoardId,
                       Model model) {
        Long initialBoardId = selectBoardId != null ? selectBoardId : selectedBoardId;
        model.addAttribute("boards", biosService.findAllGrouped(includeDeleted));
        model.addAttribute("includeDeleted", includeDeleted);
        model.addAttribute("selectId", selectId);
        model.addAttribute("selectBoardId", initialBoardId);
        return "management/bios/list";
    }

    // ==== 신규 번들 등록 ===============================================

    @GetMapping("/{boardId}/new")
    public String newForm(@PathVariable("boardId") Long boardId, Model model) {
        BoardModelResponse board = boardModelService.findById(boardId);
        model.addAttribute("biosForm", new BiosCreateRequest("", "", "", "", false, ""));
        populateFormContext(model, boardId, null, board);
        return "management/bios/bios-new";
    }

    /**
     * 업로드 Intent 핸드셰이크 — 번들 바이트 전송 이전 하드 검증 + 토큰 발급.
     */
    @PostMapping(path = "/{boardId}/upload-intent")
    @ResponseBody
    public ResponseEntity<?> intent(@PathVariable("boardId") Long boardId,
                                    @Valid @RequestBody BiosUploadIntentRequest request,
                                    BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String msg = bindingResult.getFieldErrors().stream()
                    .findFirst()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .orElse("입력값이 올바르지 않습니다.");
            return ResponseEntity.badRequest().body(new ApiErrorResponse(msg));
        }
        try {
            BiosUploadIntentResponse body = biosUploadIntentService.issue(boardId, request);
            return ResponseEntity.ok(body);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(e.getMessage()));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(e.getMessage()));
        } catch (DomainException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(e.getMessage()));
        }
    }

    /**
     * 번들 업로드 본체. {@code uploadMode} 에 따라 {@code folderFiles[]} / {@code zipFile} / {@code singleFile}
     * 중 정확히 하나만 실어 보낸다.
     */
    @PostMapping(path = "/{boardId}/upload")
    @ResponseBody
    public ResponseEntity<?> uploadBundle(@PathVariable("boardId") Long boardId,
                                          @Valid @ModelAttribute BiosCreateRequest request,
                                          BindingResult bindingResult,
                                          @RequestParam("uploadMode") BiosUploadMode uploadMode,
                                          @RequestParam(value = "folderFiles", required = false) MultipartFile[] folderFiles,
                                          @RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
                                          @RequestParam(value = "singleFile", required = false) MultipartFile singleFile,
                                          @RequestHeader(name = "X-Upload-Token", required = false) String uploadToken) {
        if (bindingResult.hasErrors()) {
            String msg = bindingResult.getFieldErrors().stream()
                    .findFirst()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .orElse("입력값이 올바르지 않습니다.");
            return ResponseEntity.badRequest().body(new ApiErrorResponse(msg));
        }
        try {
            biosUploadIntentService.consume(boardId, uploadToken);
            Long id = biosService.addBios(boardId, request, uploadMode, folderFiles, zipFile, singleFile);
            String redirect = "/management/bios?selectId=" + id;
            return ResponseEntity.ok(new BiosUploadResponse(id, redirect));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(e.getMessage()));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(e.getMessage()));
        } catch (DomainException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(e.getMessage()));
        }
    }

    // ==== 메타 수정 ===================================================

    @GetMapping("/{boardId}/bios/{biosId}/edit")
    public String editForm(@PathVariable("boardId") Long boardId,
                           @PathVariable("biosId") Long biosId,
                           Model model) {
        BiosResponse bios = biosService.findBios(boardId, biosId);
        BoardModelResponse board = boardModelService.findById(boardId);
        model.addAttribute("biosForm", new BiosUpdateRequest(
                bios.name(),
                bios.version(),
                nullToEmpty(bios.description())
        ));
        model.addAttribute("treeRootPath", bios.treeRootPath());
        model.addAttribute("entrypointRelativePath", bios.entrypointRelativePath());
        populateFormContext(model, boardId, biosId, board);
        return "management/bios/bios-edit";
    }

    @PostMapping("/{boardId}/bios/{biosId}/edit")
    public String update(@PathVariable("boardId") Long boardId,
                         @PathVariable("biosId") Long biosId,
                         @Valid @ModelAttribute("biosForm") BiosUpdateRequest request,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            BiosResponse bios = biosService.findBios(boardId, biosId);
            BoardModelResponse board = boardModelService.findById(boardId);
            model.addAttribute("treeRootPath", bios.treeRootPath());
            model.addAttribute("entrypointRelativePath", bios.entrypointRelativePath());
            populateFormContext(model, boardId, biosId, board);
            return "management/bios/bios-edit";
        }
        biosService.update(boardId, biosId, request);
        return redirectToListWithSelect(biosId);
    }

    // ==== 상태 전이 =====================================================

    @PostMapping("/{boardId}/bios/{biosId}/toggle")
    public String toggle(@PathVariable("boardId") Long boardId,
                         @PathVariable("biosId") Long biosId) {
        biosService.toggleEnabled(boardId, biosId);
        return redirectToListWithSelect(biosId);
    }

    @PostMapping("/{boardId}/bios/{biosId}/delete")
    public String delete(@PathVariable("boardId") Long boardId,
                         @PathVariable("biosId") Long biosId) {
        biosService.softDelete(boardId, biosId);
        return "redirect:/management/bios?selectBoardId=" + boardId;
    }

    @PostMapping("/{boardId}/bios/{biosId}/restore")
    public String restore(@PathVariable("boardId") Long boardId,
                          @PathVariable("biosId") Long biosId) {
        biosService.restore(boardId, biosId);
        return redirectToListWithSelect(biosId);
    }

    // ==== 무결성 / marker 관리 ==========================================

    /**
     * 현재 트리의 무결성 재검증을 BackgroundJob 으로 비동기 실행. 호출 측은 jobId 만 받고,
     * 결과(서명/해시 통과 여부) 는 알림 센터의 작업 카드 색상으로 확인한다.
     * 디렉토리 트리 manifest 재계산은 파일 수에 비례해 시간이 늘어나므로 비동기화 효과가 크다.
     */
    @PostMapping(path = "/{boardId}/bios/{biosId}/verify")
    @ResponseBody
    public com.example.serverprovision.global.job.dto.response.JobStartResponse verify(
            @PathVariable("boardId") Long boardId,
            @PathVariable("biosId") Long biosId) {
        String jobId = biosVerificationLauncher.startVerification(boardId, biosId);
        return new com.example.serverprovision.global.job.dto.response.JobStartResponse(jobId);
    }

    /**
     * 현재 시점의 무결성 상태를 즉시 계산해 badge 렌더링용 JSON 으로 반환한다.
     * CP2 단계에서는 persisted last status 가 아직 없으므로 조회 시마다 재계산한다.
     */
    @GetMapping(path = "/{boardId}/bios/{biosId}/integrity-status")
    @ResponseBody
    public IntegrityStatusResponse integrityStatus(@PathVariable("boardId") Long boardId,
                                                   @PathVariable("biosId") Long biosId) {
        return biosService.findIntegrityStatus(boardId, biosId);
    }

    // 단건 marker 재발급 endpoint 는 위험도가 높아 제거됨. 일괄 재발급은
    // PathReconciliationService.triggerReissueAllSignatures (POST /maintenance/reconciliation/reissue-all-markers) 로만 호출.

    // ==== 서버 경로 탐색 (폼 입력 보조) ================================

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

    // ==== 헬퍼 =========================================================

    private String redirectToListWithSelect(Long selectId) {
        return "redirect:/management/bios?selectId=" + selectId;
    }

    private void populateFormContext(Model model, Long boardId, Long biosId, BoardModelResponse board) {
        model.addAttribute("boardId", boardId);
        if (biosId != null) {
            model.addAttribute("biosId", biosId);
        }
        model.addAttribute("contextLabel",
                board.vendor().getDisplayName() + " · " + board.modelName());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
