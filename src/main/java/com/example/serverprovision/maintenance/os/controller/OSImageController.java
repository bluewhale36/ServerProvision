package com.example.serverprovision.maintenance.os.controller;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.exception.DomainException;
import com.example.serverprovision.global.exception.NotFoundException;
import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.maintenance.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.maintenance.os.dto.request.ISOUpdateRequest;
import com.example.serverprovision.maintenance.os.dto.request.IsoUploadIntentRequest;
import com.example.serverprovision.maintenance.os.dto.request.OSImageCreateRequest;
import com.example.serverprovision.maintenance.os.dto.request.OSImageUpdateRequest;
import com.example.serverprovision.maintenance.os.dto.response.ApiErrorResponse;
import com.example.serverprovision.maintenance.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.maintenance.os.dto.response.ISOResponse;
import com.example.serverprovision.maintenance.os.dto.response.IsoUploadResponse;
import com.example.serverprovision.maintenance.os.dto.response.OSImageResponse;
import com.example.serverprovision.maintenance.os.enums.OSName;
import com.example.serverprovision.maintenance.os.service.CompsExtractionLauncher;
import com.example.serverprovision.maintenance.os.service.IsoUploadIntentService;
import com.example.serverprovision.maintenance.os.service.OSImageService;
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
 * A1. OS 이미지 관리 MVC 컨트롤러.
 * <ul>
 *   <li>뷰에는 Request / Response 만 넘긴다 (엔티티 직접 노출 금지).</li>
 *   <li>성공 시 {@code /pxe/v1/maintenance/os?selectId=...} 로 리다이렉트해 Miller 초기 선택을 복원한다.</li>
 *   <li>검증 실패({@code BindingResult}) 는 같은 폼 뷰로 돌아가 Thymeleaf 의 field errors 를 렌더한다.</li>
 *   <li>도메인 예외({@code NotFound}, {@code Conflict}) 는 {@link com.example.serverprovision.global.exception.GlobalExceptionHandler} 가 처리한다.</li>
 *   <li>장시간 실행 작업(추출·업로드) 은 Stage S1 의 {@code BackgroundJobService} 를 거쳐 알림 센터로 통합 노출된다.</li>
 * </ul>
 */
@Controller
@RequestMapping("/pxe/v1/maintenance/os")
@RequiredArgsConstructor
public class OSImageController {

    /** A1 MVP 시점에 등록 가능한 OS 이름 — 나머지 2종(WINDOWS 계열) 은 Stage 3 에서 열린다. */
    private static final List<OSName> MVP_OS_NAMES = List.of(
            OSName.ROCKY_LINUX, OSName.CENTOS, OSName.UBUNTU
    );

    private final OSImageService osImageService;
    private final CompsExtractionLauncher compsExtractionLauncher;
    private final IsoUploadIntentService isoUploadIntentService;

    // ==== 목록 ========================================================

    @GetMapping
    public String list(@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
                       @RequestParam(name = "selectId", required = false) Long selectId,
                       Model model) {
        model.addAttribute("osGroups", osImageService.findAllGrouped(includeDeleted));
        model.addAttribute("includeDeleted", includeDeleted);
        model.addAttribute("selectId", selectId);
        return "maintenance/os/list";
    }

    // ==== OS 이미지 신규 ==============================================

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("osImageForm", new OSImageCreateRequest(null, "", ""));
        model.addAttribute("osNameOptions", MVP_OS_NAMES);
        return "maintenance/os/new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("osImageForm") OSImageCreateRequest request,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("osNameOptions", MVP_OS_NAMES);
            return "maintenance/os/new";
        }
        Long id = osImageService.create(request);
        return redirectToListWithSelect(id);
    }

    // ==== OS 이미지 수정 ==============================================

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        OSImageResponse image = osImageService.findById(id);
        model.addAttribute("osImageForm", new OSImageUpdateRequest(
                image.osVersion(),
                nullToEmpty(image.description())
        ));
        model.addAttribute("osImageId", id);
        model.addAttribute("osNameLabel", image.osName().getDisplayName());
        return "maintenance/os/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("osImageForm") OSImageUpdateRequest request,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            // 검증 실패 시 다시 렌더 — osNameLabel 만 보조 속성으로 채워준다 (폼 값은 BindingResult 가 보존).
            OSImageResponse image = osImageService.findById(id);
            model.addAttribute("osImageId", id);
            model.addAttribute("osNameLabel", image.osName().getDisplayName());
            return "maintenance/os/edit";
        }
        osImageService.update(id, request);
        return redirectToListWithSelect(id);
    }

    // ==== OS 이미지 상태 전이 =========================================

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        osImageService.toggleEnabled(id);
        return redirectToListWithSelect(id);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        osImageService.softDelete(id);
        // 삭제된 항목은 기본 보기에서 사라지므로 선택 복원 없이 전체 목록으로 이동
        return "redirect:/pxe/v1/maintenance/os";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id) {
        osImageService.restore(id);
        return redirectToListWithSelect(id);
    }

    // ==== ISO ==========================================================

    @GetMapping("/{osId}/iso/new")
    public String newIsoForm(@PathVariable("osId") Long osId, Model model) {
        OSImageResponse os = osImageService.findById(osId);
        model.addAttribute("isoForm", new ISOCreateRequest("", "", false));
        populateIsoFormContext(model, osId, null, os);
        return "maintenance/os/iso-new";
    }

    @PostMapping("/{osId}/iso")
    public String addIso(@PathVariable("osId") Long osId,
                         @Valid @ModelAttribute("isoForm") ISOCreateRequest request,
                         BindingResult bindingResult,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         Model model) {
        if (bindingResult.hasErrors()) {
            OSImageResponse os = osImageService.findById(osId);
            populateIsoFormContext(model, osId, null, os);
            return "maintenance/os/iso-new";
        }
        osImageService.addISO(osId, request, file);
        return redirectToListWithSelect(osId);
    }

    /**
     * 업로드 Intent 핸드셰이크 — 실제 XHR 전송 전 사전 검증.
     * 하드 조건 (동일 isoPath 의 활성 ISO 가 이미 있음) 에 걸리면 409 로 즉시 거절되어
     * 바이트 전송 자체가 일어나지 않는다. 성공 시 응답의 {@code uploadToken} 을
     * 실제 업로드 요청의 {@code X-Upload-Token} 헤더에 실어야 한다.
     * <p>모든 예외 분기는 JSON {@link ApiErrorResponse} 로 감싸서 반환한다 — 전역 핸들러의
     * HTML 에러 뷰 경로로 빠지면 클라이언트가 실제 사유 메시지를 파싱하지 못해
     * "사전 검증 실패" 같은 모호한 fallback 만 보이게 된다.</p>
     */
    @PostMapping(path = "/{osId}/iso/upload-intent")
    @ResponseBody
    public ResponseEntity<?> intent(@PathVariable("osId") Long osId,
                                    @Valid @RequestBody IsoUploadIntentRequest request,
                                    BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String msg = bindingResult.getFieldErrors().stream()
                    .findFirst()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .orElse("입력값이 올바르지 않습니다.");
            return ResponseEntity.badRequest().body(new ApiErrorResponse(msg));
        }
        try {
            IsoUploadIntentResponse body = isoUploadIntentService.issue(osId, request);
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
     * JS XHR 업로드 전용 REST 엔드포인트 (옵션 A 경로).
     * 클라이언트가 foreground XHR 로 업로드를 수행하며 실시간 진행률을 페이지 내 바에 표시한다.
     * <ul>
     *   <li>{@code X-Upload-Token} 헤더의 intent 토큰을 서버에서 1회용으로 소비한다.</li>
     *   <li>최후 SHA-256 체크섬 검증은 {@link OSImageService#addISO} 가 그대로 수행한다 —
     *       intent 의 사전 방어선과 최후 방어선이 이중으로 작동한다.</li>
     * </ul>
     * 도메인 예외는 메서드 내부에서 JSON 으로 감싸 {@link com.example.serverprovision.global.exception.GlobalExceptionHandler}
     * 의 HTML 뷰 경로로 빠지지 않게 한다.
     */
    @PostMapping(path = "/{osId}/iso/upload")
    @ResponseBody
    public ResponseEntity<?> uploadIso(@PathVariable("osId") Long osId,
                                       @Valid @ModelAttribute ISOCreateRequest request,
                                       BindingResult bindingResult,
                                       @RequestParam(value = "file", required = false) MultipartFile file,
                                       @RequestHeader(name = "X-Upload-Token", required = false) String uploadToken) {
        if (bindingResult.hasErrors()) {
            String msg = bindingResult.getFieldErrors().stream()
                    .findFirst()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .orElse("입력값이 올바르지 않습니다.");
            return ResponseEntity.badRequest().body(new ApiErrorResponse(msg));
        }
        try {
            // 1차 방어선 : intent 토큰 소비. 없거나 만료됐거나 타 OS 면 즉시 거절.
            isoUploadIntentService.consume(osId, uploadToken);

            // 2차 방어선 : OSImageService 가 스트리밍 SHA-256 계산 → DB 중복 체크섬 확인 → 저장.
            Long id = osImageService.addISO(osId, request, file);
            String redirect = "/pxe/v1/maintenance/os?selectId=" + osId;
            return ResponseEntity.ok(new IsoUploadResponse(id, redirect));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(e.getMessage()));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(e.getMessage()));
        } catch (DomainException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/{osId}/iso/{isoId}/edit")
    public String editIsoForm(@PathVariable("osId") Long osId,
                              @PathVariable("isoId") Long isoId,
                              Model model) {
        ISOResponse iso = osImageService.findISO(osId, isoId);
        OSImageResponse os = osImageService.findById(osId);
        model.addAttribute("isoForm", new ISOUpdateRequest(iso.isoPath(), nullToEmpty(iso.description())));
        populateIsoFormContext(model, osId, isoId, os);
        return "maintenance/os/iso-edit";
    }

    @PostMapping("/{osId}/iso/{isoId}/edit")
    public String updateIso(@PathVariable("osId") Long osId,
                            @PathVariable("isoId") Long isoId,
                            @Valid @ModelAttribute("isoForm") ISOUpdateRequest request,
                            BindingResult bindingResult,
                            Model model) {
        if (bindingResult.hasErrors()) {
            OSImageResponse os = osImageService.findById(osId);
            populateIsoFormContext(model, osId, isoId, os);
            return "maintenance/os/iso-edit";
        }
        osImageService.updateISO(osId, isoId, request);
        return redirectToListWithSelect(osId);
    }

    @PostMapping("/{osId}/iso/{isoId}/toggle")
    public String toggleIso(@PathVariable("osId") Long osId,
                            @PathVariable("isoId") Long isoId) {
        osImageService.toggleIsoEnabled(osId, isoId);
        return redirectToListWithSelect(osId);
    }

    @PostMapping("/{osId}/iso/{isoId}/delete")
    public String deleteIso(@PathVariable("osId") Long osId,
                            @PathVariable("isoId") Long isoId) {
        osImageService.softDeleteISO(osId, isoId);
        return redirectToListWithSelect(osId);
    }

    @PostMapping("/{osId}/iso/{isoId}/restore")
    public String restoreIso(@PathVariable("osId") Long osId,
                             @PathVariable("isoId") Long isoId) {
        osImageService.restoreISO(osId, isoId);
        return redirectToListWithSelect(osId);
    }

    // ==== 설치 환경·패키지 그룹 추출 (A1-1) ===========================

    /**
     * 추출 시작. 동일 ISO 에 이미 활성 Job 이 있으면 새 Job 을 만들지 않고 기존 jobId 를 반환한다.
     * 프론트는 반환된 jobId 를 알림 센터(서류가방 드롭다운) 에서 추적한다.
     */
    @PostMapping("/{osId}/iso/{isoId}/extract")
    @ResponseBody
    public JobStartResponse startExtract(@PathVariable("osId") Long osId,
                                         @PathVariable("isoId") Long isoId) {
        String jobId = compsExtractionLauncher.startExtraction(osId, isoId);
        return new JobStartResponse(jobId);
    }

    /**
     * 환경·패키지 그룹 섹션만 렌더하는 Thymeleaf fragment.
     * 추출 Job 완료 시점에 클라이언트가 fetch 해서 해당 OS 의 상세 패널 안쪽 블록만 교체한다.
     * 전체 페이지 reload 를 피해 foreground 작업 흐름을 방해하지 않기 위한 경로.
     */
    @GetMapping("/{osId}/env-groups-fragment")
    public String envGroupsFragment(@PathVariable("osId") Long osId, Model model) {
        model.addAttribute("os", osImageService.findById(osId));
        return "maintenance/os/list :: envGroups";
    }

    /**
     * 단일 ISO 의 최신 제공 환경·패키지 그룹 정보 (JSON).
     * 추출 Job 완료 이벤트 수신 시 해당 ISO 의 아코디언 행 안쪽 "설치 환경"·"패키지 그룹" 값을
     * 다른 아이템의 펼침 상태를 건드리지 않고 부분 갱신하는 데 쓰인다. 전체 accordion 을 재렌더하면
     * 사용자가 열어둔 다른 항목이 닫히므로 최소 단위로 끊었다.
     */
    @GetMapping("/{osId}/iso/{isoId}/provisions")
    @ResponseBody
    public ISOResponse isoProvisions(@PathVariable("osId") Long osId,
                                     @PathVariable("isoId") Long isoId) {
        return osImageService.findISO(osId, isoId);
    }

    // ==== 헬퍼 =========================================================

    private String redirectToListWithSelect(Long selectId) {
        return "redirect:/pxe/v1/maintenance/os?selectId=" + selectId;
    }

    private void populateIsoFormContext(Model model, Long osId, Long isoId, OSImageResponse os) {
        model.addAttribute("osId", osId);
        // isoId 는 수정 폼 전용 키 — 신규 등록 때는 null 전달
        if (isoId != null) {
            model.addAttribute("isoId", isoId);
        }
        model.addAttribute("contextLabel", os.osName().getDisplayName() + " " + os.osVersion());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
