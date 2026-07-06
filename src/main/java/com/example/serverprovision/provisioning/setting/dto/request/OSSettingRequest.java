package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * OS 후처리 설정 단계 요청 ({@code "type": "OS_SETTING"}) — 2단 다형 베이스.
 * 계열({@code "osFamily"} 판별자) 해석·등록 지점은 {@link ProcessRequestDeserializer}.
 *
 * <p>레거시부터 이미 {@code osMetadataId} 만 가진 얇은 베이스였으므로 v2 변형이 불요했다
 * (설치 쪽 {@link OSInstallationRequest} 와 대칭 구조). 현재 RHEL 계열만 등록 — 새 계열은
 * 해석기 맵 등록 한 항목으로 확장한다.</p>
 */
@Getter
public abstract class OSSettingRequest extends AbstractProcessRequest {

    /** 후처리 대상 OS 메타데이터의 DB 기본키. */
    @NotNull(message = "OS 메타데이터 ID는 필수 값입니다.")
    protected final Long osMetadataId;

    protected OSSettingRequest(Long osMetadataId) {
        this.osMetadataId = osMetadataId;
    }

    @Override
    public final SettingProcessType processType() {
        return SettingProcessType.OS_SETTING;
    }

    /** OS 계열 — {@code "osFamily"} 판별자와 1:1 인 다형 accessor (화면 분기 + 직렬화 왕복용). */
    @JsonProperty("osFamily")
    public abstract OSFamily osFamily();
}
