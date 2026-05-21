package com.example.serverprovision.management.common.nudge.dto;

import com.example.serverprovision.global.lifecycle.LifecycleStage;

/**
 * MK2 — 단계 A (upload-intent 메타 사전 경고). 결정 #4 (CP1 v3) — 본 슬라이스 포함.
 *
 * <p>사용자가 메타 (이름 / 버전) 가 같은 기존 자원 (어느 stage 든) 위에 새 파일을 올리려는 경우 응답에
 * 본 객체를 동봉. 클라이언트는 단순 안내 (1차 dismiss) modal 만 표시하고, 사용자가 진행하면 본 업로드 진입.
 * 단계 B (해시 후) 와는 독립 — 메타만 같고 파일이 다르면 본 객체만 노출되고 단계 B 충돌은 미발생.</p>
 */
public record PreExistingMatchInfo(
		Long id,
		LifecycleStage state,
		String name,
		String version
) {

}
