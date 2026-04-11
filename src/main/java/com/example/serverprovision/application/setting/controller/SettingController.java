package com.example.serverprovision.application.setting.controller;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.dto.SettingCreateRequest;
import com.example.serverprovision.application.setting.dto.SettingCreateResponse;
import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.application.setting.service.SettingService;
import com.example.serverprovision.domain.board.service.BoardModelService;
import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.installation.LinuxInstallation;
import com.example.serverprovision.domain.os.model.installation.PartitionPreset;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;


/**
 * 세팅 주문서 생성 UI와 REST API를 제공하는 혼합형 Controller이다.
 *
 * <p>역할: {@code GET /pxe/v1/setting/new}로 세팅 주문서 작성 폼 페이지를 렌더링하고,
 * {@code POST /pxe/v1/setting/api/new}로 프론트엔드 Fetch API가 전송한 JSON 요청을
 * 수신하여 {@link SettingService#save}에 위임한다. OS 파티션 프리셋 조회용
 * {@code GET /pxe/v1/setting/api/default-partitions} 엔드포인트도 함께 제공한다.</p>
 *
 * <p>유스케이스: 관리자가 브라우저에서 {@code /pxe/v1/setting/new}에 접근하면
 * {@link #newSetting}이 {@link BoardModelService#getViewModelList}와
 * {@link OSMetadataService#getInstallationViewList}를 호출하여 폼에 필요한 선택지
 * (보드 모델 목록, OS 메타데이터 목록, 파일 시스템 목록)를 모델에 담아 반환한다.
 * 폼 제출 시 JS가 JSON을 조립해 {@link #createSetting}에 POST하면, {@link SettingService}가
 * {@link com.example.serverprovision.application.setting.domain.entity.ServerSetting}을
 * 생성·저장하고 컨트롤러는 201 Created 응답을 반환한다.</p>
 *
 * <p>확장 가이드: {@code GET /pxe/v1/setting/{id}} 조회 엔드포인트는 후속 사이클에서 추가
 * 예정이다. 추가 시 {@link #createSetting}의 {@code Location} 헤더가 자동으로 유효화된다.
 * 새로운 프로세스 단계 지원 시 폼 페이지({@code setting/new.html})와
 * {@link com.example.serverprovision.application.setting.model.request.AbstractProcessRequest}
 * 하위 클래스를 함께 추가하고, 대응하는 {@link com.example.serverprovision.application.setting.service.resolver.SettingProcessResolver}
 * 구현체를 {@code @Component}로 등록해야 한다.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/pxe/v1/setting")
public class SettingController {

    private final SettingService settingService;
    private final BoardModelService boardModelService;
    private final OSMetadataService osMetadataService;

    /**
     * 세팅 주문서 작성 폼 페이지를 렌더링한다.
     *
     * <p>역할: 세팅 주문서 생성에 필요한 선택지 데이터를 {@code Model}에 추가하고
     * {@code setting/new} 뷰를 반환한다.</p>
     *
     * <p>유스케이스: 관리자가 {@code /pxe/v1/setting/new}에 접근할 때 호출된다.
     * {@code settingProcessStepList}에는 {@link SettingProcessStep} 전체 상수 목록,
     * {@code boardViewList}에는 보드 모델·BIOS·BMC 선택지,
     * {@code osInstallationViewList}에는 OS 메타데이터·환경·패키지 그룹 선택지,
     * {@code fileSystems}에는 파티션 파일 시스템 선택지가 담긴다.
     * Thymeleaf 템플릿은 {@code boardViewList}의 BIOS/BMC 목록을
     * {@code data-bios}, {@code data-bmc} 속성에 JSON으로 직렬화하여 JS가 파싱한다.</p>
     *
     * <p>확장 가이드: 폼에 새 선택지 데이터가 필요하면 해당 Service를 주입하고
     * 여기서 {@code model.addAttribute}로 추가한 뒤, {@code setting/new.html}
     * 템플릿에도 해당 항목을 반영한다.</p>
     *
     * @param model Thymeleaf 뷰에 전달할 데이터 컨테이너
     * @return 뷰 이름 {@code "setting/new"}
     */
    @GetMapping("/new")
    public String newSetting(Model model) {

        // 전체 설정 프로세스
        model.addAttribute("settingProcessStepList", List.of(SettingProcessStep.values()));

        // BIOS/BMC 업데이트
        model.addAttribute("boardViewList", boardModelService.getViewModelList());

        // OS 설치, 세팅 — 환경·패키지 그룹 포함 뷰 데이터
        model.addAttribute("osInstallationViewList", osMetadataService.getInstallationViewList());
        model.addAttribute("fileSystems", FileSystem.values());
        return "setting/new";
    }

    /**
     * OS별 권장 파티션 프리셋 목록을 반환한다.
     *
     * <p>역할: 선택된 OS 이름에 맞는 {@link PartitionPreset} 목록을
     * {@link LinuxInstallation#getDefaultPartitions}에서 조회하여 JSON으로 반환한다.</p>
     *
     * <p>유스케이스: 프론트엔드의 "기본 파티션 자동 생성" 버튼이 이 엔드포인트를 호출하여
     * 파티션 테이블을 초기화한다. {@code size} / {@code sizeUnit}이 {@code null}인
     * 프리셋 항목은 크기를 사용자가 직접 입력해야 함을 나타낸다.
     * {@code osName}이 {@link OSName} 열거형 상수와 일치하지 않으면 400 Bad Request를 반환한다.</p>
     *
     * <p>확장 가이드: 새 OS 타입을 지원할 때 {@link OSName}에 상수를 추가하고,
     * {@link LinuxInstallation#getDefaultPartitions} 내부 switch에 해당 case를
     * 추가하면 이 엔드포인트는 자동으로 해당 프리셋을 반환한다.</p>
     *
     * @param osName OS 이름 문자열 (예: {@code "ROCKY_LINUX"}). {@link OSName} 상수명과 일치해야 한다.
     * @return 200 OK + 프리셋 목록, 또는 400 Bad Request (알 수 없는 OS 이름)
     */
    @GetMapping("/api/default-partitions")
    @ResponseBody
    public ResponseEntity<List<PartitionPreset>> getDefaultPartitions(@RequestParam String osName) {
        OSName os;
        try {
            os = OSName.valueOf(osName);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(LinuxInstallation.getDefaultPartitions(os));
    }

    /**
     * 세팅 주문서 생성 REST 엔드포인트이다.
     *
     * <p>역할: 프론트엔드 Fetch API가 JSON으로 전송한 {@link SettingCreateRequest}를
     * {@link SettingService#save}에 위임하여 {@link com.example.serverprovision.application.setting.domain.entity.ServerSetting}을
     * 생성·저장하고, {@link SettingCreateResponse}와 201 Created 응답을 반환한다.</p>
     *
     * <p>유스케이스: {@code setting/new.html}의 JS가 폼 데이터를 JSON으로 조립하여
     * {@code POST /pxe/v1/setting/api/new}로 전송할 때 호출된다. {@code @Valid}에 의해
     * Bean Validation이 먼저 수행되고, Resolver 레벨 검증 실패 시
     * {@link com.example.serverprovision.global.exception.FieldValidationException}이 발생하여
     * {@link com.example.serverprovision.global.exception.GlobalExceptionHandler}가 처리한다.
     * Location 헤더에는 {@code /pxe/v1/setting/{id}} 경로가 설정되어 후속 조회 엔드포인트
     * 추가 시 자동으로 유효화된다.</p>
     *
     * <p>확장 가이드: 반환 DTO에 필드를 추가할 경우 {@link SettingCreateResponse}를 수정한다.
     * 조회 엔드포인트({@code GET /pxe/v1/setting/{id}})를 추가하면 Location 헤더가
     * 자동으로 유효화되므로 Location URI 형식은 그대로 유지한다.</p>
     *
     * @param request 세팅 주문서 생성 요청 DTO. {@code @Valid}로 Bean Validation 적용.
     * @return 201 Created + {@link SettingCreateResponse} (id, name, status)
     */
    @PostMapping("/api/new")
    @ResponseBody
    public ResponseEntity<SettingCreateResponse> createSetting(@Valid @RequestBody SettingCreateRequest request) {
        log.info("[SettingController] 세팅 주문서 수신. name={}, 단계 수={}", request.name(), request.processList().size());
        ServerSetting saved = settingService.save(request);
        SettingCreateResponse body = SettingCreateResponse.from(saved);
        // Location 대상 `GET /pxe/v1/setting/{id}` 엔드포인트는 후속 사이클에서 추가 예정.
        // REST 원칙상 자원 생성 시점에 URI 를 채워두면 추후 엔드포인트가 붙는 순간 자동 유효화된다.
        URI location = URI.create("/pxe/v1/setting/" + body.id());
        return ResponseEntity.created(location).body(body);
    }

}
