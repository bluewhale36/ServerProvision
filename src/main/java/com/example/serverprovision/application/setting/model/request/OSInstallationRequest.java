package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

/**
 * OS 설치 단계에 대한 프론트엔드 요청 DTO이다.
 *
 * <p>역할: {@code "type": "OS_INSTALLATION"}으로 Jackson 다형성 역직렬화에 사용된다.
 * OS 설치에 필요한 모든 정보(OS 메타데이터 ID, 파티션 목록, 사용자 정보, Timezone, 환경 설정)를 담는다.</p>
 *
 * <p>유스케이스: {@code POST /pxe/v1/setting/api/new} 요청의 {@code processList} 항목 중
 * {@code "type": "OS_INSTALLATION"}에 해당하는 항목으로 역직렬화된다.
 * {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}가
 * 이 Request를 받아 {@code osMetadataId}, {@code environmentId}, {@code packageGroupIds}로
 * 엔티티를 조회하고, 도메인 값 객체({@link com.example.serverprovision.domain.os.model.installation.Partition},
 * {@link com.example.serverprovision.domain.os.model.installation.User} 등)로 변환하여
 * {@link com.example.serverprovision.domain.os.model.installation.RockyLinuxInstallation}을 빌드한다.
 * {@code rootPassword}가 {@code null}이면 root 계정이 잠긴 상태로 설치되며, 이 경우
 * {@code users}에 최소 1명 이상이 있어야 도메인 검증을 통과한다.</p>
 *
 * <p>확장 가이드: 새 OS 타입을 지원할 때 이 Request 자체는 수정할 필요가 없다.
 * {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}의
 * OS 타입 switch에 새 case를 추가하는 것으로 충분하다.
 * OS별로 추가적인 설치 파라미터가 필요하면 이 클래스에 새 필드를 추가하고
 * {@code @JsonCreator} 생성자에도 반영한다.</p>
 */
@Getter
public class OSInstallationRequest extends AbstractProcessRequest {

    /**
     * 설치할 OS의 메타데이터 DB 기본키이다. {@code os_metadata.id}에 해당하며,
     * OS 타입({@link com.example.serverprovision.domain.os.model.enums.OSName})과
     * 버전 정보를 함께 식별한다. {@code installVersion}은 Resolver에서 이 ID로 조회한 엔티티에서 파생된다.
     */
    @NotNull(message = "OS 메타데이터 ID는 필수 값입니다.")
    private final Long osMetadataId;

    /**
     * Kickstart {@code %addon com_redhat_kdump} 섹션 활성화 여부이다.
     * KDump는 커널 크래시 시 메모리 덤프를 수집하는 기능이다.
     */
    private final boolean isKDumpEnabled;

    /**
     * 설치 시스템의 Timezone 설정 정보이다. Kickstart {@code timezone} 명령에 대응한다.
     */
    @NotNull(message = "타임존 정보는 필수 값입니다.")
    @Valid
    private final TimezoneRequest timezone;

    /**
     * 설치할 패키지 환경 그룹의 DB 기본키이다. {@code os_environment.id}에 해당한다.
     * Kickstart {@code %packages}의 {@code @^environment} 항목에 대응한다.
     */
    @NotNull(message = "설치 환경 ID는 필수 값입니다.")
    private final Long environmentId;

    /**
     * 추가 설치할 패키지 그룹의 DB 기본키 목록이다. {@code os_package_group.id} 목록에 해당한다.
     * Kickstart {@code %packages}의 {@code @group} 항목들에 대응한다.
     * add-on 패키지 그룹은 선택 사항이므로 {@code null} 또는 빈 리스트 모두 허용된다.
     * 이 경우 {@code %packages} 섹션에 환경 항목({@code @^env})만 기록된다.
     */
    private final List<Long> packageGroupIds;

    /**
     * 파티션 설정 목록이다. Kickstart {@code part} 명령들에 대응한다.
     * 필수 마운트포인트({@code /}, {@code /boot}, {@code /boot/efi}, {@code swap})가
     * 모두 포함되어야 도메인 검증을 통과한다.
     */
    @NotEmpty(message = "파티션 정보는 필수 값입니다.")
    @Valid
    private final List<PartitionRequest> partitions;

    /**
     * root 계정 비밀번호 정보이다. Kickstart {@code rootpw} 명령에 대응한다.
     * {@code null}이면 root 계정이 잠긴 상태({@code rootpw --lock})로 설치되며,
     * 이 경우 {@code users}에 최소 1명 이상의 일반 사용자가 있어야 한다.
     */
    @Valid
    private final RootPasswordRequest rootPassword;

    /**
     * 일반 사용자 계정 목록이다. Kickstart {@code user} 명령들에 대응한다.
     * Rocky Linux Kickstart에서 일반 사용자는 선택 사항이다(root 비밀번호가 있는 경우).
     * {@code rootPassword}가 {@code null}이고 이 목록도 비어 있으면
     * 도메인 검증에서 {@code NO_ACCESSIBLE_USER} 예외가 발생한다.
     */
    @Valid
    private final List<UserRequest> users;

    @JsonCreator
    public OSInstallationRequest(
            @JsonProperty("osMetadataId")    Long osMetadataId,
            @JsonProperty("isKDumpEnabled")  boolean isKDumpEnabled,
            @JsonProperty("timezone")        TimezoneRequest timezone,
            @JsonProperty("environmentId")   Long environmentId,
            @JsonProperty("packageGroupIds") List<Long> packageGroupIds,
            @JsonProperty("partitions")      List<PartitionRequest> partitions,
            @JsonProperty("rootPassword")    RootPasswordRequest rootPassword,
            @JsonProperty("users")           List<UserRequest> users) {
        this.osMetadataId    = osMetadataId;
        this.isKDumpEnabled  = isKDumpEnabled;
        this.timezone        = timezone;
        this.environmentId   = environmentId;
        this.packageGroupIds = packageGroupIds;
        this.partitions      = partitions;
        this.rootPassword    = rootPassword;
        this.users           = users;
    }
}
