package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.exception.DomainException;
import com.example.serverprovision.global.exception.NotFoundException;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcUpdateRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcResponse;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadResponse;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.bmc.service.BmcUploadIntentService;
import com.example.serverprovision.management.bmc.service.BmcVerificationLauncher;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * MA4 BMC 펌웨어 관리 MVC 컨트롤러.
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcController {

    private final BmcService bmcService;
    private final BmcUploadIntentService bmcUploadIntentService;
    private final BoardModelService boardModelService;
    private final BmcVerificationLauncher bmcVerificationLauncher;
    private final DirectoryBrowseService directoryBrowseService;

    @GetMapping
    public String list(@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
                       @RequestParam(name = "selectId", required = false) Long selectId,
                       @RequestParam(name = "selectBoardId", required = false) Long selectBoardId,
                       @RequestParam(name = "selectedBoardId", required = false) Long selectedBoardId,
                       Model model) {
        Long initialBoardId = selectBoardId != null ? selectBoardId : selectedBoardId;
        model.addAttribute("boards", bmcService.findAllGrouped(includeDeleted));
        model.addAttribute("includeDeleted", includeDeleted);
        model.addAttribute("selectId", selectId);
        model.addAttribute("selectBoardId", initialBoardId);
        return "management/bmc/list";
    }

    @GetMapping("/{boardId}/new")
    public String newForm(@PathVariable("boardId") Long boardId, Model model) {
        BoardModelResponse board = boardModelService.findById(boardId);
        model.addAttribute("bmcForm", new BmcCreateRequest("", "", "", "", false, ""));
        populateFormContext(model, boardId, null, board);
        return "management/bmc/bmc-new";
    }

    @PostMapping(path = "/{boardId}/upload-intent")
    @ResponseBody
    public ResponseEntity<?> intent(@PathVariable("boardId") Long boardId,
                                    @Valid @RequestBody BmcUploadIntentRequest request,
                                    BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String msg = bindingResult.getFieldErrors().stream()
                    .findFirst()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .orElse("입력값이 올바르지 않습니다.");
            return ResponseEntity.badRequest().body(new ApiErrorResponse(msg));
        }
        try {
            BmcUploadIntentResponse body = bmcUploadIntentService.issue(boardId, request);
            return ResponseEntity.ok(body);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(e.getMessage()));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(e.getMessage()));
        } catch (DomainException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(e.getMessage()));
        }
    }

    @PostMapping(path = "/{boardId}/upload")
    @ResponseBody
    public ResponseEntity<?> uploadBundle(@PathVariable("boardId") Long boardId,
                                          @Valid @ModelAttribute BmcCreateRequest request,
                                          BindingResult bindingResult,
                                          @RequestParam("uploadMode") BmcUploadMode uploadMode,
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
            bmcUploadIntentService.consume(boardId, uploadToken);
            Long id = bmcService.addBmc(boardId, request, uploadMode, folderFiles, zipFile, singleFile);
            return ResponseEntity.ok(new BmcUploadResponse(id, "/management/bmc?selectId=" + id));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(e.getMessage()));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(e.getMessage()));
        } catch (DomainException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/{boardId}/bmc/{bmcId}/edit")
    public String editForm(@PathVariable("boardId") Long boardId,
                           @PathVariable("bmcId") Long bmcId,
                           Model model) {
        BmcResponse bmc = bmcService.findBmc(boardId, bmcId);
        BoardModelResponse board = boardModelService.findById(boardId);
        model.addAttribute("bmcForm", new BmcUpdateRequest(
                bmc.name(),
                bmc.version(),
                nullToEmpty(bmc.description())
        ));
        model.addAttribute("treeRootPath", bmc.treeRootPath());
        model.addAttribute("entrypointRelativePath", bmc.entrypointRelativePath());
        populateFormContext(model, boardId, bmcId, board);
        return "management/bmc/bmc-edit";
    }

    @PostMapping("/{boardId}/bmc/{bmcId}/edit")
    public String update(@PathVariable("boardId") Long boardId,
                         @PathVariable("bmcId") Long bmcId,
                         @Valid @ModelAttribute("bmcForm") BmcUpdateRequest request,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            BmcResponse bmc = bmcService.findBmc(boardId, bmcId);
            BoardModelResponse board = boardModelService.findById(boardId);
            model.addAttribute("treeRootPath", bmc.treeRootPath());
            model.addAttribute("entrypointRelativePath", bmc.entrypointRelativePath());
            populateFormContext(model, boardId, bmcId, board);
            return "management/bmc/bmc-edit";
        }
        bmcService.update(boardId, bmcId, request);
        return redirectToListWithSelect(bmcId);
    }

    @PostMapping("/{boardId}/bmc/{bmcId}/toggle")
    public String toggle(@PathVariable("boardId") Long boardId,
                         @PathVariable("bmcId") Long bmcId) {
        bmcService.toggleEnabled(boardId, bmcId);
        return redirectToListWithSelect(bmcId);
    }

    @PostMapping("/{boardId}/bmc/{bmcId}/delete")
    public String delete(@PathVariable("boardId") Long boardId,
                         @PathVariable("bmcId") Long bmcId) {
        bmcService.softDelete(boardId, bmcId);
        return "redirect:/management/bmc?selectBoardId=" + boardId;
    }

    @PostMapping("/{boardId}/bmc/{bmcId}/restore")
    public String restore(@PathVariable("boardId") Long boardId,
                          @PathVariable("bmcId") Long bmcId) {
        bmcService.restore(boardId, bmcId);
        return redirectToListWithSelect(bmcId);
    }

    @PostMapping(path = "/{boardId}/bmc/{bmcId}/verify")
    @ResponseBody
    public JobStartResponse verify(@PathVariable("boardId") Long boardId,
                                   @PathVariable("bmcId") Long bmcId) {
        String jobId = bmcVerificationLauncher.startVerification(boardId, bmcId);
        return new JobStartResponse(jobId);
    }

    @GetMapping(path = "/{boardId}/bmc/{bmcId}/integrity-status")
    @ResponseBody
    public IntegrityStatusResponse integrityStatus(@PathVariable("boardId") Long boardId,
                                                   @PathVariable("bmcId") Long bmcId) {
        return bmcService.findIntegrityStatus(boardId, bmcId);
    }

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

    private void populateFormContext(Model model, Long boardId, Long bmcId, BoardModelResponse board) {
        model.addAttribute("boardId", boardId);
        model.addAttribute("bmcId", bmcId);
        model.addAttribute("contextLabel", board.vendor().getDisplayName() + " · " + board.modelName());
    }

    private String redirectToListWithSelect(Long selectId) {
        return "redirect:/management/bmc?selectId=" + selectId;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
