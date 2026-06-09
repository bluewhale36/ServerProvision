package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.provisioning.service.BiosSetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * BIOS 셋업 진입 (MVC). 사용자가 먼저 보드를 선택하면 해당 보드의 BIOS 셋업 화면을 렌더한다.
 * <p><b>임시방편</b> : 보드 자원이 application.properties 의 lazy 파일 파싱으로 구성된다.
 * 향후 BoardModel 자원 도메인과 병합해 DB 기반 프리셋으로 대체 예정 — 그때도 보드 해석은
 * {@code BiosSetupLoader.load(boardKey)} 단일 seam 으로 유지한다(컨트롤러는 파일/설정을 직접 읽지 않음).</p>
 */
@Controller
@RequestMapping("/provisioning/bios-setup")
@RequiredArgsConstructor
public class BiosSetupController {

	private final BiosSetupService biosSetupService;

	/** 보드 선택 랜딩. */
	@GetMapping
	public String selectBoard(Model model) {
		model.addAttribute("boards", biosSetupService.listBoards());
		return "provisioning/bios-board-select";
	}

	/** 선택된 보드의 BIOS 셋업 화면. 미등록 boardKey 는 {@code BiosBoardNotFoundException} → 404. */
	@GetMapping("/{boardKey}")
	public String biosSetup(@PathVariable String boardKey, Model model) {
		model.addAttribute("bios", biosSetupService.renderMenu(boardKey));
		model.addAttribute("boardKey", boardKey);
		return "provisioning/bios-setup";
	}
}
