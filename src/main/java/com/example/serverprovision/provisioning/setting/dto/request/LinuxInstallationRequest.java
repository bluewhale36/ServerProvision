package com.example.serverprovision.provisioning.setting.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

/**
 * 리눅스 계열 OS 설치 요청의 추상 중간층 (U2-1 plan v2 D9).
 *
 * <p>이 층의 존재 근거는 미래의 Windows 가 아니라 <b>현재 RHEL/Ubuntu 두 구현체가 실제로 중복
 * 공유하는 필드·검증</b>이다(중복 금지). timezone 이 베이스가 아닌 이 층에 있는 이유:
 * {@link TimezoneRequest} 의 형태(IANA 문자열 + RTC-UTC 플래그)가 Kickstart {@code timezone --utc}
 * 의 사상이라, 개념은 보편이어도 표현이 리눅스 것이기 때문이다.</p>
 *
 * <p>Jackson 은 상속을 평탄화하므로 이 중간층은 wire JSON 에 나타나지 않는다(D10 — flat 유지).
 * {@code @JsonSubTypes} 에도 등록하지 않는다(abstract, 판별자 없음).</p>
 */
@Getter
public abstract class LinuxInstallationRequest extends OSInstallationRequest {

    @NotNull(message = "타임존 정보는 필수 값입니다.")
    @Valid
    protected final TimezoneRequest timezone;

    @NotEmpty(message = "파티션 정보는 필수 값입니다.")
    @Valid
    protected final List<PartitionRequest> partitions;

    /**
     * 일반 사용자 목록. root 비밀번호는 이 층이 아니라 {@link RHELInstallationRequest} 소유다
     * (사용자 확정 2026-07-05): Ubuntu autoinstall 은 root 잠금이 기본이고 identity 사용자가
     * 필수이며, root 접근은 설치 후 {@code sudo passwd root} 로 여는 관례라 설치 계약에
     * root 비밀번호 개념 자체가 없다. 계열별 접근성 규칙(RHEL: root 비밀번호 또는 사용자 1+,
     * Ubuntu: 사용자 1+)은 계열 검사기가 검증한다.
     */
    @Valid
    protected final List<UserRequest> users;

    protected LinuxInstallationRequest(
            Long osMetadataId,
            Long isoId,
            TimezoneRequest timezone,
            List<PartitionRequest> partitions,
            List<UserRequest> users
    ) {
        super(osMetadataId, isoId);
        this.timezone   = timezone;
        this.partitions = partitions;
        this.users      = users;
    }

    /**
     * 수정 폼 pre-fill 용 — 비밀번호를 제거(기존 유지 플래그로 대체)한 사본을 반환한다.
     * 비밀번호는 서버 밖으로 다시 내보내지 않는다. RHEL 은 이 구현에서 자신의 root 비밀번호도
     * 함께 패치한다(root 패치 지식이 소유 계층에 캡슐화 — 호출측 instanceof 불요).
     */
    public abstract LinuxInstallationRequest withPatchedPasswords(List<UserRequest> patchedUsers);
}
