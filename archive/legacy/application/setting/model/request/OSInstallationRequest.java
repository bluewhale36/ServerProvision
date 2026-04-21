package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

/**
 * OS 설치 단계에 대한 프론트엔드 요청 DTO의 다형성 기반 클래스이다.
 *
 * <p>역할: {@code "type": "OS_INSTALLATION"} 판별자로 역직렬화될 때 2단계 다형성을 거친다.
 * 1단계는 {@link AbstractProcessRequest}의 {@code type} 판별자로 이 클래스를 선택하고,
 * 2단계는 이 클래스의 {@code osFamily} 판별자로 구체 {@link RHELInstallationRequest} 또는
 * {@link UbuntuInstallationRequest} 를 선택한다. OS 패밀리별로 설치 스크립트 포맷이 다르므로
 * (Kickstart / autoinstall YAML) 필드 구성도 다르며, 공통 필드는 이 추상 클래스에 둔다.</p>
 *
 * <p>유스케이스: {@code POST /pxe/v1/setting/api/new} 요청의 {@code processList} 항목 중
 * {@code "type": "OS_INSTALLATION"}에 해당하는 항목으로 역직렬화된다.
 * {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}가
 * 이 Request를 받아 {@code osMetadataId}, {@code environmentId}(RHEL 전용), {@code packageGroupIds}(RHEL 전용)로
 * 엔티티를 조회하고, 도메인 값 객체로 변환하여 OS 패밀리별 설치 모델을 빌드한다.</p>
 *
 * <p>확장 가이드: 새 OS 패밀리를 지원할 때 {@code osFamily} 판별자에 추가할 값과
 * 대응하는 구체 클래스 하나를 생성하면 된다. 도메인 모델 측도
 * {@link com.example.serverprovision.domain.os.model.installation.OSInstallation}
 * 의 {@code @JsonSubTypes}에 새 모델을 등록해야 한다.</p>
 */
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "osFamily")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RHELInstallationRequest.class,   name = "RHEL_BASED"),
        @JsonSubTypes.Type(value = UbuntuInstallationRequest.class, name = "DEBIAN_BASED")
})
public abstract class OSInstallationRequest extends AbstractProcessRequest {

    /**
     * 설치할 OS의 메타데이터 DB 기본키이다. {@code os_metadata.id}에 해당하며,
     * OS 타입({@link com.example.serverprovision.domain.os.model.enums.OSName})과
     * 버전 정보를 함께 식별한다.
     */
    @NotNull(message = "OS 메타데이터 ID는 필수 값입니다.")
    protected final Long osMetadataId;

    /**
     * 설치 시스템의 Timezone 설정 정보이다.
     * RHEL 계열은 Kickstart {@code timezone} 명령, Ubuntu 는 autoinstall {@code timezone} 키에 대응한다.
     */
    @NotNull(message = "타임존 정보는 필수 값입니다.")
    @Valid
    protected final TimezoneRequest timezone;

    /**
     * 파티션 설정 목록이다. RHEL 계열은 Kickstart {@code part} 명령, Ubuntu 는
     * autoinstall {@code storage.config} 에 대응한다.
     * 필수 마운트포인트 검증은 OS 패밀리별 도메인 모델 생성자에서 수행된다.
     */
    @NotEmpty(message = "파티션 정보는 필수 값입니다.")
    @Valid
    protected final List<PartitionRequest> partitions;

    /**
     * root 계정 비밀번호 정보이다. RHEL 계열은 Kickstart {@code rootpw}, Ubuntu 는
     * autoinstall {@code identity} 혹은 {@code ssh.install-server} 설정에 대응한다.
     * {@code null} 이면 root 계정이 잠긴 상태로 설치되며, 이 경우 {@link #users}에
     * 최소 1명 이상의 일반 사용자가 있어야 한다.
     */
    @Valid
    protected final RootPasswordRequest rootPassword;

    /**
     * 일반 사용자 계정 목록이다. RHEL 계열은 Kickstart {@code user} 명령, Ubuntu 는
     * autoinstall {@code identity} 또는 {@code user-data} 에 대응한다.
     * {@code rootPassword} 가 {@code null} 이고 이 목록도 비어 있으면 도메인 검증에서 거부된다.
     */
    @Valid
    protected final List<UserRequest> users;

    protected OSInstallationRequest(
            Long osMetadataId,
            TimezoneRequest timezone,
            List<PartitionRequest> partitions,
            RootPasswordRequest rootPassword,
            List<UserRequest> users
    ) {
        this.osMetadataId = osMetadataId;
        this.timezone     = timezone;
        this.partitions   = partitions;
        this.rootPassword = rootPassword;
        this.users        = users;
    }

    /**
     * 기존 저장된 비밀번호를 유지하는 수정 케이스에서, 패치된 비밀번호 필드로 본인과 동일한
     * 구체 타입의 새 인스턴스를 만들어 반환한다.
     *
     * <p>OS 패밀리별로 보유 필드가 다르므로 이 메서드는 각 구체 클래스에서 구현된다.
     * {@link com.example.serverprovision.application.setting.service.SettingService#patchKeepExistingPasswords}
     * 에서 호출된다.</p>
     *
     * @param patchedRootPassword 패치된 root 비밀번호 요청 (또는 {@code null} — 잠금 상태)
     * @param patchedUsers        패치된 사용자 목록
     * @return 동일 구체 타입의 새 인스턴스 (다른 필드는 원본 그대로 복제)
     */
    public abstract OSInstallationRequest withPatchedPasswords(
            RootPasswordRequest patchedRootPassword,
            List<UserRequest> patchedUsers
    );
}
