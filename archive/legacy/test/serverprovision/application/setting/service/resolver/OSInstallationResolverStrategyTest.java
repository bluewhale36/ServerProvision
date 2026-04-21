package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.application.setting.model.request.RHELInstallationRequest;
import com.example.serverprovision.application.setting.model.request.UbuntuInstallationRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSPackageGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 Strategy 리팩터 — {@link OSInstallationBuilder#supports(OSInstallationRequest, OSMetadata)}
 * 매트릭스 검증.
 *
 * <p>5 개의 빌더(RockyLinux8/9/10Builder, CentOS7Builder, Ubuntu2204Builder) 각각이
 * (요청 DTO 타입 × OSMetadata(osName, osVersion)) 조합에서 자신의 담당 케이스만 true 를
 * 반환하고 나머지는 false 를 반환하는지 검증한다.</p>
 *
 * <p>Mockito 로 repository 는 주입만 하고 실제 호출은 하지 않는다 ({@code supports()} 는 I/O
 * 를 유발하지 않는 순수 판별 로직이므로).</p>
 */
@ExtendWith(MockitoExtension.class)
class OSInstallationResolverStrategyTest {

    @Mock
    private OSEnvironmentRepository osEnvironmentRepository;

    @Mock
    private OSPackageGroupRepository osPackageGroupRepository;

    // ─────────────────────────────────────────────
    // 테스트 더블 헬퍼
    // ─────────────────────────────────────────────

    private OSMetadata metaOf(OSName osName, String osVersion) {
        return OSMetadata.builder()
                .id(1L)
                .osName(osName)
                .osVersion(osVersion)
                .isoMountPath("/mnt/iso")
                .isEnabled(true)
                .build();
    }

    private RHELInstallationRequest rhelReq() {
        return new RHELInstallationRequest(
                /* osMetadataId   */ 1L,
                /* timezone       */ null,
                /* partitions     */ List.of(),
                /* rootPassword   */ null,
                /* users          */ List.of(),
                /* environmentId  */ 1L,
                /* packageGroupIds*/ List.of(),
                /* isKDumpEnabled */ false,
                /* allowSshRoot   */ null
        );
    }

    private UbuntuInstallationRequest ubuntuReq() {
        return new UbuntuInstallationRequest(
                /* osMetadataId */ 1L,
                /* timezone     */ null,
                /* partitions   */ List.of(),
                /* rootPassword */ null,
                /* users        */ List.of(),
                /* hostname     */ "host1",
                /* packages     */ List.of()
        );
    }

    private RockyLinux8Builder rocky8() {
        return new RockyLinux8Builder(osEnvironmentRepository, osPackageGroupRepository);
    }

    private RockyLinux9Builder rocky9() {
        return new RockyLinux9Builder(osEnvironmentRepository, osPackageGroupRepository);
    }

    private RockyLinux10Builder rocky10() {
        return new RockyLinux10Builder(osEnvironmentRepository, osPackageGroupRepository);
    }

    private CentOS7Builder centos7() {
        return new CentOS7Builder(osEnvironmentRepository, osPackageGroupRepository);
    }

    private Ubuntu2204Builder ubuntu() {
        return new Ubuntu2204Builder();
    }

    // ─────────────────────────────────────────────
    // Rocky 8
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("RockyLinux8Builder.supports")
    class Rocky8Supports {

        @Test
        @DisplayName("RHEL 요청 + Rocky 8.10 메타데이터 → true")
        void rockyEightMatches() {
            assertThat(rocky8().supports(rhelReq(), metaOf(OSName.ROCKY_LINUX, "8.10"))).isTrue();
        }

        @Test
        @DisplayName("RHEL 요청 + Rocky 9.5 → false (메이저 버전 불일치)")
        void rockyEightRejectsNineMajor() {
            assertThat(rocky8().supports(rhelReq(), metaOf(OSName.ROCKY_LINUX, "9.5"))).isFalse();
        }

        @Test
        @DisplayName("RHEL 요청 + CentOS 7.9 → false (OS 이름 불일치)")
        void rockyEightRejectsCentOSName() {
            assertThat(rocky8().supports(rhelReq(), metaOf(OSName.CENTOS, "7.9"))).isFalse();
        }

        @Test
        @DisplayName("Ubuntu 요청 + Rocky 8.10 → false (요청 타입 불일치)")
        void rockyEightRejectsUbuntuRequest() {
            assertThat(rocky8().supports(ubuntuReq(), metaOf(OSName.ROCKY_LINUX, "8.10"))).isFalse();
        }
    }

    // ─────────────────────────────────────────────
    // Rocky 9
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("RockyLinux9Builder.supports")
    class Rocky9Supports {

        @Test
        @DisplayName("RHEL 요청 + Rocky 9.5 → true")
        void rockyNineMatches() {
            assertThat(rocky9().supports(rhelReq(), metaOf(OSName.ROCKY_LINUX, "9.5"))).isTrue();
        }

        @Test
        @DisplayName("RHEL 요청 + Rocky 8.10 → false")
        void rockyNineRejectsEight() {
            assertThat(rocky9().supports(rhelReq(), metaOf(OSName.ROCKY_LINUX, "8.10"))).isFalse();
        }

        @Test
        @DisplayName("RHEL 요청 + Rocky 10.0 → false")
        void rockyNineRejectsTen() {
            assertThat(rocky9().supports(rhelReq(), metaOf(OSName.ROCKY_LINUX, "10.0"))).isFalse();
        }
    }

    // ─────────────────────────────────────────────
    // Rocky 10
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("RockyLinux10Builder.supports")
    class Rocky10Supports {

        @Test
        @DisplayName("RHEL 요청 + Rocky 10.0 → true")
        void rockyTenMatches() {
            assertThat(rocky10().supports(rhelReq(), metaOf(OSName.ROCKY_LINUX, "10.0"))).isTrue();
        }

        @Test
        @DisplayName("RHEL 요청 + Rocky 9.5 → false (9.x 가 10.x 접두사에 겹치지 않음)")
        void rockyTenRejectsNine() {
            assertThat(rocky10().supports(rhelReq(), metaOf(OSName.ROCKY_LINUX, "9.5"))).isFalse();
        }

        @Test
        @DisplayName("RHEL 요청 + Ubuntu 22.04.5 → false (OS 이름 불일치)")
        void rockyTenRejectsUbuntuMeta() {
            assertThat(rocky10().supports(rhelReq(), metaOf(OSName.UBUNTU, "22.04.5"))).isFalse();
        }
    }

    // ─────────────────────────────────────────────
    // CentOS 7
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("CentOS7Builder.supports")
    class CentOS7Supports {

        @Test
        @DisplayName("RHEL 요청 + CentOS 7.9 → true")
        void centos7Matches() {
            assertThat(centos7().supports(rhelReq(), metaOf(OSName.CENTOS, "7.9"))).isTrue();
        }

        @Test
        @DisplayName("RHEL 요청 + Rocky 7.9 → false (CentOS 가 아닌 Rocky OSName)")
        void centos7RejectsRockyOSName() {
            assertThat(centos7().supports(rhelReq(), metaOf(OSName.ROCKY_LINUX, "7.9"))).isFalse();
        }

        @Test
        @DisplayName("Ubuntu 요청 + CentOS 7.9 → false (요청 타입 불일치)")
        void centos7RejectsUbuntuRequest() {
            assertThat(centos7().supports(ubuntuReq(), metaOf(OSName.CENTOS, "7.9"))).isFalse();
        }
    }

    // ─────────────────────────────────────────────
    // Ubuntu 22.04
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Ubuntu2204Builder.supports")
    class Ubuntu2204Supports {

        @Test
        @DisplayName("Ubuntu 요청 + Ubuntu 22.04.5 → true")
        void ubuntuMatches() {
            assertThat(ubuntu().supports(ubuntuReq(), metaOf(OSName.UBUNTU, "22.04.5"))).isTrue();
        }

        @Test
        @DisplayName("Ubuntu 요청 + Ubuntu 24.04 → false (다른 버전 접두사)")
        void ubuntuRejectsOtherMajor() {
            assertThat(ubuntu().supports(ubuntuReq(), metaOf(OSName.UBUNTU, "24.04"))).isFalse();
        }

        @Test
        @DisplayName("RHEL 요청 + Ubuntu 22.04.5 → false (요청 타입 불일치)")
        void ubuntuRejectsRhelRequest() {
            assertThat(ubuntu().supports(rhelReq(), metaOf(OSName.UBUNTU, "22.04.5"))).isFalse();
        }

        @Test
        @DisplayName("Ubuntu 요청 + Rocky 9.5 → false (OS 이름 불일치)")
        void ubuntuRejectsRockyMeta() {
            assertThat(ubuntu().supports(ubuntuReq(), metaOf(OSName.ROCKY_LINUX, "9.5"))).isFalse();
        }
    }

    // ─────────────────────────────────────────────
    // 전체 매트릭스 — "정확히 하나의 빌더만 매칭" 불변식
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("빌더 목록 디스패치 — 모든 유효 (요청, 메타) 조합에 대해 정확히 1 개만 매칭")
    class ExactlyOneBuilderMatches {

        private final List<OSInstallationBuilder> allBuilders = List.of(
                rocky8(), rocky9(), rocky10(), centos7(), ubuntu()
        );

        private long matchingCount(OSInstallationRequest req, OSMetadata meta) {
            return allBuilders.stream().filter(b -> b.supports(req, meta)).count();
        }

        @Test
        @DisplayName("Rocky 8.10 → Rocky8Builder 하나만")
        void rocky810() {
            assertThat(matchingCount(rhelReq(), metaOf(OSName.ROCKY_LINUX, "8.10"))).isEqualTo(1);
        }

        @Test
        @DisplayName("Rocky 9.5 → Rocky9Builder 하나만")
        void rocky95() {
            assertThat(matchingCount(rhelReq(), metaOf(OSName.ROCKY_LINUX, "9.5"))).isEqualTo(1);
        }

        @Test
        @DisplayName("Rocky 10.0 → Rocky10Builder 하나만")
        void rocky100() {
            assertThat(matchingCount(rhelReq(), metaOf(OSName.ROCKY_LINUX, "10.0"))).isEqualTo(1);
        }

        @Test
        @DisplayName("CentOS 7.9 → CentOS7Builder 하나만")
        void centos79() {
            assertThat(matchingCount(rhelReq(), metaOf(OSName.CENTOS, "7.9"))).isEqualTo(1);
        }

        @Test
        @DisplayName("Ubuntu 22.04.5 → Ubuntu2204Builder 하나만")
        void ubuntu22045() {
            assertThat(matchingCount(ubuntuReq(), metaOf(OSName.UBUNTU, "22.04.5"))).isEqualTo(1);
        }

        @Test
        @DisplayName("미지원 조합 (Windows) → 0 개 매칭")
        void noBuilderMatchesWindows() {
            assertThat(matchingCount(rhelReq(), metaOf(OSName.WINDOWS_SERVER, "2022"))).isZero();
        }

        @Test
        @DisplayName("미지원 조합 (Rocky 7) → 0 개 매칭")
        void noBuilderMatchesRocky7() {
            assertThat(matchingCount(rhelReq(), metaOf(OSName.ROCKY_LINUX, "7.9"))).isZero();
        }

        @Test
        @DisplayName("요청-메타 mismatch (RHEL 요청 + Ubuntu 메타) → 0 개 매칭")
        void noBuilderMatchesCrossFamily() {
            assertThat(matchingCount(rhelReq(), metaOf(OSName.UBUNTU, "22.04.5"))).isZero();
        }
    }
}
