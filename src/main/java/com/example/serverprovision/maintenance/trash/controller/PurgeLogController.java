package com.example.serverprovision.maintenance.trash.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.dto.response.PurgeLogResponse;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;
import com.example.serverprovision.global.trash.enums.PurgeOutcome;
import com.example.serverprovision.global.trash.service.PurgeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/**
 * S5-2-4 — purge_log 조회 페이지. 단일 시간순 로그를 운영자가 필터/페이지네이션으로 회고.
 *
 * <p>CP2 — view + 필터 파라미터 시그니처. 본체는 CP4.</p>
 */
@Controller
@RequestMapping("/maintenance/trash/purge-log")
@RequiredArgsConstructor
public class PurgeLogController {

	private final PurgeLogService purgeLogService;

	@GetMapping
	public String view(
			@RequestParam(name = "resource_type", required = false) ResourceType resourceType,
			@RequestParam(name = "origin", required = false) PurgeOrigin origin,
			@RequestParam(name = "outcome", required = false) PurgeOutcome outcome,
			@RequestParam(name = "from", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(name = "to", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			Pageable pageable,
			Model model
	) {

		Page<PurgeLogResponse> page = purgeLogService.findPage(resourceType, origin, outcome, from, to, pageable);
		model.addAttribute("page", page);
		model.addAttribute("filterResourceType", resourceType);
		model.addAttribute("filterOrigin", origin);
		model.addAttribute("filterOutcome", outcome);
		model.addAttribute("filterFrom", from);
		model.addAttribute("filterTo", to);
		model.addAttribute("resourceTypes", ResourceType.values());
		model.addAttribute("origins", PurgeOrigin.values());
		model.addAttribute("outcomes", PurgeOutcome.values());
		return "maintenance/trash/purge-log";
	}
}
