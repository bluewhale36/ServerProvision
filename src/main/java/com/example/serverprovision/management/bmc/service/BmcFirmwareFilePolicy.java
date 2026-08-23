package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.management.bmc.firmware.BmcFirmwareFilePolicyStrategy;
import com.example.serverprovision.management.common.firmware.FirmwareFilePolicySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * R12-2 — BMC 펌웨어 파일명 정책의 외부 진입점 (dispatcher).
 *
 * <p>vendor 매칭과 뷰 파생 문자열 조립은 {@link FirmwareFilePolicySupport} 가 담당하고, 이 클래스는
 * <b>BMC 전략 목록과 자원 라벨만</b> 공급한다. 정책 데이터는 각 strategy 구현체에 있다 :</p>
 * <ul>
 *   <li>{@code GigabyteBmcFirmwareFilePolicyStrategy} — 허용 확장자 .ima_enc 단일 (Redfish 실측)</li>
 *   <li>{@code AsusBmcFirmwareFilePolicyStrategy} — 제약 없음</li>
 *   <li>{@code FujitsuBmcFirmwareFilePolicyStrategy} — 제약 없음</li>
 * </ul>
 *
 * <p>주입 목록의 타입이 {@link BmcFirmwareFilePolicyStrategy} 이므로 BIOS 전략이 섞이지 않는다 —
 * 같은 제조사라도 자원에 따라 요구 형식이 다르기 때문에(GIGABYTE 기준 BIOS 는 .RBU) 이 격리가 필요하다.</p>
 */
@Service
@RequiredArgsConstructor
public class BmcFirmwareFilePolicy extends FirmwareFilePolicySupport<BmcFirmwareFilePolicyStrategy> {

	private final List<BmcFirmwareFilePolicyStrategy> strategies;

	@Override
	protected List<BmcFirmwareFilePolicyStrategy> strategies() {
		return strategies;
	}

	@Override
	protected String resourceLabel() {
		return "BMC";
	}
}
