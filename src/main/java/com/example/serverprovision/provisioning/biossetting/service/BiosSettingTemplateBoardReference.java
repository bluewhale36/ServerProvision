package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.management.board.service.purge.BoardModelPurgeBlocker;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 메인보드 영구 삭제를 막는 BIOS 세팅 템플릿(HF26) — 사용자가 작성한 내용이라 보드와 함께 지우지 않는다. */
@Component
@RequiredArgsConstructor
public class BiosSettingTemplateBoardReference implements BoardModelPurgeBlocker {

	private final BiosSettingTemplateRepository templateRepository;

	@Override
	public String remainingReference(Long boardModelId) {
		long count = templateRepository.countByBoardModel_Id(boardModelId);
		return count == 0 ? null : "BIOS 세팅 템플릿 " + count + " 개";
	}
}
