package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.exception.RetainedPasswordUnavailableException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * Windows Server 설치 요청 ({@code "osFamily": "WINDOWS"}, E4-1-a-2) — R11 이 "E4 부활 시" 로 예고한 실체.
 * 정의서가 고르는 것은 설치 이미지(에디션)와 Administrator 비밀번호뿐이다. 시간대 · 제품 키 · 공유 접속 정보는
 * 전역 운영 설정({@code WindowsInstallProperties})이 공급하고, 디스크는 RAID 계획의 OS 영역이 정한다.
 *
 * <p>구 저장본(식별 전용 → WINDOWS 치환)은 {@code imageName} · {@code administratorPassword} 가 null 로 해석되며,
 * 상세 카드가 "미지정" 으로 드러내고 E4-1-a-3 의 준비도가 실행을 막는다. 새 저장은 Layer A 가 둘 다 요구한다.</p>
 */
@Getter
public class WindowsInstallationRequest extends OSInstallationRequest {

    /** install.wim 의 {@code /IMAGE/NAME} — 설치 소스가 채집한 목록에서 고른다(계열 검사기가 존재를 확인). */
    @NotNull(message = "설치 이미지를 선택해야 합니다.")
    private final WindowsImageName imageName;

    @NotNull(message = "Administrator 비밀번호를 입력해야 합니다.")
    @Valid
    private final WindowsAdministratorPasswordRequest administratorPassword;

    @JsonCreator
    public WindowsInstallationRequest(
            @JsonProperty("osMetadataId") Long osMetadataId,
            @JsonProperty("isoId") Long isoId,
            @JsonProperty("imageName") WindowsImageName imageName,
            @JsonProperty("administratorPassword") WindowsAdministratorPasswordRequest administratorPassword
    ) {
        super(osMetadataId, isoId);
        this.imageName = imageName;
        this.administratorPassword = administratorPassword;
    }

    @Override
    public OSFamily osFamily() {
        return OSFamily.WINDOWS;
    }

    /** 저장본에 비밀번호 값이 실려 있는가 — 상세 카드 배지 · 병합 판정이 함께 쓴다. */
    @JsonIgnore
    public boolean hasAdministratorPassword() {
        return administratorPassword != null && administratorPassword.hasPassword();
    }

    /** 수정 폼 pre-fill — 비밀번호 값을 빼고 기존 유지 플래그로 대체한다. 값이 없던 저장본은 그대로(유지할 것이 없다). */
    @Override
    public WindowsInstallationRequest withoutSecrets() {
        if (!hasAdministratorPassword()) {
            return this;
        }
        return new WindowsInstallationRequest(osMetadataId, isoId, imageName, administratorPassword.withoutSecret());
    }

    /**
     * 수정 저장 병합 — "기존 유지" 면 같은 단계의 저장본에서 값을 복사한다. 유지할 값이 없으면 400 으로 거절한다
     * (정상 UX 는 값이 있을 때만 유지 체크를 보이므로 direct POST 안전망).
     */
    @Override
    public WindowsInstallationRequest withSecretsRetainedFrom(AbstractProcessRequest existing) {
        if (administratorPassword == null || !administratorPassword.isKeepExistingPassword()) {
            return this;
        }
        if (existing instanceof WindowsInstallationRequest previous && previous.hasAdministratorPassword()) {
            return new WindowsInstallationRequest(osMetadataId, isoId, imageName,
                    administratorPassword.retaining(previous.administratorPassword.getPassword()));
        }
        throw new RetainedPasswordUnavailableException();
    }
}
