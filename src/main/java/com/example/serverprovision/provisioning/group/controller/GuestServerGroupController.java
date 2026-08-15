package com.example.serverprovision.provisioning.group.controller;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.BatchAssignResult;
import com.example.serverprovision.provisioning.assignment.dto.response.GroupApplyPreviewResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.GroupPickerResponse;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.GroupAssignmentService;
import com.example.serverprovision.provisioning.group.dto.request.AddMembersRequest;
import com.example.serverprovision.provisioning.group.dto.request.CreateGroupRequest;
import com.example.serverprovision.provisioning.group.dto.request.RenameGroupRequest;
import com.example.serverprovision.provisioning.group.dto.response.GroupDetailResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupMemberResponse;
import com.example.serverprovision.provisioning.group.dto.response.SeedCandidateResponse;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupCommandService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/**
 * 게스트 서버 그룹 화면 (U3-4).
 *
 * <p>생성 폼만 {@code BindingResult} 를 받아 실패 시 같은 뷰를 다시 그린다 — 이름 오류를 입력칸 옆에
 * 붙여야 하기 때문이다. 나머지 액션은 성공하면 redirect, 실패하면 예외라 그릴 화면이 없고,
 * 전역 인터셉터가 fetch 로 보내 안내 모달로 수렴한다({@code .claude/domain-conventions/new-form.md}).</p>
 */
@Controller
@RequestMapping("/provisioning/server-group")
@RequiredArgsConstructor
public class GuestServerGroupController {

    private final GuestServerGroupQueryService queryService;
    private final GuestServerGroupCommandService commandService;
    // U3-5-c — 그룹과 할당을 잇는 자리는 컨트롤러다. group 과 assignment 는 서로를 참조하지 않는다(DEC-F).
    private final AssignmentQueryService assignmentQueryService;
    private final GroupAssignmentService groupAssignmentService;
    private final SettingQueryService settingQueryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("groups", queryService.findAll());
        return "provisioning/server-group-list";
    }

    /**
     * 생성 폼. 씨앗으로 넘어온 서버가 있으면 함께 보여주고, 없으면 빈 그룹 만들기가 된다(DEC-J).
     *
     * @param serverIds 스펙 묶음에서 넘어온 서버들. 빈 그룹이면 없다
     * @param suggested 스펙 라벨에서 만든 이름 제안. 운영자가 그대로 쓰거나 고친다
     */
    @GetMapping("/new")
    public String newForm(@RequestParam(value = "serverIds", required = false) List<UUID> serverIds,
                          @RequestParam(value = "suggested", required = false) String suggested,
                          Model model) {
        List<SeedCandidateResponse> candidates = queryService.findSeedCandidates(serverIds);
        model.addAttribute("form", new CreateGroupRequest(
                suggested,
                candidates.stream().filter(SeedCandidateResponse::selectable).map(c -> c.server().id()).toList()));
        addSeedModel(model, candidates);
        return "provisioning/server-group-form";
    }

    /** 생성 폼이 최초 렌더와 재렌더에서 같은 재료를 받도록 한 곳에서 얹는다. */
    private void addSeedModel(Model model, List<SeedCandidateResponse> candidates) {
        model.addAttribute("candidates", candidates);
        model.addAttribute("blockedCount", candidates.stream().filter(c -> !c.selectable()).count());
    }

    /**
     * 그룹 생성. 이름 충돌은 <b>예외를 잡지 않고</b> 미리 확인해 필드 오류로 되돌린다 — 컨트롤러가
     * 도메인 예외를 try/catch 하면 도메인이 늘 때마다 분기가 따라 자란다. 확인과 서비스 가드는 같은
     * 판정 메서드를 부르므로 어긋나지 않고, 그 사이의 동시 삽입은 서비스 가드와 DB UNIQUE 가 받는다.
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("form") CreateGroupRequest form,
                         BindingResult bindingResult,
                         Model model) {
        String conflict = bindingResult.hasFieldErrors("name")
                ? null                                        // 이미 붙은 오류가 있으면 덧붙이지 않는다
                : queryService.nameConflictReason(form.name(), null);
        if (conflict != null) {
            bindingResult.rejectValue("name", "duplicate", conflict);
        }
        if (bindingResult.hasErrors()) {
            addSeedModel(model, queryService.findSeedCandidates(form.serverIdsOrEmpty()));
            return "provisioning/server-group-form";
        }
        return "redirect:/provisioning/server-group/"
                + commandService.create(form.name(), form.serverIdsOrEmpty());
    }

    /**
     * 서버 넣기 모달의 후보 목록 조각 (개정).
     *
     * <p>상세와 나눠 둔 이유는 <b>열지 않으면 값을 치르지 않기 위해서</b>다. 후보를 시간 × 스펙으로
     * 묶는 일은 상세를 그릴 때마다 할 필요가 없다. 조각만 돌려주므로 레이아웃을 거치지 않는다.</p>
     */
    @GetMapping("/{id}/candidates")
    public String candidates(@PathVariable Long id, Model model) {
        queryService.findDetail(id);   // 없는 그룹이면 여기서 404 — 모달이 빈 목록을 보여주지 않게
        model.addAttribute("candidates", queryService.findCandidateGroups());
        return "fragments/provisioning/server-picker :: candidates";
    }

    /**
     * 정의서 일괄 할당 모달의 내용 (U3-5-c) — 좌측 목록과 우측 미리보기 · 상세를 한 조각으로 내려준다.
     *
     * <p>상세와 나눠 둔 이유는 서버 넣기 모달과 같다 — 정의서 × 멤버 조합 판정과 정의서 상세 조립을
     * 상세 진입마다 치르지 않는다. 없는 그룹은 {@code findDetail} 이 404 로 끊는다.</p>
     *
     * <p>세 서비스를 여기서 잇는 것은 의도한 것이다(DEC-F). {@code group} 과 {@code assignment} 는 서로를
     * 참조하지 않으며, 멤버 목록은 그룹 쪽에서 · 정의서는 setting 쪽에서 받아 판정에 넘긴다.</p>
     */
    @GetMapping("/{id}/assignment/picker")
    public String assignmentPicker(@PathVariable Long id, Model model) {
        GroupDetailResponse group = queryService.findDetail(id);
        List<GuestServerSummaryResponse> members = memberSummariesOf(group);
        List<SettingSummaryResponse> assignable = settingQueryService.findAssignable();
        List<GroupApplyPreviewResponse> previews = assignmentQueryService.groupPreview(members, assignable);
        List<SettingDetailResponse> details = settingQueryService.findDetailsOf(
                previews.stream().map(GroupApplyPreviewResponse::definitionId).toList());
        model.addAttribute("picker", GroupPickerResponse.of(members.size(), previews, details));
        return "fragments/provisioning/group-definition-picker :: picker";
    }

    /**
     * 정의서 일괄 할당 (U3-5-c) — 미리보기가 알린 대로 붙는 멤버에만 붙인다.
     *
     * <p>대상 선별을 서버가 다시 하는 이유는 화면이 보낸 목록을 그대로 믿지 않기 위해서다. 같은
     * {@code groupPreview} 를 다시 불러 지금 붙는 멤버를 고르므로, 화면이 알린 것과 실제로 하는 것이
     * 같은 판정에서 나온다. 그 사이에 상태가 바뀌었으면 결과 문구가 그 차이를 싣는다.</p>
     *
     * <p><b>{@code data-native-submit} 을 전제한다</b>(DEC-E) — 전역 폼 가로채기가 fetch 로 리다이렉트를
     * 따라가면 flash 가 그 안에서 소비되어 화면에 뜨지 않는다(U3-5-c CP1 실측).</p>
     */
    @PostMapping("/{id}/assignment")
    public String assignBatch(@PathVariable Long id,
                              @RequestParam("definitionId") Long definitionId,
                              RedirectAttributes redirectAttributes) {
        GroupDetailResponse group = queryService.findDetail(id);
        GroupApplyPreviewResponse preview = assignmentQueryService
                .groupPreview(memberSummariesOf(group), settingQueryService.findAssignable()).stream()
                .filter(candidate -> candidate.definitionId().equals(definitionId))
                .findFirst()
                // 목록에 없는 정의서 — 삭제 · 비활성이거나 애초에 없는 것이다. 할당 경로와 같은 판정으로 끊는다.
                .orElseThrow(() -> new SettingNotFoundException(definitionId));

        // 미리보기를 통째로 넘긴다 — 고를 때 빠진 멤버와 실행 중 거절된 멤버를 한 결과에 함께 세기 위해서다.
        // 대상 목록만 넘기면 "2 대에 붙는다" 를 보고 승인한 사용자가 "1 대에 할당했습니다" 만 읽게 된다.
        BatchAssignResult result = groupAssignmentService.assignToMembers(preview, definitionId);
        redirectAttributes.addFlashAttribute("flashMessage", result.message());
        return "redirect:/provisioning/server-group/" + id;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        GroupDetailResponse group = queryService.findDetail(id);
        model.addAttribute("renameForm", new RenameGroupRequest(group.name()));
        addDetailModel(model, group);
        return "provisioning/server-group-detail";
    }

    /** 상세 화면이 최초 렌더와 재렌더에서 같은 재료를 받도록 한 곳에서 얹는다. */
    private void addDetailModel(Model model, GroupDetailResponse group) {
        model.addAttribute("group", group);
        // U3-5-c — 멤버 표의 '할당된 정의서' 열. 일괄 할당 결과를 화면에서 확인하는 근거가 된다.
        model.addAttribute("assignedDefinitions",
                assignmentQueryService.activeDefinitionNamesOf(memberIdsOf(group)));

    }

    private static List<UUID> memberIdsOf(GroupDetailResponse group) {
        return group.members().stream().map(member -> member.server().id()).toList();
    }

    private static List<GuestServerSummaryResponse> memberSummariesOf(GroupDetailResponse group) {
        return group.members().stream().map(GroupMemberResponse::server).toList();
    }

    /**
     * 이름 변경. 생성과 같은 이유로 {@code BindingResult} 를 받는다 — 실패하면 상세를 다시 그려
     * 입력칸 옆에 사유를 붙인다.
     *
     * <p>{@code BindingResult} 없이 {@code @Valid @ModelAttribute} 만 쓰면 검증 실패가
     * {@code BindException} 으로 나가는데, advice 에 등록된 것은 그 하위 타입인
     * {@code MethodArgumentNotValidException} 이라 잡히지 않는다.</p>
     */
    @PostMapping("/{id}/rename")
    public String rename(@PathVariable Long id,
                         @Valid @ModelAttribute("renameForm") RenameGroupRequest form,
                         BindingResult bindingResult,
                         Model model) {
        String conflict = bindingResult.hasFieldErrors("name")
                ? null
                : queryService.nameConflictReason(form.name(), id);
        if (conflict != null) {
            bindingResult.rejectValue("name", "duplicate", conflict);
        }
        if (bindingResult.hasErrors()) {
            addDetailModel(model, queryService.findDetail(id));
            return "provisioning/server-group-detail";
        }
        commandService.rename(id, form.name());
        return "redirect:/provisioning/server-group/" + id;
    }

    @PostMapping("/{id}/members")
    public String addMembers(@PathVariable Long id, @ModelAttribute AddMembersRequest form) {
        commandService.addMembers(id, form.serverIdsOrEmpty());
        return "redirect:/provisioning/server-group/" + id;
    }

    @PostMapping("/{id}/members/{serverId}/remove")
    public String removeMember(@PathVariable Long id, @PathVariable UUID serverId) {
        commandService.removeMember(id, serverId);
        return "redirect:/provisioning/server-group/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        commandService.delete(id);
        return "redirect:/provisioning/server-group";
    }
}
