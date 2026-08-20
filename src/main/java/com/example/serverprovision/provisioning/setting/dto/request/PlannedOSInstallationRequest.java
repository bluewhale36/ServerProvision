package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OS 설치 단계의 식별 전용 요청 — 계열({@code osFamily}) 판별자가 없는 "설치 예정 기록"이다 (R11 D-R1).
 *
 * <p>MVP 가 OS 설치 실행(E4)을 배포 범위에서 제외해 상세 입력(타임존 · 파티션 · 사용자 · 패키지)의
 * 소비처가 없으므로, 정의서의 OS 설치 단계는 "이 서버에 무엇을 설치할 예정인가"의 식별
 * (OS 메타데이터 · ISO)만 기록한다. 계열 구현체와 그 계약은 보존된다 — 화면 노출만 걷혔고,
 * E4 부활 시 재노출로 복귀한다(R11 D-R2 · D-R3).</p>
 *
 * <p>기록 허용 대상은 Windows 계열뿐이다(사용자 확정 2026-08-20 — 대부분 출고 서버의 실 공통
 * 과정인 Windows Server 테스트 설치만 기록 가치가 실재). 판정은
 * {@link com.example.serverprovision.provisioning.setting.service.reference.os.PlannedInstallTargetPolicy}
 * 가 UI 차단과 서버 가드의 단일 SSOT 로 수행한다(R11 D-R8).</p>
 */
public class PlannedOSInstallationRequest extends OSInstallationRequest {

    @JsonCreator
    public PlannedOSInstallationRequest(
            @JsonProperty("osMetadataId") Long osMetadataId,
            @JsonProperty("isoId") Long isoId) {
        super(osMetadataId, isoId);
    }

    /** 식별 전용 — 계열 미정이므로 판별자가 없다. null 직렬화 → 재해석 시 같은 타입으로 왕복한다. */
    @Override
    public OSFamily osFamily() {
        return null;
    }
}
