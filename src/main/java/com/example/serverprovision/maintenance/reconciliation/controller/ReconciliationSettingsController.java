package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.maintenance.reconciliation.dto.request.ReconciliationSettingsRequest;
import com.example.serverprovision.maintenance.reconciliation.dto.response.ReconciliationSettingsResponse;
import com.example.serverprovision.maintenance.reconciliation.service.ReconciliationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;

/**
 * MK4-3-1 — 자원 무결성 점검의 운영 설정 화면. 휴지통 설정 화면과 대칭 구조다.
 *
 * <p>검증 실패 시 같은 뷰를 다시 렌더하므로 폼은 {@code data-native-submit} 으로 전역 가로채기에서
 * 빠진다 — fetch 로 보내면 재렌더된 화면을 버려 필드별 오류 표시가 사라진다(S10 규약).</p>
 */
@Controller
@RequestMapping("/maintenance/reconciliation/settings")
@RequiredArgsConstructor
public class ReconciliationSettingsController {

	private static final String VIEW = "maintenance/reconciliation/settings";

	private final ReconciliationSettingsService settingsService;

	/**
	 * 읽기 전용으로 보여 줄 주기. 저장 대상이 아니라 <b>지금 무엇으로 돌고 있는지</b>를 알리는 값이라
	 * 설정 파일에서 그대로 읽는다. 화면으로 옮기는 것은 MK4-3-2 소관이다.
	 */
	@Value("${reconciliation.scan.interval-ms:3600000}")
	private long scanIntervalMs;

	@Value("${reconciliation.scan.deep-interval-ms:86400000}")
	private long deepScanIntervalMs;

	@GetMapping
	public String view(Model model) {
		ReconciliationSettingsResponse current = currentResponse();
		model.addAttribute("settings", current);
		if (!model.containsAttribute("settingsForm")) {
			model.addAttribute("settingsForm", toRequest(current));
		}
		return VIEW;
	}

	/**
	 * 화면이 그릴 값과 폼이 바인딩할 값은 다른 타입이다 — 앞은 설명 · 후보 목록까지 담은 응답이고
	 * 뒤는 저장될 값만 담은 요청이다. 검증 실패 시 입력값을 보존해야 하므로 폼 쪽은 요청이어야 한다.
	 */
	private static ReconciliationSettingsRequest toRequest(ReconciliationSettingsResponse current) {
		return new ReconciliationSettingsRequest(
				current.selectedKinds().stream().map(Enum::name).toList(),
				current.resolutionEnabled(),
				current.reportRetentionCount(),
				current.extraScanRoots(),
				current.startupScanEnabled());
	}

	@PostMapping
	public String update(
			@Valid @ModelAttribute("settingsForm") ReconciliationSettingsRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			// 실패해도 화면은 후보 목록 · 설명 문구를 그려야 하므로 현재 상태를 함께 싣는다.
			model.addAttribute("settings", currentResponse());
			return VIEW;
		}
		settingsService.update(request);
		return "redirect:/maintenance/reconciliation/settings?saved";
	}

	private ReconciliationSettingsResponse currentResponse() {
		return ReconciliationSettingsResponse.of(
				settingsService.currentValues(),
				settingsService.unknownAutoApplyKinds(),
				Duration.ofMillis(scanIntervalMs),
				Duration.ofMillis(deepScanIntervalMs));
	}
}
