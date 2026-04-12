package com.example.serverprovision.domain.os.model.setting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RockyLinuxSettingTest {

    private final RockyLinuxSetting setting = new RockyLinuxSetting();

    // ========== SELinux 모드별 스크립트 생성 ==========

    @Nested
    @DisplayName("SELinux 모드별 %post 스크립트")
    class SelinuxScriptTest {

        @Test
        @DisplayName("enforcing 모드: setenforce 1과 sed 명령을 포함한다")
        void enforcing_containsSetenforce1AndSed() {
            String script = setting.getPostInstallScript("enforcing", List.of(), List.of());

            assertThat(script).contains("setenforce 1");
            assertThat(script).contains("sed -i 's/^SELINUX=.*/SELINUX=enforcing/' /etc/selinux/config");
            assertThat(script).startsWith("%post\n");
            assertThat(script).endsWith("%end\n");
        }

        @Test
        @DisplayName("permissive 모드: setenforce 0과 sed 명령을 포함한다")
        void permissive_containsSetenforce0AndSed() {
            String script = setting.getPostInstallScript("permissive", List.of(), List.of());

            assertThat(script).contains("setenforce 0");
            assertThat(script).contains("sed -i 's/^SELINUX=.*/SELINUX=permissive/' /etc/selinux/config");
        }

        @Test
        @DisplayName("disabled 모드: setenforce 없이 sed 명령만 포함한다")
        void disabled_containsOnlySed() {
            String script = setting.getPostInstallScript("disabled", List.of(), List.of());

            assertThat(script).doesNotContain("setenforce");
            assertThat(script).contains("sed -i 's/^SELINUX=.*/SELINUX=disabled/' /etc/selinux/config");
        }

        @Test
        @DisplayName("null selinuxMode: SELinux 관련 명령을 생략한다")
        void nullSelinuxMode_skipsSELinuxSection() {
            String script = setting.getPostInstallScript(null, List.of(), List.of());

            assertThat(script).doesNotContain("setenforce");
            assertThat(script).doesNotContain("sed");
            assertThat(script).doesNotContain("SELINUX");
        }

        @Test
        @DisplayName("빈 문자열 selinuxMode: SELinux 관련 명령을 생략한다")
        void emptySelinuxMode_skipsSELinuxSection() {
            String script = setting.getPostInstallScript("", List.of(), List.of());

            assertThat(script).doesNotContain("setenforce");
            assertThat(script).doesNotContain("sed");
        }

        @Test
        @DisplayName("공백 문자열 selinuxMode: SELinux 관련 명령을 생략한다")
        void blankSelinuxMode_skipsSELinuxSection() {
            String script = setting.getPostInstallScript("   ", List.of(), List.of());

            assertThat(script).doesNotContain("setenforce");
            assertThat(script).doesNotContain("sed");
        }
    }

    // ========== 패키지 설치 섹션 ==========

    @Nested
    @DisplayName("추가 패키지 설치 섹션")
    class PackageInstallSectionTest {

        @Test
        @DisplayName("빈 패키지 목록: dnf install 섹션을 생략한다")
        void emptyPackages_skipsDnfInstallSection() {
            String script = setting.getPostInstallScript("enforcing", List.of(), List.of());
            assertThat(script).doesNotContain("dnf install");
        }

        @Test
        @DisplayName("패키지 1개: dnf install -y pkg 형식이다")
        void singlePackage_formatsDnfInstallCorrectly() {
            String script = setting.getPostInstallScript("enforcing", List.of(), List.of("vim"));
            assertThat(script).contains("dnf install -y vim");
        }

        @Test
        @DisplayName("패키지 여러 개: dnf install -y pkg1 pkg2 형식이다")
        void multiplePackages_formatsDnfInstallWithAllPackages() {
            String script = setting.getPostInstallScript(
                    "enforcing", List.of(), List.of("vim", "net-tools", "gcc-c++"));
            assertThat(script).contains("dnf install -y vim net-tools gcc-c++");
        }

        @Test
        @DisplayName("null 패키지 목록: dnf install 섹션을 생략한다")
        void nullPackages_skipsDnfInstallSection() {
            String script = setting.getPostInstallScript("enforcing", List.of(), null);
            assertThat(script).doesNotContain("dnf install");
        }
    }

    // ========== 서비스 활성화 섹션 ==========

    @Nested
    @DisplayName("서비스 활성화 섹션")
    class ServiceEnableSectionTest {

        @Test
        @DisplayName("빈 서비스 목록: systemctl enable 섹션을 생략한다")
        void emptyServices_skipsSystemctlSection() {
            String script = setting.getPostInstallScript("enforcing", List.of(), List.of());
            assertThat(script).doesNotContain("systemctl enable");
        }

        @Test
        @DisplayName("서비스 1개: systemctl enable svc 행이 생성된다")
        void singleService_createsSystemctlEnableLine() {
            String script = setting.getPostInstallScript("enforcing", List.of("httpd"), List.of());
            assertThat(script).contains("systemctl enable httpd");
        }

        @Test
        @DisplayName("서비스 여러 개: 각각 systemctl enable 행이 생성된다")
        void multipleServices_createsMultipleSystemctlEnableLines() {
            String script = setting.getPostInstallScript(
                    "enforcing", List.of("httpd", "sshd", "firewalld"), List.of());

            assertThat(script).contains("systemctl enable httpd\n");
            assertThat(script).contains("systemctl enable sshd\n");
            assertThat(script).contains("systemctl enable firewalld\n");
        }

        @Test
        @DisplayName("null 서비스 목록: systemctl enable 섹션을 생략한다")
        void nullServices_skipsSystemctlSection() {
            String script = setting.getPostInstallScript("enforcing", null, List.of());
            assertThat(script).doesNotContain("systemctl enable");
        }
    }

    // ========== 전체 조합 ==========

    @Nested
    @DisplayName("전체 조합 테스트")
    class FullCombinationTest {

        @Test
        @DisplayName("모든 파라미터가 비어있으면 %post와 %end만 포함한다")
        void allEmpty_containsOnlyPostAndEnd() {
            String script = setting.getPostInstallScript(null, List.of(), List.of());

            assertThat(script).isEqualTo("%post\n%end\n");
        }

        @Test
        @DisplayName("모든 파라미터가 설정되면 전체 스크립트가 올바른 순서로 생성된다")
        void allParamsSet_generatesFullScript() {
            String script = setting.getPostInstallScript(
                    "enforcing",
                    List.of("httpd", "sshd"),
                    List.of("vim", "net-tools")
            );

            assertThat(script).startsWith("%post\n");
            assertThat(script).endsWith("%end\n");

            // SELinux 섹션이 패키지 섹션보다 먼저 나온다
            int selinuxIdx = script.indexOf("setenforce");
            int dnfIdx = script.indexOf("dnf install");
            int systemctlIdx = script.indexOf("systemctl enable");

            assertThat(selinuxIdx).isLessThan(dnfIdx);
            assertThat(dnfIdx).isLessThan(systemctlIdx);
        }

        @Test
        @DisplayName("null 파라미터 조합에서도 예외 없이 안전하게 생성된다")
        void nullParams_safelyGenerated() {
            String script = setting.getPostInstallScript(null, null, null);

            assertThat(script).startsWith("%post\n");
            assertThat(script).endsWith("%end\n");
            assertThat(script).doesNotContain("setenforce");
            assertThat(script).doesNotContain("dnf install");
            assertThat(script).doesNotContain("systemctl enable");
        }
    }
}
