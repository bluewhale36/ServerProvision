package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.ServiceAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RHELBasedSetting 의 Kickstart {@code %post} 생성 로직 회귀 테스트.
 *
 * <p>대표 구체 클래스인 {@link RockyLinux9Setting} 을 통해 검증한다. 동일 로직을 공유하는
 * {@link RockyLinux8Setting}, {@link RockyLinux10Setting}, {@link CentOS7Setting} 은 RHELBasedSetting 의
 * 템플릿 구현만 재사용하므로 별도 회귀가 필요 없다.</p>
 */
class RHELBasedSettingTest {

    private RHELBasedSetting build(String selinuxMode, List<ServiceDirective> services, List<String> packages) {
        return RockyLinux9Setting.builder()
                .selinuxMode(selinuxMode)
                .services(services)
                .additionalPackages(packages)
                .build();
    }

    private ServiceDirective enable(String name) {
        return new ServiceDirective(name, ServiceAction.ENABLE);
    }

    private ServiceDirective disable(String name) {
        return new ServiceDirective(name, ServiceAction.DISABLE);
    }

    @Nested
    @DisplayName("SELinux 모드별 %post 스크립트")
    class SelinuxScriptTest {

        @Test
        @DisplayName("enforcing 모드: setenforce 1과 sed 명령을 포함한다")
        void enforcing_containsSetenforce1AndSed() {
            String script = build("enforcing", List.of(), List.of()).getPostInstallScript();

            assertThat(script).contains("setenforce 1");
            assertThat(script).contains("sed -i 's/^SELINUX=.*/SELINUX=enforcing/' /etc/selinux/config");
            assertThat(script).startsWith("%post\n");
            assertThat(script).endsWith("%end\n");
        }

        @Test
        @DisplayName("permissive 모드: setenforce 0과 sed 명령을 포함한다")
        void permissive_containsSetenforce0AndSed() {
            String script = build("permissive", List.of(), List.of()).getPostInstallScript();

            assertThat(script).contains("setenforce 0");
            assertThat(script).contains("sed -i 's/^SELINUX=.*/SELINUX=permissive/' /etc/selinux/config");
        }

        @Test
        @DisplayName("disabled 모드: setenforce 없이 sed 명령만 포함한다")
        void disabled_containsOnlySed() {
            String script = build("disabled", List.of(), List.of()).getPostInstallScript();

            assertThat(script).doesNotContain("setenforce");
            assertThat(script).contains("sed -i 's/^SELINUX=.*/SELINUX=disabled/' /etc/selinux/config");
        }

        @Test
        @DisplayName("null selinuxMode: SELinux 관련 명령을 생략한다")
        void nullSelinuxMode_skipsSELinuxSection() {
            String script = build(null, List.of(), List.of()).getPostInstallScript();

            assertThat(script).doesNotContain("setenforce");
            assertThat(script).doesNotContain("sed");
            assertThat(script).doesNotContain("SELINUX");
        }

        @Test
        @DisplayName("빈 문자열 selinuxMode: SELinux 관련 명령을 생략한다")
        void emptySelinuxMode_skipsSELinuxSection() {
            String script = build("", List.of(), List.of()).getPostInstallScript();

            assertThat(script).doesNotContain("setenforce");
            assertThat(script).doesNotContain("sed");
        }

        @Test
        @DisplayName("공백 문자열 selinuxMode: SELinux 관련 명령을 생략한다")
        void blankSelinuxMode_skipsSELinuxSection() {
            String script = build("   ", List.of(), List.of()).getPostInstallScript();

            assertThat(script).doesNotContain("setenforce");
            assertThat(script).doesNotContain("sed");
        }
    }

    @Nested
    @DisplayName("추가 패키지 설치 섹션")
    class PackageInstallSectionTest {

        @Test
        @DisplayName("빈 패키지 목록: dnf install 섹션을 생략한다")
        void emptyPackages_skipsDnfInstallSection() {
            String script = build("enforcing", List.of(), List.of()).getPostInstallScript();
            assertThat(script).doesNotContain("dnf install");
        }

        @Test
        @DisplayName("패키지 1개: dnf install -y pkg 형식이다")
        void singlePackage_formatsDnfInstallCorrectly() {
            String script = build("enforcing", List.of(), List.of("vim")).getPostInstallScript();
            assertThat(script).contains("dnf install -y vim");
        }

        @Test
        @DisplayName("패키지 여러 개: dnf install -y pkg1 pkg2 형식이다")
        void multiplePackages_formatsDnfInstallWithAllPackages() {
            String script = build("enforcing", List.of(),
                    List.of("vim", "net-tools", "gcc-c++")).getPostInstallScript();
            assertThat(script).contains("dnf install -y vim net-tools gcc-c++");
        }

        @Test
        @DisplayName("null 패키지 목록: dnf install 섹션을 생략한다")
        void nullPackages_skipsDnfInstallSection() {
            String script = build("enforcing", List.of(), null).getPostInstallScript();
            assertThat(script).doesNotContain("dnf install");
        }
    }

    @Nested
    @DisplayName("서비스 지시 섹션")
    class ServiceDirectiveSectionTest {

        @Test
        @DisplayName("빈 서비스 목록: systemctl 섹션을 생략한다")
        void emptyServices_skipsSystemctlSection() {
            String script = build("enforcing", List.of(), List.of()).getPostInstallScript();
            assertThat(script).doesNotContain("systemctl");
        }

        @Test
        @DisplayName("ENABLE 지시 1개: systemctl enable svc 행이 생성된다")
        void singleEnable_createsSystemctlEnableLine() {
            String script = build("enforcing", List.of(enable("httpd")), List.of()).getPostInstallScript();
            assertThat(script).contains("systemctl enable httpd");
        }

        @Test
        @DisplayName("DISABLE 지시 1개: systemctl disable svc 행이 생성된다")
        void singleDisable_createsSystemctlDisableLine() {
            String script = build("enforcing", List.of(disable("cups")), List.of()).getPostInstallScript();
            assertThat(script).contains("systemctl disable cups");
        }

        @Test
        @DisplayName("ENABLE/DISABLE 혼합: 각 동작별로 올바른 systemctl 명령이 생성된다")
        void mixedDirectives_createsMatchingSystemctlLines() {
            String script = build("enforcing",
                    List.of(enable("httpd"), disable("cups"), enable("sshd")),
                    List.of()).getPostInstallScript();

            assertThat(script).contains("systemctl enable httpd\n");
            assertThat(script).contains("systemctl disable cups\n");
            assertThat(script).contains("systemctl enable sshd\n");
        }

        @Test
        @DisplayName("null 서비스 목록: systemctl 섹션을 생략한다")
        void nullServices_skipsSystemctlSection() {
            String script = build("enforcing", null, List.of()).getPostInstallScript();
            assertThat(script).doesNotContain("systemctl");
        }

        @Test
        @DisplayName("ServiceDirective 생성자에서 action=null 은 ENABLE 로 기본 해석된다")
        void nullActionInDirective_defaultsToEnable() {
            ServiceDirective d = new ServiceDirective("httpd", null);
            String script = build("enforcing", List.of(d), List.of()).getPostInstallScript();
            assertThat(script).contains("systemctl enable httpd");
        }
    }

    @Nested
    @DisplayName("전체 조합 테스트")
    class FullCombinationTest {

        @Test
        @DisplayName("모든 파라미터가 비어있으면 %post와 %end만 포함한다")
        void allEmpty_containsOnlyPostAndEnd() {
            String script = build(null, List.of(), List.of()).getPostInstallScript();

            assertThat(script).isEqualTo("%post\n%end\n");
        }

        @Test
        @DisplayName("모든 파라미터가 설정되면 전체 스크립트가 올바른 순서로 생성된다")
        void allParamsSet_generatesFullScript() {
            String script = build("enforcing",
                    List.of(enable("httpd"), disable("cups")),
                    List.of("vim", "net-tools")).getPostInstallScript();

            assertThat(script).startsWith("%post\n");
            assertThat(script).endsWith("%end\n");

            int selinuxIdx = script.indexOf("setenforce");
            int dnfIdx = script.indexOf("dnf install");
            int systemctlIdx = script.indexOf("systemctl");

            assertThat(selinuxIdx).isLessThan(dnfIdx);
            assertThat(dnfIdx).isLessThan(systemctlIdx);
        }

        @Test
        @DisplayName("null 파라미터 조합에서도 예외 없이 안전하게 생성된다")
        void nullParams_safelyGenerated() {
            String script = build(null, null, null).getPostInstallScript();

            assertThat(script).startsWith("%post\n");
            assertThat(script).endsWith("%end\n");
            assertThat(script).doesNotContain("setenforce");
            assertThat(script).doesNotContain("dnf install");
            assertThat(script).doesNotContain("systemctl");
        }
    }

    @Nested
    @DisplayName("호환 OS/버전 판별")
    class CompatibilityTest {

        @Test
        @DisplayName("RockyLinux9Setting 은 Rocky Linux 9.x 와 호환된다")
        void rocky9Setting_isCompatibleWithRocky9() {
            RHELBasedSetting s = build("enforcing", List.of(), List.of());
            assertThat(s.isCompatible(com.example.serverprovision.domain.os.model.enums.OSName.ROCKY_LINUX, "9.6")).isTrue();
            assertThat(s.isCompatible(com.example.serverprovision.domain.os.model.enums.OSName.ROCKY_LINUX, "10.0")).isFalse();
            assertThat(s.isCompatible(com.example.serverprovision.domain.os.model.enums.OSName.CENTOS, "9.6")).isFalse();
        }
    }
}
