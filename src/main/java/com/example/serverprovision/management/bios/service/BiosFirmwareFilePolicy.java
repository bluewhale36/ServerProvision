package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.firmware.BiosFirmwareFilePolicyStrategy;
import com.example.serverprovision.management.common.firmware.FirmwareFilePolicySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * R12-1 — BIOS 펌웨어 파일명 정책의 외부 진입점 (dispatcher).
 *
 * <p>제약(금지 파일명 · 허용 확장자)은 제조사 패키지 관례에서 기원하므로 vendor 매칭
 * {@link BiosFirmwareFilePolicyStrategy} 에 위임만 한다 — 단일 클래스 하드코딩은 다른 제조사 파일에
 * 불필요하게 같은 제약을 강제한다. 정책 데이터는 각 strategy 구현체에 있다 :</p>
 * <ul>
 *   <li>{@code GigabyteFirmwareFilePolicyStrategy} — 허용 확장자 .RBU 단일 + PFR1.RBU · PFR2.RBU 금지
 *       (PFR 사본 경로 전용, 전송 금지 실측)</li>
 *   <li>{@code AsusFirmwareFilePolicyStrategy} — 제약 없음</li>
 *   <li>{@code FujitsuFirmwareFilePolicyStrategy} — 제약 없음</li>
 * </ul>
 *
 * <p>R12-2 — vendor 매칭과 뷰 파생 문자열 조립은 BMC 와 동일하므로 {@link FirmwareFilePolicySupport} 로
 * 올렸다. 이 클래스는 <b>BIOS 전략 목록과 자원 라벨만</b> 공급한다.</p>
 *
 * <p>UI 사전 검사(JavaScript)와 서버 가드(intent 하드 검증 · 등록 본체 안전망)가 본 진입점 하나를
 * 공유한다 — 뷰에는 금지 파일명 · 허용 확장자 · 문구를 data 속성으로 실어 JavaScript 가 읽는다(문구 소유는 서버).</p>
 */
@Service
@RequiredArgsConstructor
public class BiosFirmwareFilePolicy extends FirmwareFilePolicySupport<BiosFirmwareFilePolicyStrategy> {

	private final List<BiosFirmwareFilePolicyStrategy> strategies;

	@Override
	protected List<BiosFirmwareFilePolicyStrategy> strategies() {
		return strategies;
	}

	@Override
	protected String resourceLabel() {
		return "BIOS";
	}
}
