package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.OrphanRecoveryContext;
import com.example.serverprovision.global.orphan.OrphanRecoverySpi;
import com.example.serverprovision.global.orphan.dto.OrphanRetryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ISO 오펀 재등록 SPI 구현체 (R1-4-4 의 첫 도메인 구현). 격리 컨텍스트로부터 {@code PreparedIsoRegistration} 을
 * 재구성해 ISO 등록 launcher 로 새 job 을 시작하고, OS 페이지 redirect 를 조립한다.
 *
 * <p>이 클래스가 {@link IsoRegistrationLauncher} 의존을 보유하므로 공통 saga(OrphanRecoveryService/QuarantineService)는
 * launcher 와 무관해진다 — 기존 @Lazy 순환 제거의 핵심.</p>
 */
@Component
@RequiredArgsConstructor
public class IsoOrphanRecoverySpi implements OrphanRecoverySpi {

	private final IsoRegistrationLauncher launcher;

	@Override
	public ResourceType supportedType() {
		return ResourceType.OS_ISO;
	}

	@Override
	public OrphanRetryResponse relaunch(OrphanRecoveryContext context) {
		IsoRecoveryPayload payload = (IsoRecoveryPayload) context.payload();
		IsoRegistrationService.PreparedIsoRegistration prepared = new IsoRegistrationService.PreparedIsoRegistration(
				context.parentId(),
				context.resolvedPath(),
				payload.description(),
				context.originalFilename(),
				context.uploadedFile(),
				payload.clientHash()
		);
		String jobId = launcher.startRegistration(prepared);
		return new OrphanRetryResponse(jobId, "/management/os?selectId=" + context.parentId());
	}
}
