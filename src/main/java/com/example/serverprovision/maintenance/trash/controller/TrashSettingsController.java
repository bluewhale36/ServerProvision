package com.example.serverprovision.maintenance.trash.controller;

import com.example.serverprovision.global.trash.dto.request.TrashSettingsRequest;
import com.example.serverprovision.global.trash.dto.request.TrashSettingsRequestValidator;
import com.example.serverprovision.global.trash.dto.response.TrashSettingsResponse;
import com.example.serverprovision.global.trash.service.TrashSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * S5-2-4 — 휴지통 운영 설정 (trash_settings) 페이지 + 갱신.
 *
 * <p>관리자 권한 (S3 인증 통합 시 보강). CP2 — 시그니처 + view 라우팅만 ; 본체는 CP4.</p>
 */
@Controller
@RequestMapping("/maintenance/trash/settings")
@RequiredArgsConstructor
public class TrashSettingsController {

    private final TrashSettingsService trashSettingsService;
    private final TrashSettingsRequestValidator trashSettingsRequestValidator;

    /**
     * 본 controller 의 모든 binding 에 {@link TrashSettingsRequestValidator} 추가 — Jakarta
     * Bean Validation 직후 cron expression 문법 검증이 자동 실행되어 정확한 field 에 reject.
     */
    @InitBinder("form")
    void initBinder(WebDataBinder binder) {
        binder.addValidators(trashSettingsRequestValidator);
    }

    /** 운영자가 보는 운영 설정 페이지. */
    @GetMapping
    public String view(Model model) {
        TrashSettingsResponse current = trashSettingsService.current();
        model.addAttribute("settings", current);
        if (!model.containsAttribute("form")) {
            // form attribute 는 항상 TrashSettingsRequest — InitBinder 의 validator 가 supports
            // 검증을 통과하도록 타입 일관성 유지.
            model.addAttribute("form", toRequest(current));
        }
        return "maintenance/trash/settings";
    }

    /** Response → Request prefill 변환. record copy. */
    private static TrashSettingsRequest toRequest(TrashSettingsResponse r) {
        return new TrashSettingsRequest(
                r.ttlDays(), r.autoPurgeEnabled(),
                r.purgeCronExpression(), r.notifyCronExpression(),
                r.notifyDaysBefore(), r.notificationChannels(),
                r.retryMaxAttempts(), r.retryBackoffBaseMs()
        );
    }

    /** 운영 설정 갱신 (관리자). CP4 본체 — 즉시 worker 반영 (cron rescheduled). */
    @PostMapping
    public String update(@Valid @ModelAttribute("form") TrashSettingsRequest request,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("settings", trashSettingsService.current());
            return "maintenance/trash/settings";
        }
        trashSettingsService.update(request);
        return "redirect:/maintenance/trash/settings";
    }
}
