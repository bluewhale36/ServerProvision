package com.example.serverprovision.application.setting.controller;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.dto.SettingCreateRequest;
import com.example.serverprovision.application.setting.dto.SettingCreateResponse;
import com.example.serverprovision.application.setting.dto.SettingUpdateResponse;
import com.example.serverprovision.application.setting.model.BasicUpdate;
import com.example.serverprovision.application.setting.model.OSInstallation;
import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.application.setting.model.enums.SettingStatus;
import com.example.serverprovision.application.setting.service.SettingService;
import com.example.serverprovision.domain.board.service.BoardModelService;
import com.example.serverprovision.domain.os.dto.OSPackageGroupDTO;
import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.example.serverprovision.domain.os.model.enums.OSFamily;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.installation.LinuxInstallation;
import com.example.serverprovision.domain.os.model.installation.PartitionPreset;
import com.example.serverprovision.domain.os.model.installation.RHELBasedInstallation;
import com.example.serverprovision.domain.os.model.installation.RockyLinux10Installation;
import com.example.serverprovision.domain.os.model.installation.UbuntuInstallation;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


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
    private final ObjectMapper objectMapper;

    /**
     * 모든 뷰에서 {@code ${osFamily.RHEL_BASED}} 형태로 {@link OSFamily} 상수명을 참조할 수 있게 주입한다.
     *
     * <p>{@code Map<상수명, 상수명>} 구조로 노출하는 이유: 템플릿 SpEL 에서 {@code ${osFamily.RHEL_BASED}}
     * 접근 시 {@link OSFamily} 열거형 자체가 아닌 값만 필요하기 때문이다. 동시에 Thymeleaf 의
     * restricted context(th:attr 등) 에서도 안전하게 평가된다.</p>
     */
    @ModelAttribute("osFamily")
    public Map<String, String> populateOSFamilyKeys() {
        return Arrays.stream(OSFamily.values())
                .collect(Collectors.toMap(Enum::name, Enum::name,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 모든 뷰에서 {@code ${osNameKey.ROCKY_LINUX}} 형태로 {@link OSName} 상수명을 참조할 수 있게 주입한다.
     *
     * <p>{@code OSAdminController.populateOSNames()} 가 {@code OSName[]} 을 그대로 노출하는 것과는
     * 용도가 다르다: 저쪽은 드롭다운 iteration 용, 여기는 특정 상수명을 키로 참조하기 위한 맵이다.</p>
     */
    @ModelAttribute("osNameKey")
    public Map<String, String> populateOSNameKeys() {
        return Arrays.stream(OSName.values())
                .collect(Collectors.toMap(Enum::name, Enum::name,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 세팅 주문서 목록 페이지를 렌더링한다.
     *
     * <p>역할: DB에 저장된 모든 {@link ServerSetting}을 생성일시 내림차순으로 조회하여
     * {@code Model}에 추가하고 {@code setting/list} 뷰를 반환한다.</p>
     *
     * <p>유스케이스: 관리자가 {@code /pxe/v1/setting}에 접근할 때 호출된다.
     * 주문서가 없는 경우 템플릿의 빈 상태(empty state) 블록이 렌더링된다.</p>
     *
     * @param model Thymeleaf 뷰에 전달할 데이터 컨테이너
     * @return 뷰 이름 {@code "setting/list"}
     */
    @GetMapping
    public String listSettings(Model model) {
        model.addAttribute("settings", settingService.findAll());
        return "setting/list";
    }

    /**
     * 세팅 주문서 상세 페이지를 렌더링한다.
     *
     * <p>역할: 주어진 {@code id}에 해당하는 {@link ServerSetting}을 조회하여
     * {@code Model}에 추가하고 {@code setting/detail} 뷰를 반환한다.</p>
     *
     * <p>유스케이스: 목록 페이지({@code /pxe/v1/setting})에서 주문서 명칭을 클릭할 때 호출된다.
     * 해당 id의 주문서가 없으면 404 응답이 반환된다.</p>
     *
     * @param id    조회할 세팅 주문서의 PK
     * @param model Thymeleaf 뷰에 전달할 데이터 컨테이너
     * @return 뷰 이름 {@code "setting/detail"}
     */
    @GetMapping("/{id}")
    public String detailSetting(@PathVariable Long id, Model model) {
        var setting = settingService.findById(id);
        // 존재하지 않는 ID는 목록 페이지로 리다이렉트
        if (setting.isEmpty()) {
            return "redirect:/pxe/v1/setting";
        }
        model.addAttribute("setting", setting.get());
        return "setting/detail";
    }

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
     * 세팅 주문서 수정 폼 페이지를 렌더링한다.
     *
     * <p>역할: PENDING 상태인 주문서에 한해 수정 폼을 렌더링한다.
     * 주문서가 없거나 PENDING이 아닌 경우 적절한 페이지로 redirect한다.
     * 기존 데이터는 {@link #buildInitialJson}으로 JSON 직렬화하여 모델에 담는다.</p>
     *
     * <p>유스케이스: 상세 페이지({@code /pxe/v1/setting/{id}})의 "수정" 버튼 클릭 시 호출된다.
     * PENDING이 아닌 주문서는 상세 페이지로 redirect하여 수정 폼 진입을 차단한다.</p>
     *
     * @param id    수정할 세팅 주문서의 PK
     * @param model Thymeleaf 뷰에 전달할 데이터 컨테이너
     * @return 뷰 이름 {@code "setting/edit"}, 또는 redirect URI
     */
    @GetMapping("/{id}/edit")
    public String editSetting(@PathVariable Long id, Model model) {
        var setting = settingService.findById(id);
        if (setting.isEmpty()) return "redirect:/pxe/v1/setting";
        ServerSetting s = setting.get();
        if (s.getStatus() != SettingStatus.PENDING) return "redirect:/pxe/v1/setting/" + id;

        model.addAttribute("settingProcessStepList", List.of(SettingProcessStep.values()));
        model.addAttribute("boardViewList", boardModelService.getViewModelList());
        model.addAttribute("osInstallationViewList", osMetadataService.getInstallationViewList());
        model.addAttribute("fileSystems", FileSystem.values());
        model.addAttribute("setting", s);
        model.addAttribute("initialSettingJson", buildInitialJson(s));
        return "setting/edit";
    }

    /**
     * 세팅 주문서 수정 REST 엔드포인트이다.
     *
     * <p>역할: {@link SettingCreateRequest}와 동일한 JSON 스키마를 받아
     * {@link SettingService#update}에 위임하고 200 OK + {@link SettingUpdateResponse}를 반환한다.</p>
     *
     * <p>유스케이스: {@code setting/edit.html}의 JS가 PUT 요청을 전송할 때 호출된다.
     * PENDING이 아닌 주문서 수정 시도 시 409 Conflict가 반환된다.</p>
     *
     * @param id      수정할 세팅 주문서의 PK
     * @param request 수정 요청 DTO. {@code @Valid}로 Bean Validation 적용.
     * @return 200 OK + {@link SettingUpdateResponse} (id, name, status)
     */
    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<SettingUpdateResponse> updateSetting(
            @PathVariable Long id,
            @Valid @RequestBody SettingCreateRequest request) {
        log.info("[SettingController] 세팅 주문서 수정 수신. id={}, name={}", id, request.name());
        ServerSetting updated = settingService.update(id, request);
        return ResponseEntity.ok(SettingUpdateResponse.from(updated));
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

    /**
     * {@link ServerSetting}의 기존 프로세스 목록을 JS pre-fill용 JSON으로 역변환한다.
     *
     * <p>역할: {@link #editSetting}에서 수정 폼에 기존 값을 초기화(pre-fill)할 때
     * 사용할 JSON 문자열을 생성한다. 각 프로세스 타입에서 필요한 ID와 값을 추출하여
     * JS가 파싱 가능한 구조로 반환한다.</p>
     *
     * <p>보안 주의: 비밀번호(rootPassword, users[].password)는 보안상 포함하지 않는다.
     * timezone은 도메인 모델에 저장되지 않으므로 포함할 수 없다.</p>
     *
     * @param setting pre-fill 대상 세팅 주문서 엔티티
     * @return JSON 문자열. 직렬화 실패 시 {@code "{}"}
     */
    private String buildInitialJson(ServerSetting setting) {
        if (setting.getSettingProcess() == null) return "{}";
        List<Map<String, Object>> processList = setting.getSettingProcess().processList()
                .stream().map(this::toInitialMap).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", setting.getName());
        payload.put("processList", processList);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[SettingController] initialSettingJson 직렬화 실패. id={}", setting.getId(), e);
            return "{}";
        }
    }

    /**
     * 단일 {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}를
     * JS pre-fill용 {@link Map}으로 변환한다.
     *
     * @param process 변환할 프로세스 단계 도메인 모델
     * @return JS가 파싱 가능한 key-value 맵
     */
    private Map<String, Object> toInitialMap(
            com.example.serverprovision.application.setting.model.AbstractSettingProcess process) {
        return switch (process.getProcessStep()) {
            case BASIC_UPDATE -> {
                BasicUpdate bu = (BasicUpdate) process;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "BASIC_UPDATES");
                m.put("boardModelId", bu.getBoardModel().id());
                m.put("boardBIOSId", bu.getBoardBIOS().id());
                m.put("boardBMCId", bu.getBoardBMC().id());
                yield m;
            }
            case BASIC_SETTING -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "BASIC_SETTING");
                yield m;
            }
            case OS_INSTALLATION -> {
                OSInstallation oi = (OSInstallation) process;
                // 도메인 설치 모델의 실제 타입으로 분기: RHEL 계열 vs Ubuntu.
                // 공통 필드(partitions, users, rootPassword 메타)는 LinuxInstallation 레벨에서 접근,
                // family 고유 필드는 인스턴스 타입별 분기로 채운다.
                LinuxInstallation base = (LinuxInstallation) oi.getOsInstallation();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "OS_INSTALLATION");
                m.put("osMetadataId", oi.getOsMetadata().id());

                // family 판별자: JS 가 pane 활성화 및 payload 구성 시 참조.
                m.put("osFamily", oi.getOsMetadata().osName().getFamily().name());

                if (base instanceof RHELBasedInstallation rli) {
                    m.put("isKDumpEnabled", rli.isKDumpEnabled());
                    m.put("environmentId",
                            rli.getEnvironment() != null && rli.getEnvironment().getOsEnvironment() != null
                            ? rli.getEnvironment().getOsEnvironment().id() : null);
                    m.put("packageGroupIds",
                            rli.getEnvironment() != null
                            ? rli.getEnvironment().getPackageGroups().stream()
                                    .map(OSPackageGroupDTO::id).toList()
                            : List.of());
                    // Rocky 10 전용 allowSshRoot: 다른 RHEL 버전은 포함하지 않음
                    if (rli instanceof RockyLinux10Installation r10) {
                        m.put("allowSshRoot", r10.isAllowSshRoot());
                    }
                    if (rli.getTimezone() != null) {
                        m.put("timezone", rli.getTimezone().getTimezone());
                        m.put("isUTC", rli.getTimezone().isUTC());
                    }
                } else if (base instanceof UbuntuInstallation ui) {
                    m.put("hostname", ui.getHostname() != null ? ui.getHostname() : "");
                    m.put("packages", ui.getPackages() != null ? ui.getPackages() : List.of());
                    if (ui.getTimezone() != null) {
                        m.put("timezone", ui.getTimezone().getTimezone());
                        m.put("isUTC", ui.getTimezone().isUTC());
                    }
                }

                m.put("partitions", base.getPartitions().stream().map(p -> {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("mountPoint", p.getMountPoint());
                    pm.put("fileSystem", p.getFileSystem().name());
                    pm.put("diskName", p.getDiskName() != null ? p.getDiskName() : "");
                    pm.put("size", p.getSizeInMB());
                    pm.put("sizeUnit", "MB");
                    pm.put("grow", p.isGrow());
                    return pm;
                }).toList());
                m.put("users", base.getUsers().stream().map(u -> {
                    Map<String, Object> um = new LinkedHashMap<>();
                    um.put("username", u.getUsername());
                    um.put("isSudoer", u.isSudoer());
                    um.put("isPasswordEncrypted", u.isPasswordEncrypted());
                    // password: 보안상 pre-fill 불가 — JS에서 빈 값으로 표시
                    return um;
                }).toList());
                m.put("hasRootPassword", base.getRootPassword() != null);
                m.put("rootPasswordEncrypted",
                        base.getRootPassword() != null && base.getRootPassword().isPasswordEncrypted());
                yield m;
            }
            case OS_SETTING -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "OS_SETTING");
                yield m;
            }
        };
    }

}
