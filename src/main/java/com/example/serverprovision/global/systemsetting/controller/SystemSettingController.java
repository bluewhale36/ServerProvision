package com.example.serverprovision.global.systemsetting.controller;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.systemsetting.dto.request.ProvisioningAllowedRangeRequest;
import com.example.serverprovision.global.systemsetting.dto.response.SystemSettingResponse;
import com.example.serverprovision.global.systemsetting.enums.SystemSettingKey;
import com.example.serverprovision.global.systemsetting.service.SystemSettingService;
import com.example.serverprovision.global.systemsetting.spi.ProvisioningRangeFollower;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 시스템 설정 화면(U7). 카드는 현재 사용자가 접근할 수 있는 설정만 보인다. */
@Controller
@RequestMapping("/system/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private static final String VIEW = "system/settings";

    private final SystemSettingService systemSettingService;
    private final ObjectProvider<ProvisioningRangeFollower> rangeFollowers;

    @GetMapping
    public String page(Model model) {
        List<SystemSettingResponse> settings = systemSettingService.accessibleSettings(currentRoles());
        String current = settings.stream()
                .filter(s -> s.key() == SystemSettingKey.PROVISIONING_ALLOWED_RANGE)
                .findFirst().map(SystemSettingResponse::value).orElse(null);
        model.addAttribute("rangeForm", new ProvisioningAllowedRangeRequest(current));
        return render(model, settings);
    }

    @PostMapping("/provisioning-allowed-range")
    public String saveProvisioningAllowedRange(
            @Valid @ModelAttribute("rangeForm") ProvisioningAllowedRangeRequest form, BindingResult binding,
            RedirectAttributes redirectAttributes, Model model, HttpServletResponse response) {
        Set<Role> roles = currentRoles();
        // 권한이 먼저다. 권한이 없는 화면에는 이 카드가 없어 필드 오류를 그릴 자리가 없다.
        systemSettingService.assertAccessible(SystemSettingKey.PROVISIONING_ALLOWED_RANGE, roles);
        if (binding.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(model, systemSettingService.accessibleSettings(roles));
        }
        ProvisioningAllowedCidr cidr = ProvisioningAllowedCidr.parse(form.cidr());
        systemSettingService.saveProvisioningAllowedRange(cidr, roles);
        redirectAttributes.addFlashAttribute("flashMessage", "프로비저닝 허용 대역을 저장했습니다.");
        reapplyNotice(cidr).ifPresent(notice -> redirectAttributes.addFlashAttribute("flashWarning", notice));
        return "redirect:/system/settings";
    }

    /**
     * 이 대역을 따라가는 설정의 재적용 안내(HF35). 저장은 끝났지만 운영자가 할 일이 남았다는 주의라, 성공 알림과
     * 나눠 주의 알림으로 보인다.
     */
    private Optional<String> reapplyNotice(ProvisioningAllowedCidr cidr) {
        String joined = rangeFollowers.orderedStream()
                .map(f -> f.reapplyNotice(cidr))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(" "));
        return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
    }

    private static String render(Model model, List<SystemSettingResponse> settings) {
        model.addAttribute("settings", settings);
        model.addAttribute("rangeSetting", settings.stream()
                .filter(s -> s.key() == SystemSettingKey.PROVISIONING_ALLOWED_RANGE).findFirst().orElse(null));
        return VIEW;
    }

    /** 세션 컨텍스트에서 읽는다. {@code @AuthenticationPrincipal} 은 보안 MVC 통합이 없는 슬라이스 테스트에서 풀리지 않는다. */
    private static Set<Role> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return Role.fromAuthorityNames(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
