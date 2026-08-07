package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.provisioning.setting.service.SettingCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 세팅 정의서 lifecycle (SSR form-POST) — U3-2-b. os · board lifecycle 선례의
 * {@code POST /{id}/delete · /restore · /purge(typedName) · /toggle · /deprecate · /undeprecate} 규약을
 * 따르고, 삭제 확인은 관리 공용 confirm 모달 fragment(soft-delete / purge) 를 재사용한다(form submit → redirect).
 *
 * <p><b>왜 SettingRestController(XHR) 가 아니라 별도 SSR 컨트롤러인가</b>: ① plan §3 가 세 액션의 성공
 * 응답을 302(redirect) 로 명시했고 ② 확인 모달 재사용이 form-submit 흐름을 전제하며 ③ os · board lifecycle
 * 선례와 동형이다. 조회 전용인 {@link SettingController} 에 쓰기 의존을 섞지 않으려 lifecycle 만 분리했다
 * (os 의 IsoLifecycleController 분리 선례). purge 의 typedName 은 confirm-purge JS 가 hidden input 으로
 * 넣는 form 필드라 {@code @RequestParam} 으로 받는다(os 동형 — 별도 body DTO 불요).</p>
 *
 * <p>도메인 예외는 advice 가 HTTP 로 매핑한다 — 없는 정의서 404({@code SettingNotFoundException}),
 * 복원 name 충돌 409({@code RestoreNameConflictException}), purge typed-name 불일치
 * 400({@code TypedNameMismatchException}). 삭제 자체는 차단이 아니라 경고(DEC-C)라 정상 경로엔 예외가 없다.</p>
 */
@Controller
@RequestMapping("/provisioning/setting")
@RequiredArgsConstructor
public class SettingLifecycleController {

    private final SettingCommandService settingCommandService;

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        settingCommandService.softDelete(id);
        // 삭제 후 상세로 복귀 — "삭제됨" 배지 + 복원/영구삭제 액션을 그 자리에서 이어간다.
        return redirectToDetail(id);
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable("id") Long id) {
        settingCommandService.restore(id);
        return redirectToDetail(id);
    }

    @PostMapping("/{id}/purge")
    public String purge(@PathVariable("id") Long id, @RequestParam("typedName") String typedName) {
        settingCommandService.purge(id, typedName);
        // 영구 삭제 후 정의서 행이 사라지므로 상세가 아니라 휴지통 목록으로 복귀한다.
        return "redirect:/provisioning/setting?includeDeleted=true";
    }

    // ==== U3-2-b DEC-G — 활성 · 사용 중단 권고 축 (os lifecycle 규약 동형) ====

    @PostMapping("/{id}/toggle")
    public String toggleEnabled(@PathVariable("id") Long id) {
        settingCommandService.toggleEnabled(id);
        return redirectToDetail(id);
    }

    @PostMapping("/{id}/deprecate")
    public String deprecate(@PathVariable("id") Long id) {
        settingCommandService.deprecate(id);
        return redirectToDetail(id);
    }

    @PostMapping("/{id}/undeprecate")
    public String undeprecate(@PathVariable("id") Long id) {
        settingCommandService.undeprecate(id);
        return redirectToDetail(id);
    }

    /** 상태 전이 후 복귀 지점 — 바뀐 배지와 다음 액션을 그 자리에서 이어가게 상세로 돌려보낸다(PRG). */
    private String redirectToDetail(Long id) {
        return "redirect:/provisioning/setting/" + id;
    }
}
