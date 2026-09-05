package com.example.serverprovision.provisioning.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.example.serverprovision.provisioning.setting.exception.RetainedPasswordUnavailableException;
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
     * 비밀번호 필드를 어떻게 바꿔 다시 조립할지(HF12) — 제거(수정 폼 pre-fill)와 보존(수정 저장 병합) 둘이
     * 같은 재조립 경로를 탄다. 계열이 root 를 갖든(RHEL) 안 갖든(Ubuntu) 자기 필드에만 적용한다.
     */
    protected interface PasswordPatch {
        UserRequest user(UserRequest user);
        RootPasswordRequest root(RootPasswordRequest root);
    }

    /** 값 제거 + 기존 유지 플래그 — 비밀번호는 서버 밖으로 다시 내보내지 않는다. */
    private static final PasswordPatch STRIP = new PasswordPatch() {
        @Override public UserRequest user(UserRequest u) {
            return new UserRequest(u.getUsername(), null, u.getIsSudoer(), false, true);
        }
        @Override public RootPasswordRequest root(RootPasswordRequest r) {
            return r == null ? null : new RootPasswordRequest(null, false, true);
        }
    };

    /**
     * "기존 유지" 인 항목만 저장본에서 값을 복사한다(keep 해제). 유지할 값이 없으면 400 — 정상 UX 는 값이 있는
     * 저장본에만 유지 체크를 보이므로 direct PUT 안전망이다. 저장본이 리눅스 요청이 아니면(단계 신설 · 타입 변경) 값 없음.
     */
    private static PasswordPatch retainFrom(AbstractProcessRequest existing) {
        LinuxInstallationRequest previous = existing instanceof LinuxInstallationRequest p ? p : null;
        return new PasswordPatch() {
            @Override public UserRequest user(UserRequest u) {
                if (!u.isKeepExistingPassword()) return u;
                UserRequest kept = previous == null || previous.users == null ? null : previous.users.stream()
                        .filter(prev -> prev != null && prev.getUsername() != null
                                && prev.getUsername().equals(u.getUsername()) && prev.hasPassword())
                        .findFirst().orElse(null);
                if (kept == null) throw new RetainedPasswordUnavailableException("users", "사용자(" + u.getUsername() + ")");
                return u.retaining(kept);
            }
            @Override public RootPasswordRequest root(RootPasswordRequest r) {
                if (r == null || !r.isKeepExistingPassword()) return r;
                if (previous instanceof RHELInstallationRequest rhel
                        && rhel.getRootPassword() != null && rhel.getRootPassword().hasPassword()) {
                    return r.retaining(rhel.getRootPassword());
                }
                throw new RetainedPasswordUnavailableException("rootPassword", "root");
            }
        };
    }

    /** 계열별 재조립 — 자기 필드(RHEL 은 root 포함)에 patch 를 적용한 사본을 돌려준다. */
    public abstract LinuxInstallationRequest withPatchedPasswords(PasswordPatch patch);

    /** users 에 patch 적용 — 재조립 구현이 공유하는 조각. */
    protected final List<UserRequest> patchUsers(PasswordPatch patch) {
        return users == null ? null : users.stream().map(u -> u == null ? null : patch.user(u)).toList();
    }

    /** 수정 폼 pre-fill — 비밀번호 제거(E4-1-a-2 D-11). */
    @Override
    public LinuxInstallationRequest withoutSecrets() {
        return withPatchedPasswords(STRIP);
    }

    /** 수정 저장 병합 — Windows 축과 같은 훅(HF12 결함 B 정정). */
    @Override
    public LinuxInstallationRequest withSecretsRetainedFrom(AbstractProcessRequest existing) {
        return withPatchedPasswords(retainFrom(existing));
    }

    /** grow 아닌 파티션 크기의 합(바이트) — OS 영역 볼륨 하한과 대조한다(U4-1-3 D7). 판정 재료라 payload 에 싣지 않는다. */
    @JsonIgnore
    public long fixedPartitionBytes() {
        return partitions == null ? 0L : partitions.stream().filter(java.util.Objects::nonNull).mapToLong(PartitionRequest::fixedBytes).sum();
    }

    /** grow 파티션이 있는가 — 있으면 고정 합이 하한보다 작아야(등호 불가) grow 가 자리를 갖는다. */
    @JsonIgnore
    public boolean hasGrowPartition() {
        return partitions != null && partitions.stream().anyMatch(p -> p != null && p.isGrow());
    }
}
