package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerListResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.provisioning.assignment.dto.request.AssignSettingRequest;
import com.example.serverprovision.provisioning.assignment.dto.request.ReassignSettingRequest;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentPlanResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.PlannedPhaseRailResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.ReassignmentResponse;
import com.example.serverprovision.provisioning.assignment.service.AssignmentCommandService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentStartService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 등록된 게스트 서버 조회 + 인라인 수정 + 회수 + 세팅 정의서 할당/개시 페이지 (사용자 영역 진입점).
 * 게스트 서버 애그리거트의 application service 는 execution 이 소유하고(U1 §D11), 할당 스냅샷은 provisioning
 * 이 소유한다(U3-1). 본 컨트롤러는 각 서비스만 호출한다(리포지토리 직접 주입 없음).
 * <ul>
 *   <li>{@code GET  /provisioning/server}                  — 목록</li>
 *   <li>{@code GET  /provisioning/server/{id}}              — 상세 (인라인 수정 + 할당 폼 + 계획 phase rail)</li>
 *   <li>{@code POST /provisioning/server/{id}/edit}         — 상세 화면의 4 필드 저장</li>
 *   <li>{@code POST /provisioning/server/{id}/decommission} — 서버 회수</li>
 *   <li>{@code POST /provisioning/server/{id}/assignment}          — 세팅 정의서 할당 (XHR JSON)</li>
 *   <li>{@code POST /provisioning/server/{id}/assignment/reassign} — 세팅 정의서 재할당 (XHR JSON, U3-2-a)</li>
 *   <li>{@code POST /provisioning/server/{id}/start}               — 프로비저닝 개시 (+ 활성 스냅샷 소비)</li>
 * </ul>
 */
@Controller
@RequestMapping("/provisioning/server")
@RequiredArgsConstructor
public class GuestServerController {

    private final GuestServerQueryService guestServerQueryService;
    private final GuestServerCommandService guestServerCommandService;
    private final AssignmentCommandService assignmentCommandService;
    private final AssignmentQueryService assignmentQueryService;
    private final AssignmentStartService assignmentStartService;
    private final SettingQueryService settingQueryService;
    private final GuestServerGroupQueryService groupQueryService;

    /**
     * 게스트 서버 목록 (U3-3) — 상대 시간 × 스펙으로 묶어 보여준다.
     *
     * <p>필터와 '등록 진행 중' 의 펼침이 <b>질의 파라미터</b>인 이유는 SSE 갱신 때문이다(DEC-E · DEC-H).
     * {@code server-stream.js} 는 신호를 받으면 {@code fetch(location.href)} 로 같은 URL 을 다시 받아
     * {@code [data-live]} 영역을 통째 교체한다. 상태가 URL 에 있으면 서버가 같은 화면을 다시 렌더해 주므로
     * 복원 코드가 필요 없다 — 반대로 화면에만 두면 갱신 때마다 지워진다.</p>
     *
     * <p>알 수 없는 {@code phase} 값은 Spring 의 enum 바인딩 실패로 400 이 된다. 새 분기를 만들지 않으며,
     * 주소창 진입이 HTML 오류 페이지를 받는 것은 S10 의 Accept 정규화가 보장한다.</p>
     */
    @GetMapping
    public String list(@RequestParam(value = "phase", required = false) ProvisioningPhase phase,
                       @RequestParam(value = "pending", required = false) String pending,
                       Model model) {
        GuestServerListResponse list = guestServerQueryService.findGrouped(phase);
        model.addAttribute("list", list);
        model.addAttribute("phaseFilter", phase);
        model.addAttribute("phases", ProvisioningPhase.values());
        model.addAttribute("pendingOpen", "open".equals(pending));
        // U3-4 — 소속 그룹 배지. 목록 조회는 execution 이고 그룹은 provisioning 이라 요약 응답에 실을 수 없다(DEC-C).
        // 이 컨트롤러가 이미 provisioning 이므로 두 서비스를 각각 부른 뒤 모델 단계에서 합성한다 — SPI 역전 불요.
        model.addAttribute("groupBadges", groupQueryService.findBadges(visibleServerIds(list)));
        return "provisioning/server-list";
    }

    /** 화면에 실제로 그려지는 서버들 — 그룹 배지는 이들만 있으면 된다. */
    private List<UUID> visibleServerIds(GuestServerListResponse list) {
        Stream<GuestServerSummaryResponse> pending = list.pending() == null
                ? Stream.of()
                : Stream.concat(list.pending().registeredOnly().stream(), list.pending().collecting().stream());
        Stream<GuestServerSummaryResponse> grouped = list.timeGroups().stream()
                .flatMap(tg -> tg.specGroups().stream())
                .flatMap(sg -> sg.servers().stream());
        return Stream.concat(pending, grouped).map(GuestServerSummaryResponse::id).toList();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") UUID id, Model model) {
        GuestServerDetailResponse server = guestServerQueryService.findDetail(id);
        addDetailModel(model, id, server);
        // 수정 폼 초깃값 — 상세 응답의 현재 값으로 채운다 (4 필드 모두 guest_server).
        model.addAttribute("updateForm", new UpdateGuestServerRequest(
                server.name(), server.modelName(), server.serialNumber(), server.memo()));
        return "provisioning/server-detail";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable("id") UUID id,
            @Valid @ModelAttribute("updateForm") UpdateGuestServerRequest request,
            BindingResult bindingResult,
            Model model
    ) {
        // 유니크 컬럼(name / serial_number) 의 외부 상태 검증 → 인라인 필드 에러로 표시 (예외 아님).
        if (StringUtils.hasText(request.name())
                && guestServerCommandService.isNameTakenByOther(id, request.name().trim())) {
            bindingResult.rejectValue("name", "duplicate", "이미 사용 중인 이름입니다.");
        }
        if (StringUtils.hasText(request.serialNumber())
                && guestServerCommandService.isSerialTakenByOther(id, request.serialNumber().trim())) {
            bindingResult.rejectValue("serialNumber", "duplicate", "이미 사용 중인 시리얼 번호입니다.");
        }

        if (bindingResult.hasErrors()) {
            // 검증 실패 — 같은 상세 화면을 다시 렌더(읽기 전용 영역 복원 + 입력값/에러 유지).
            addDetailModel(model, id, guestServerQueryService.findDetail(id));
            return "provisioning/server-detail";
        }

        guestServerCommandService.update(id, request);
        return "redirect:/provisioning/server/" + id;
    }

    @PostMapping("/{id}/decommission")
    public String decommission(@PathVariable("id") UUID id) {
        guestServerCommandService.decommission(id);
        return "redirect:/provisioning/server/" + id;
    }

    /**
     * 세팅 정의서 할당(U3-1, XHR JSON). 정상 흐름은 뷰가 활성 할당이 있으면 폼을 숨겨 1차 차단하고,
     * direct POST 는 서비스 가드가 409(중복 활성)/404(없는 게스트·정의서) 로 거절한다(advice 매핑).
     * {@code @ResponseBody} — 성공 시 도출 {@code ownedPhases} 를 JSON 으로 반환한다.
     */
    @PostMapping("/{id}/assignment")
    @ResponseBody
    public ResponseEntity<AssignmentResponse> assign(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignSettingRequest request
    ) {
        return ResponseEntity.ok(assignmentCommandService.assign(id, request.definitionId()));
    }

    /**
     * 세팅 정의서 재할당(U3-2-a, XHR JSON) — 미개시 활성을 supersede 하고 현재 정의서로 새 활성 스냅샷을 만든다.
     * 정상 흐름은 뷰가 미개시 활성일 때만 재할당 폼을 노출하고 개시된 활성이면 버튼을 {@code disabled} + tooltip
     * 으로 1차 차단한다(같은 SSOT {@code reassignBlockReason()}). direct POST 는 서비스 가드가 409(개시 후 재할당 ·
     * 활성 부재) / 404(없는 게스트 · 정의서) 로 거절한다(advice 매핑).
     */
    @PostMapping("/{id}/assignment/reassign")
    @ResponseBody
    public ResponseEntity<ReassignmentResponse> reassign(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReassignSettingRequest request
    ) {
        return ResponseEntity.ok(assignmentCommandService.reassign(id, request.definitionId()));
    }

    /**
     * 프로비저닝 개시(E1-0a, DEC-26) + 활성 스냅샷 소비(U3-1, D-D)를 한 트랜잭션으로. 정상 흐름은 뷰가
     * startable 플래그로 버튼을 숨겨 차단하고, direct POST 는 서비스 가드가 409 로 거절한다(안전망) —
     * plain form PRG(회수 버튼과 동일 패턴).
     */
    @PostMapping("/{id}/start")
    public String startProvisioning(@PathVariable("id") UUID id) {
        assignmentStartService.startProvisioning(id);
        return "redirect:/provisioning/server/" + id;
    }

    /**
     * 운영자 수동 실패 전환(E1-2, DEC-4) — 무보고 침묵(UC-4) 대응. 노출 판정(markFailable)은
     * 서버 가드와 같은 도메인 메서드 SSOT — direct POST 는 409 안전망.
     */
    @PostMapping("/{id}/mark-failed")
    public String markFailed(@PathVariable("id") UUID id) {
        guestServerCommandService.markFailedManually(id);
        return "redirect:/provisioning/server/" + id;
    }

    /**
     * 운영자 재시도(E1-2, DEC-4) — 실패 신호 해제 후 커서 유지 재개. 펌웨어 실패는 서버 가드가 차단
     * (UI 는 disabled + tooltip — 같은 SSOT).
     */
    @PostMapping("/{id}/retry")
    public String retry(@PathVariable("id") UUID id) {
        guestServerCommandService.retry(id);
        return "redirect:/provisioning/server/" + id;
    }

    /**
     * 상세 화면의 할당 관련 모델 — 정의서 선택지 + 계획 phase rail. 계획(활성 할당)에 실제 진행 커서
     * (개시된 경우만)를 겹쳐 done/current/pending 을 계산한다(계획 vs 실제 라벨 구분).
     */
    /** 상세 화면이 최초 렌더와 재렌더에서 같은 재료를 받도록 한 곳에서 얹는다. */
    private void addDetailModel(Model model, UUID id, GuestServerDetailResponse server) {
        model.addAttribute("server", server);
        populateAssignmentModel(id, server, model);
    }

    private void populateAssignmentModel(UUID id, GuestServerDetailResponse server, Model model) {
        // 할당 대상은 "할당 가능" 정의서만(삭제 · 비활성 제외, U3-2-b DEC-G). 판정은 서버 가드와 같은
        // 도메인 SSOT(assignBlockReason) 이므로 옵션에서 뺀 정의서를 direct POST 해도 409 로 일관 거절된다.
        model.addAttribute("definitionOptions", settingQueryService.findAssignable());
        AssignmentPlanResponse plan = assignmentQueryService.plannedPhasesOf(id);
        GuestServerDetailResponse.Progress progress = server.progress();
        ProvisioningPhase currentPhase = progress != null ? progress.currentPhase() : null;
        boolean started = progress != null && progress.startedAt() != null;
        model.addAttribute("phaseRail", PlannedPhaseRailResponse.of(plan, currentPhase, started));
    }
}
