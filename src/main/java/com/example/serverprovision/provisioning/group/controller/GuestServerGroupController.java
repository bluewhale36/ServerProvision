package com.example.serverprovision.provisioning.group.controller;

import com.example.serverprovision.provisioning.group.dto.request.AddMembersRequest;
import com.example.serverprovision.provisioning.group.dto.request.CreateGroupRequest;
import com.example.serverprovision.provisioning.group.dto.request.RenameGroupRequest;
import com.example.serverprovision.provisioning.group.dto.response.GroupDetailResponse;
import com.example.serverprovision.provisioning.group.dto.response.SeedCandidateResponse;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupCommandService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
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
