package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * OS 설치 단계 요청 ({@code "type": "OS_INSTALLATION"}) — 2단 다형 베이스.
 * 계열({@code "osFamily"} 판별자) 해석·등록 지점은 {@link ProcessRequestDeserializer}.
 *
 * <p><b>Windows-safe 얇은 베이스 (U2-1 plan v2 D9)</b> : 레거시는 timezone·partitions·rootPassword·users 를
 * 이 층에 hoist 했으나, 그것은 리눅스 설치의 표현이다. 모든 OS 설치의 진짜 공통은
 * "어떤 OS 를 설치하는가"({@link #osMetadataId}) 뿐이므로 그것만 남기고, 리눅스 공통 필드는
 * {@link LinuxInstallationRequest} 중간층으로 내렸다. 추후 Windows 대응 시
 * {@code WindowsInstallationRequest} 가 이 베이스만 상속하고 해석기 맵에 {@code WINDOWS}
 * 한 항목을 등록하면 된다 — 등록 전까지 {@code WINDOWS} 전송은 advice 가 400 으로 응답한다.</p>
 */
@Getter
public abstract class OSInstallationRequest extends AbstractProcessRequest {

    /** 설치할 OS 메타데이터의 DB 기본키. OS 종류·버전을 함께 식별한다. */
    @NotNull(message = "OS 메타데이터 ID는 필수 값입니다.")
    protected final Long osMetadataId;

    /**
     * 설치에 사용할 ISO 의 DB 기본키(U2-4, 사용자 확정 2026-07-11) — OS 버전 선택 후 ISO 도
     * 명시 선택한다. UI 는 사용 가능한 ISO 가 없는 OS 를 옵션에서 제외하고, 검사기가
     * 실존·OS 소속·enabled 를 검증한다. 계열 무관 보편 개념이라 베이스 소유.
     */
    @NotNull(message = "설치 ISO 는 필수 선택입니다.")
    protected final Long isoId;

    protected OSInstallationRequest(Long osMetadataId, Long isoId) {
        this.osMetadataId = osMetadataId;
        this.isoId = isoId;
    }

    @Override
    public final SettingProcessType processType() {
        return SettingProcessType.OS_INSTALLATION;
    }

    /** OS 계열 — {@code "osFamily"} 판별자와 1:1 인 다형 accessor (화면 분기 + 직렬화 왕복용). */
    @JsonProperty("osFamily")
    public abstract OSFamily osFamily();
}
