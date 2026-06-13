package com.example.serverprovision.global.orphan;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.dto.OrphanRetryResponse;

/**
 * 도메인별 오펀 재등록 위임 SPI.
 *
 * <p>공통 saga({@code OrphanRecoveryService}) 는 격리 파일을 active 경로로 복원한 뒤, 자원 종류에 맞는 SPI 에
 * 재등록을 위임한다. 도메인 구현체는 자신의 등록 launcher 를 호출해 새 background job 을 시작한다.</p>
 *
 * <p>R1-4-4 — ISO 가 첫 구현체({@code IsoOrphanRecoverySpi}, CP4). BIOS/BMC/Subprogram 은 구현체 추가만으로
 * onboarding(R1-4-5/6). <b>핵심</b> : 이 SPI 가 도메인의 launcher 의존을 보유하므로 공통 saga 서비스는 launcher 와
 * 무관해지고, 기존 {@code Launcher→Runner→Service→Launcher} 생성자 순환(@Lazy)이 사라진다.</p>
 */
public interface OrphanRecoverySpi {

	/** 이 SPI 가 담당하는 자원 종류. {@code Map<ResourceType, OrphanRecoverySpi>} 라우팅 키. */
	ResourceType supportedType();

	/**
	 * 복원된 자원의 재등록 job 을 시작하고, 재시도 응답(jobId + 도메인별 redirect)을 반환한다.
	 *
	 * <p>redirect 는 자원 종류마다 다른 페이지(ISO = {@code /management/os}, 향후 BIOS/BMC = 별도 페이지)를
	 * 가리키므로 <b>도메인 SPI 가 조립</b>한다 — 공통 saga({@code OrphanRecoveryService}) 는 도메인 페이지 경로를
	 * 알지 않는다(레이어 경계, global→management 하드코딩 회피).</p>
	 *
	 * @param context 재등록에 필요한 도메인 무관 컨텍스트
	 * @return 새 등록 background job id + 추적용 redirect
	 */
	OrphanRetryResponse relaunch(OrphanRecoveryContext context);
}
